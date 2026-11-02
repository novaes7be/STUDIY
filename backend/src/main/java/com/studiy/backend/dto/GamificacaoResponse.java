package com.studiy.backend.dto;

public record GamificacaoResponse(
        Integer xp,
        Integer nivel,
        Integer streakAtual,
        Integer xpParaProximoNivel
) {}
