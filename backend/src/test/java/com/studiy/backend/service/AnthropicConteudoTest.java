package com.studiy.backend.service;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Formato dos blocos da Messages API: figuras (texto + image base64) antes do material. */
class AnthropicConteudoTest {

    @Test
    void figurasVemAntesDoMaterialNoFormatoDeImagemDaApi() {
        var blocos = AnthropicService.conteudo("", "texto ![Figura 1](imagem:1)",
                List.of(new AnthropicService.Figura(1, "image/png", "QUJD")));

        assertThat(blocos).hasSize(3);
        assertThat(blocos.get(0)).containsEntry("type", "text");
        assertThat(blocos.get(1)).containsEntry("type", "image")
                .containsEntry("source", Map.of("type", "base64", "media_type", "image/png", "data", "QUJD"));
        assertThat((String) blocos.get(2).get("text")).contains("<figuras>", "<material>\ntexto ![Figura 1](imagem:1)\n</material>");
    }

    @Test
    void semFigurasESoOTextoEComLimitePorChamada() {
        assertThat(AnthropicService.conteudo("", "t", List.of())).singleElement()
                .satisfies(b -> assertThat((String) b.get("text")).doesNotContain("<figuras>"));

        var muitas = Collections.nCopies(30, new AnthropicService.Figura(1, "image/png", "QUJD"));
        long imagens = AnthropicService.conteudo("", "t", muitas).stream().filter(b -> "image".equals(b.get("type"))).count();
        assertThat(imagens).isEqualTo(AnthropicService.FIGURAS_POR_CHAMADA);
    }
}
