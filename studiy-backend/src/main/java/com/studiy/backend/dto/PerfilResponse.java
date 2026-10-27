package com.studiy.backend.dto;

import com.studiy.backend.model.Plano;

public record PerfilResponse(
        String nome,
        String email,
        Plano plano,
        int pdfsUsados,
        int pdfsLimite,
        Integer xp,
        Integer nivel,
        Integer streakAtual,
        Integer xpParaProximoNivel,
        String avatar
) {}