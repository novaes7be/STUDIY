package com.studiy.backend.controller;

import com.studiy.backend.dto.AuthResponse;
import com.studiy.backend.dto.ExcluirContaRequest;
import com.studiy.backend.dto.PerfilDtos;
import com.studiy.backend.dto.PerfilResponse;
import com.studiy.backend.model.Usuario;
import com.studiy.backend.security.SessaoService;
import com.studiy.backend.security.UsuarioAtual;
import jakarta.servlet.http.HttpServletResponse;
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
    private final SessaoService sessaoService;

    public UsuarioController(UsuarioService usuarioService, UsuarioAtual usuarioAtual, SessaoService sessaoService) {
        this.usuarioService = usuarioService;
        this.usuarioAtual = usuarioAtual;
        this.sessaoService = sessaoService;
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
    public AuthResponse alterarEmail(Authentication auth, @Valid @RequestBody PerfilDtos.EmailRequest request,
                                     HttpServletResponse res) {
        Usuario u = usuarioService.alterarEmail(usuarioAtual.resolver(auth), request.novoEmail(), request.senha());
        sessaoService.emitir(u, res); // as outras sessões caíram; esta continua com cookies novos
        return AuthResponse.de(u);
    }

    @PutMapping("/senha")
    public AuthResponse alterarSenha(Authentication auth, @Valid @RequestBody PerfilDtos.SenhaRequest request,
                                     HttpServletResponse res) {
        Usuario u = usuarioService.alterarSenha(usuarioAtual.resolver(auth), request.senhaAtual(), request.novaSenha());
        sessaoService.emitir(u, res);
        return AuthResponse.de(u);
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
    public void excluir(Authentication auth, @Valid @RequestBody ExcluirContaRequest request, HttpServletResponse res) {
        usuarioService.excluirConta(usuarioAtual.resolver(auth), request.senha());
        sessaoService.limparCookies(res);
    }
}
