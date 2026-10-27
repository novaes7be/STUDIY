package com.studiy.backend.controller;

import com.studiy.backend.dto.AuthResponse;
import com.studiy.backend.dto.ExcluirContaRequest;
import com.studiy.backend.dto.PerfilDtos;
import com.studiy.backend.dto.PerfilResponse;
import com.studiy.backend.security.UsuarioAtual;
import com.studiy.backend.service.UsuarioService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/usuarios/me")
public class UsuarioController {

    private final UsuarioService usuarioService;
    private final UsuarioAtual usuarioAtual;

    public UsuarioController(UsuarioService usuarioService, UsuarioAtual usuarioAtual) {
        this.usuarioService = usuarioService;
        this.usuarioAtual = usuarioAtual;
    }

    @GetMapping
    public PerfilResponse perfil(Authentication auth) {
        return usuarioService.perfil(usuarioAtual.resolver(auth));
    }

    @PutMapping
    public PerfilResponse atualizarNome(Authentication auth, @Valid @RequestBody PerfilDtos.NomeRequest request) {
        return usuarioService.atualizarNome(usuarioAtual.resolver(auth), request.nome());
    }

    @PutMapping("/email")
    public AuthResponse alterarEmail(Authentication auth, @Valid @RequestBody PerfilDtos.EmailRequest request) {
        return usuarioService.alterarEmail(usuarioAtual.resolver(auth), request.novoEmail(), request.senha());
    }

    @PutMapping("/senha")
    public AuthResponse alterarSenha(Authentication auth, @Valid @RequestBody PerfilDtos.SenhaRequest request) {
        return usuarioService.alterarSenha(usuarioAtual.resolver(auth), request.senhaAtual(), request.novaSenha());
    }

    @PutMapping("/avatar")
    public PerfilResponse avatar(Authentication auth, @Valid @RequestBody PerfilDtos.AvatarRequest request) {
        return usuarioService.definirAvatar(usuarioAtual.resolver(auth), request.avatar());
    }

    @GetMapping("/exportar")
    public Map<String, Object> exportar(Authentication auth) {
        return usuarioService.exportar(usuarioAtual.resolver(auth));
    }

    @DeleteMapping
    public void excluir(Authentication auth, @Valid @RequestBody ExcluirContaRequest request) {
        usuarioService.excluirConta(usuarioAtual.resolver(auth), request.senha());
    }
}