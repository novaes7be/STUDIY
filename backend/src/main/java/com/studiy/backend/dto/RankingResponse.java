package com.studiy.backend.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Ranking pronto para exibir. Textos e regras vêm do servidor; nenhuma linha traz id,
 * e-mail ou nome real (só o apelido público que a própria pessoa escolheu).
 */
public record RankingResponse(
        String tipo,
        String titulo,
        String unidade,
        LocalDateTime geradoEm,
        boolean participando,
        List<Item> itens,
        Item voce
) {
    public record Item(int posicao, String apelido, int valor, boolean voce) {}
}
