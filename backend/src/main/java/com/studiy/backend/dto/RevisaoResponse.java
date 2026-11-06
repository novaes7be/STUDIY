package com.studiy.backend.dto;

import com.studiy.backend.model.RespostaRevisao;

import java.util.Arrays;
import java.util.List;

/** Fila de revisão do dia + os botões de resposta (rótulo e valor vêm do servidor). */
public record RevisaoResponse(List<FlashcardResponse> cartoes, List<Opcao> opcoes) {

    public record Opcao(RespostaRevisao valor, String rotulo) {}

    public static RevisaoResponse de(List<FlashcardResponse> cartoes) {
        return new RevisaoResponse(cartoes,
                Arrays.stream(RespostaRevisao.values()).map(r -> new Opcao(r, r.rotulo())).toList());
    }
}
