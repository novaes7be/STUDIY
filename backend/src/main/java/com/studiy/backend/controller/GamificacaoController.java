package com.studiy.backend.controller;

import com.studiy.backend.dto.GamificacaoResponse;
import com.studiy.backend.security.UsuarioAtual;
import com.studiy.backend.service.GamificacaoService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/gamificacao")
public class GamificacaoController {

    private final GamificacaoService gamificacaoService;
    private final UsuarioAtual usuarioAtual;

    public GamificacaoController(GamificacaoService gamificacaoService, UsuarioAtual usuarioAtual) {
        this.gamificacaoService = gamificacaoService;
        this.usuarioAtual = usuarioAtual;
    }

    @GetMapping("/me")
    public GamificacaoResponse meuStatus(Authentication auth) {
        return gamificacaoService.status(usuarioAtual.resolver(auth));
    }
}
