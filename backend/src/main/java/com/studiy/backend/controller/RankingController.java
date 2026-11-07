package com.studiy.backend.controller;

import com.studiy.backend.dto.RankingResponse;
import com.studiy.backend.model.TipoRanking;
import com.studiy.backend.security.UsuarioAtual;
import com.studiy.backend.service.RankingService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Ranking global (só leitura da foto pré-calculada). Exige login, como todo /api. */
@RestController
@RequestMapping("/api/ranking")
public class RankingController {

    private final RankingService rankingService;
    private final UsuarioAtual usuarioAtual;

    public RankingController(RankingService rankingService, UsuarioAtual usuarioAtual) {
        this.rankingService = rankingService;
        this.usuarioAtual = usuarioAtual;
    }

    /** tipo inválido (fora do enum) vira 400 no GlobalExceptionHandler. */
    @GetMapping
    public RankingResponse ranking(Authentication auth, @RequestParam(defaultValue = "SEMANA") TipoRanking tipo) {
        return rankingService.ler(usuarioAtual.resolver(auth), tipo);
    }
}
