package com.studiy.backend.dto;

import com.studiy.backend.model.Plano;

import java.time.LocalDateTime;

/**
 * Perfil pronto para exibição. Regras (plano efetivo, limites, percentuais, textos que descrevem
 * regras de cobrança) são decididas aqui; o front só mostra.
 */
public record PerfilResponse(
        String nome,
        String email,
        Plano plano,
        String planoRotulo,
        String acaoPlano,
        LocalDateTime planoExpiraEm,
        int pdfsUsados,
        int pdfsLimite,
        int usoPercentual,
        String regraUso,
        GamificacaoResponse gamificacao,
        String avatar,
        boolean emailVerificado,
        boolean onboardingPendente,
        boolean admin,
        boolean mfaAtivo,
        String apelido
) {}
