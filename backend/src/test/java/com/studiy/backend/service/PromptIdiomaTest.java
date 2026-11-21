package com.studiy.backend.service;

import com.studiy.backend.service.AnthropicService.Tarefa;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** O conteúdo gerado sai no idioma da página; o formato dos flashcards (lido pelo parser) não muda. */
class PromptIdiomaTest {

    @Test
    void resumoNoIdiomaPedido() {
        assertThat(AnthropicService.promptSistemaPara(Tarefa.RESUMO, 0, "pt-BR")).contains("em português.");
        assertThat(AnthropicService.promptSistemaPara(Tarefa.RESUMO, 0, "en")).contains("em inglês.").doesNotContain("português");
        assertThat(AnthropicService.promptSistemaPara(Tarefa.RESUMO_PARTE, 0, "es")).contains("em espanhol,");
        assertThat(AnthropicService.promptSistemaPara(Tarefa.RESUMO_FINAL, 0, "es")).contains("em espanhol,");
    }

    @Test
    void flashcardsMantemOsRotulosDoParser() {
        String pt = AnthropicService.promptSistemaPara(Tarefa.FLASHCARD, 10, "pt-BR");
        String en = AnthropicService.promptSistemaPara(Tarefa.FLASHCARD, 10, "en");
        assertThat(pt).contains("'Pergunta: ... | Resposta: ...'").doesNotContain("Mantenha os rótulos");
        assertThat(en).contains("em inglês,").contains("Mantenha os rótulos 'Pergunta:' e 'Resposta:' exatamente assim");
    }
}
