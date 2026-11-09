package com.studiy.backend.service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Divide o Markdown de um PDF longo em partes que cabem numa chamada de IA, SEM perder texto:
 * juntando o texto das partes, volta-se ao original. Corta preferindo, nesta ordem,
 * antes de um título, entre parágrafos, entre frases e, só em último caso, entre palavras.
 * Cada parte leva o caminho de títulos em que começa ("Capítulo 2 > Mitose") como contexto.
 */
public final class DivisorDeTexto {

    private static final Pattern TITULO = Pattern.compile("^(#{1,6})\\s+(.*)$");
    private static final Pattern FIM_DE_FRASE = Pattern.compile("(?<=[.!?…])\\s+");

    private DivisorDeTexto() {}

    /** contexto: títulos que valem no começo da parte (vazio na primeira). texto: o trecho em si. */
    public record Parte(String contexto, String texto) {}

    public static List<Parte> dividir(String markdown, int maximo) {
        if (maximo < 100) throw new IllegalArgumentException("máximo pequeno demais");
        String md = markdown == null ? "" : markdown.strip();
        if (md.length() <= maximo) return List.of(new Parte("", md));

        List<Parte> partes = new ArrayList<>();
        String[] caminho = new String[7];          // títulos ativos por nível (1..6)
        String contextoDaParte = "";
        StringBuilder atual = new StringBuilder();

        for (String bloco : md.split("\n{2,}")) {
            Matcher t = TITULO.matcher(bloco);
            boolean ehTitulo = t.matches();
            int extra = (atual.length() == 0 ? 0 : 2) + bloco.length();

            // Corta antes de um título quando a parte já tem conteúdo razoável; ou quando não cabe.
            boolean cortaNoTitulo = ehTitulo && atual.length() >= maximo / 2;
            if (atual.length() > 0 && (atual.length() + extra > maximo || cortaNoTitulo)) {
                partes.add(new Parte(contextoDaParte, atual.toString()));
                atual.setLength(0);
            }
            if (atual.length() == 0) contextoDaParte = juntarCaminho(caminho);

            if (ehTitulo) {
                int nivel = t.group(1).length();
                caminho[nivel] = t.group(2).strip();
                for (int n = nivel + 1; n < caminho.length; n++) caminho[n] = null;
            }

            if (bloco.length() > maximo) {
                // Parágrafo gigante: quebra por frases (ou palavras) em pedaços que cabem.
                for (String pedaco : quebrarBloco(bloco, maximo)) {
                    if (atual.length() > 0) {
                        partes.add(new Parte(contextoDaParte, atual.toString()));
                        atual.setLength(0);
                        contextoDaParte = juntarCaminho(caminho);
                    }
                    atual.append(pedaco);
                }
                continue;
            }
            if (atual.length() > 0) atual.append("\n\n");
            atual.append(bloco);
        }
        if (atual.length() > 0) partes.add(new Parte(contextoDaParte, atual.toString()));
        return partes;
    }

    /**
     * Pedaços de até 'maximo' caracteres. Cada pedaço termina onde termina uma frase (ou palavra),
     * e o separador fica no fim do pedaço anterior: concatenar os pedaços devolve o bloco exato.
     */
    static List<String> quebrarBloco(String bloco, int maximo) {
        List<String> unidades = new ArrayList<>();
        Matcher m = FIM_DE_FRASE.matcher(bloco);
        int inicio = 0;
        while (m.find()) {
            unidades.add(bloco.substring(inicio, m.end()));
            inicio = m.end();
        }
        unidades.add(bloco.substring(inicio));

        List<String> pedacos = new ArrayList<>();
        StringBuilder atual = new StringBuilder();
        for (String u : unidades) {
            for (String peca : u.length() > maximo ? porPalavras(u, maximo) : List.of(u)) {
                if (atual.length() + peca.length() > maximo && atual.length() > 0) {
                    pedacos.add(atual.toString());
                    atual.setLength(0);
                }
                atual.append(peca);
            }
        }
        if (atual.length() > 0) pedacos.add(atual.toString());
        return pedacos;
    }

    /** Frase maior que o máximo: corta no último espaço que cabe (ou no meio, se não houver espaço). */
    private static List<String> porPalavras(String s, int maximo) {
        List<String> out = new ArrayList<>();
        int i = 0;
        while (i < s.length()) {
            int fim = Math.min(i + maximo, s.length());
            if (fim < s.length()) {
                int espaco = s.lastIndexOf(' ', fim - 1);
                if (espaco > i) fim = espaco + 1;
            }
            out.add(s.substring(i, fim));
            i = fim;
        }
        return out;
    }

    private static String juntarCaminho(String[] caminho) {
        StringBuilder sb = new StringBuilder();
        for (int n = 1; n < caminho.length; n++) {
            if (caminho[n] == null) continue;
            if (sb.length() > 0) sb.append(" > ");
            sb.append(caminho[n]);
        }
        return sb.toString();
    }
}
