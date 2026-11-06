package com.studiy.backend.dto;

/** Tudo calculado no servidor: o front só exibe (inclusive a largura da barra de nível). */
public record GamificacaoResponse(
        Integer xp,
        Integer nivel,
        Integer streakAtual,
        Integer xpNoNivel,
        Integer xpParaProximoNivel,
        Integer percentualNivel
) {}
