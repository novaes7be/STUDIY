package com.studiy.backend.service;

import com.studiy.backend.model.TipoGeracao;
import com.studiy.backend.service.AnthropicService.Tarefa;
import com.studiy.backend.service.DivisorDeTexto.Parte;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;

/**
 * Gera resumo/flashcards/simulado a partir do Markdown INTEIRO do PDF, sem cortar texto.
 * PDF curto: uma chamada. PDF longo: divide em partes (DivisorDeTexto) e chama a IA por parte,
 * no máximo 3 ao mesmo tempo:
 * - flashcards e simulado: cada parte gera os seus, e o resultado é a soma (sem perguntas repetidas);
 * - resumo: o Haiku faz anotações detalhadas de cada parte e o modelo do resumo junta tudo.
 */
@Service
public class GeradorConteudo {

    /** ~15 mil tokens por parte: cabe com folga e mantém o Haiku na faixa de preço até 100K. */
    static final int TAMANHO_PARTE = 60_000;
    private static final int CHAMADAS_SIMULTANEAS = 3;

    private final AnthropicService ia;
    private final ExecutorService executor = Executors.newFixedThreadPool(CHAMADAS_SIMULTANEAS, r -> {
        Thread t = new Thread(r, "ia-partes");
        t.setDaemon(true);
        return t;
    });

    public GeradorConteudo(AnthropicService ia) {
        this.ia = ia;
    }

    @PreDestroy
    void encerrar() {
        executor.shutdownNow();
    }

    /** Texto final + todas as chamadas feitas (para registrar tokens e custo de cada uma). */
    public record Resultado(String texto, List<RespostaIa> chamadas) {}

    /** Falhou no meio: carrega as chamadas que já tinham sido cobradas, para registrar o custo. */
    public static class FalhaNaGeracao extends RuntimeException {
        private final transient List<RespostaIa> cobradas;

        FalhaNaGeracao(RuntimeException causa, List<RespostaIa> cobradas) {
            super(causa);
            this.cobradas = cobradas;
        }

        public RuntimeException causa() {
            return (RuntimeException) getCause();
        }

        public List<RespostaIa> cobradas() {
            return cobradas;
        }
    }

    public Resultado gerar(TipoGeracao tipo, String markdown) {
        List<Parte> partes = DivisorDeTexto.dividir(markdown, TAMANHO_PARTE);
        List<RespostaIa> chamadas = Collections.synchronizedList(new ArrayList<>());
        try {
            String texto = switch (tipo) {
                case FLASHCARD -> juntarFlashcards(porParte(partes, chamadas,
                        p -> ia.chamar(Tarefa.FLASHCARD, p.texto(), p.contexto(), cartoesPara(p.texto()))));
                case SIMULADO -> juntarSimulados(porParte(partes, chamadas,
                        p -> ia.chamar(Tarefa.SIMULADO, p.texto(), p.contexto(), partes.size() == 1 ? 5 : 3)));
                case RESUMO -> resumir(partes, chamadas);
            };
            return new Resultado(texto, List.copyOf(chamadas));
        } catch (FalhaNaGeracao e) {
            throw e; // já traz as chamadas cobradas
        } catch (AnthropicService.IaRecusouException e) {
            chamadas.add(e.resposta());
            throw new FalhaNaGeracao(e, List.copyOf(chamadas));
        } catch (RuntimeException e) {
            throw new FalhaNaGeracao(e, List.copyOf(chamadas));
        }
    }

    private String resumir(List<Parte> partes, List<RespostaIa> chamadas) {
        if (partes.size() == 1) {
            RespostaIa r = ia.chamar(Tarefa.RESUMO, partes.get(0).texto(), "", 0);
            chamadas.add(r);
            return r.texto();
        }
        List<String> anotacoes = porParte(partes, chamadas,
                p -> ia.chamar(Tarefa.RESUMO_PARTE, p.texto(), p.contexto(), 0));
        StringBuilder juntas = new StringBuilder();
        for (int i = 0; i < anotacoes.size(); i++) {
            juntas.append("## Parte ").append(i + 1).append(" de ").append(anotacoes.size());
            String ctx = partes.get(i).contexto();
            if (!ctx.isBlank()) juntas.append(" (").append(ctx).append(')');
            juntas.append("\n\n").append(anotacoes.get(i).strip()).append("\n\n");
        }
        RespostaIa fim = ia.chamar(Tarefa.RESUMO_FINAL, juntas.toString().strip(), "", 0);
        chamadas.add(fim);
        return fim.texto();
    }

    /** Uma chamada por parte, em paralelo, devolvendo os textos NA ORDEM das partes. */
    private List<String> porParte(List<Parte> partes, List<RespostaIa> chamadas, Function<Parte, RespostaIa> chamada) {
        if (partes.size() == 1) {
            RespostaIa r = chamada.apply(partes.get(0));
            chamadas.add(r);
            return List.of(r.texto());
        }
        List<Future<RespostaIa>> futuros = new ArrayList<>();
        for (Parte p : partes) futuros.add(executor.submit(() -> chamada.apply(p)));
        List<String> textos = new ArrayList<>();
        RuntimeException erro = null;
        for (Future<RespostaIa> f : futuros) {
            try {
                RespostaIa r = f.get();
                chamadas.add(r);
                textos.add(r.texto());
            } catch (ExecutionException e) {
                // continua esperando as outras: as que terminarem também foram cobradas
                if (e.getCause() instanceof AnthropicService.IaRecusouException rec) chamadas.add(rec.resposta());
                if (erro == null) erro = e.getCause() instanceof RuntimeException re ? re : new IllegalStateException(e.getCause());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                futuros.forEach(x -> x.cancel(true));
                throw new IllegalStateException("Geração interrompida", e);
            }
        }
        if (erro != null) {
            // a recusa já foi somada acima; não somar de novo no catch de gerar()
            throw erro instanceof AnthropicService.IaRecusouException rec
                    ? new FalhaNaGeracao(rec, List.copyOf(chamadas)) : erro;
        }
        return textos;
    }

    /** Mais texto, mais cartões: de 8 (parte curta) a 20 (parte cheia). */
    static int cartoesPara(String texto) {
        return Math.max(8, Math.min(20, (int) Math.ceil(texto.length() / 3000.0)));
    }

    /** Soma os cartões das partes, tirando perguntas repetidas (mesmo texto, ignorando caixa, acento e pontuação). */
    static String juntarFlashcards(List<String> respostas) {
        Set<String> vistas = new HashSet<>();
        StringBuilder sb = new StringBuilder();
        for (String r : respostas) {
            for (String linha : r.split("\n")) {
                String l = linha.strip();
                if (l.isEmpty()) continue;
                int sep = l.indexOf('|');
                String pergunta = sep > 0 ? l.substring(0, sep) : l;
                String chave = java.text.Normalizer.normalize(pergunta, java.text.Normalizer.Form.NFD)
                        .replaceAll("\\p{M}+", "")             // "é" e "e" contam igual
                        .toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", "");
                if (vistas.add(chave)) sb.append(l).append('\n');
            }
        }
        return sb.toString().strip();
    }

    private static String juntarSimulados(List<String> respostas) {
        if (respostas.size() == 1) return respostas.get(0);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < respostas.size(); i++) {
            sb.append("### Parte ").append(i + 1).append(" de ").append(respostas.size()).append("\n\n")
              .append(respostas.get(i).strip()).append("\n\n");
        }
        return sb.toString().strip();
    }
}
