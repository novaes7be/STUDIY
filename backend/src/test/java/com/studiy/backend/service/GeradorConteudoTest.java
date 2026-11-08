package com.studiy.backend.service;

import com.studiy.backend.model.TipoGeracao;
import com.studiy.backend.service.AnthropicService.Tarefa;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GeradorConteudoTest {

    private static RespostaIa resp(String texto) {
        return new RespostaIa(texto, "claude-haiku-5-5", 10, 5, 0, 0, "end_turn");
    }

    /** ~3 partes de 60 mil caracteres, com capítulos. */
    private static String pdfLongo() {
        StringBuilder md = new StringBuilder();
        for (int c = 1; c <= 3; c++) {
            md.append("# Capitulo ").append(c).append("\n\n");
            for (int p = 0; p < 110; p++) md.append("Paragrafo ").append(c).append('-').append(p).append(' ').append("conteudo ".repeat(55)).append("\n\n");
        }
        return md.toString().strip();
    }

    @Test
    void resumoDePdfLongoLeTodasAsPartesEJuntaNoFinal() {
        AnthropicService ia = mock(AnthropicService.class);
        List<String> enviados = new CopyOnWriteArrayList<>();
        when(ia.chamar(eq(Tarefa.RESUMO_PARTE), anyString(), anyString(), anyInt())).thenAnswer(inv -> {
            enviados.add(inv.getArgument(1));
            return resp("- anotacao");
        });
        when(ia.chamar(eq(Tarefa.RESUMO_FINAL), anyString(), anyString(), anyInt())).thenReturn(resp("# Resumo final"));

        String md = pdfLongo();
        GeradorConteudo.Resultado r = new GeradorConteudo(ia).gerar(TipoGeracao.RESUMO, md);

        assertThat(r.texto()).isEqualTo("# Resumo final");
        assertThat(enviados).hasSizeGreaterThanOrEqualTo(3);
        // todo parágrafo do PDF foi enviado em alguma parte (nada cortado)
        for (int c = 1; c <= 3; c++) for (int p = 0; p < 110; p++) {
            String marca = "Paragrafo " + c + "-" + p + " ";
            assertThat(enviados).anySatisfy(t -> assertThat(t).contains(marca));
        }
        assertThat(r.chamadas()).hasSize(enviados.size() + 1); // partes + junção final
        verify(ia, never()).chamar(eq(Tarefa.RESUMO), anyString(), anyString(), anyInt());
    }

    @Test
    void pdfCurtoFazUmaChamadaSoNoModeloDoResumo() {
        AnthropicService ia = mock(AnthropicService.class);
        when(ia.chamar(eq(Tarefa.RESUMO), anyString(), anyString(), anyInt())).thenReturn(resp("ok"));

        GeradorConteudo.Resultado r = new GeradorConteudo(ia).gerar(TipoGeracao.RESUMO, "# A\n\nTexto curto.");

        assertThat(r.chamadas()).hasSize(1);
        verify(ia).chamar(Tarefa.RESUMO, "# A\n\nTexto curto.", "", 0);
    }

    @Test
    void flashcardsDasPartesSaoSomadosSemPerguntaRepetida() {
        assertThat(GeradorConteudo.juntarFlashcards(List.of(
                "Pergunta: O que e mitose? | Resposta: Divisao celular\nPergunta: O que e DNA? | Resposta: Acido",
                "Pergunta: o que é mitose | Resposta: Outra\nPergunta: O que e RNA? | Resposta: Acido ribo")))
                .isEqualTo("Pergunta: O que e mitose? | Resposta: Divisao celular\n"
                        + "Pergunta: O que e DNA? | Resposta: Acido\n"
                        + "Pergunta: O que e RNA? | Resposta: Acido ribo");
    }

    @Test
    void falhaNumaParteAindaDevolveAsChamadasCobradas() {
        AnthropicService ia = mock(AnthropicService.class);
        when(ia.chamar(eq(Tarefa.FLASHCARD), anyString(), anyString(), anyInt())).thenAnswer(inv -> {
            if (((String) inv.getArgument(1)).contains("Capitulo 2")) throw new IllegalStateException("caiu");
            return resp("Pergunta: x? | Resposta: y");
        });

        assertThatThrownBy(() -> new GeradorConteudo(ia).gerar(TipoGeracao.FLASHCARD, pdfLongo()))
                .isInstanceOfSatisfying(GeradorConteudo.FalhaNaGeracao.class, e -> {
                    assertThat(e.causa()).hasMessage("caiu");
                    assertThat(e.cobradas()).isNotEmpty(); // as outras partes foram pagas
                });
    }
}
