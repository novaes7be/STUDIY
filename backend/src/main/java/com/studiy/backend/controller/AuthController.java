package com.studiy.backend.controller;

import com.studiy.backend.dto.AuthResponse;
import com.studiy.backend.dto.ForgotPasswordRequest;
import com.studiy.backend.dto.LoginRequest;
import com.studiy.backend.dto.RegisterRequest;
import com.studiy.backend.dto.ResetPasswordRequest;
import com.studiy.backend.dto.VerificarEmailRequest;
import com.studiy.backend.model.Usuario;
import com.studiy.backend.security.SessaoService;
import com.studiy.backend.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final com.studiy.backend.security.UsuarioAtual usuarioAtual;
    private final SessaoService sessaoService;

    public AuthController(AuthService authService, com.studiy.backend.security.UsuarioAtual usuarioAtual,
                          SessaoService sessaoService) {
        this.authService = authService;
        this.usuarioAtual = usuarioAtual;
        this.sessaoService = sessaoService;
    }

    /** Sempre 202 com a mesma mensagem: não revela se o e-mail já tem conta. */
    @PostMapping("/registrar")
    @ResponseStatus(org.springframework.http.HttpStatus.ACCEPTED)
    public MensagemResponse registrar(@Valid @RequestBody RegisterRequest request) {
        return new MensagemResponse(authService.registrar(request));
    }

    /** Sessão vai em cookies HttpOnly; o corpo traz só dados exibíveis. */
    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletResponse res) {
        Usuario usuario = authService.login(request);
        sessaoService.emitir(usuario, res);
        return AuthResponse.de(usuario);
    }

    /** Troca o refresh token (cookie studiy_rt) por um novo par de cookies. */
    @PostMapping("/refresh")
    public AuthResponse refresh(HttpServletRequest req, HttpServletResponse res) {
        return AuthResponse.de(sessaoService.renovar(req, res));
    }

    /** Só garante que o cookie XSRF-TOKEN exista (o front chama antes do primeiro POST). */
    @GetMapping("/csrf")
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void csrf() {
    }

    @PostMapping("/esqueci-senha")
    public MensagemResponse esqueciSenha(@Valid @RequestBody ForgotPasswordRequest request) {
        return new MensagemResponse(authService.esqueciSenha(request));
    }

    @PostMapping("/redefinir-senha")
    public void redefinirSenha(@Valid @RequestBody ResetPasswordRequest request) {
        authService.redefinirSenha(request);
    }

    @PostMapping("/verificar-email")
    public void verificarEmail(@Valid @RequestBody VerificarEmailRequest request) {
        authService.verificarEmail(request.token());
    }

    @PostMapping("/reenviar-verificacao")
    public MensagemResponse reenviarVerificacao(@Valid @RequestBody ForgotPasswordRequest request) {
        return new MensagemResponse(authService.reenviarVerificacao(request));
    }

    @PostMapping("/logout")
    public void logout(org.springframework.security.core.Authentication auth, HttpServletResponse res) {
        authService.logout(usuarioAtual.resolver(auth));
        sessaoService.limparCookies(res);
    }

    public record MensagemResponse(String mensagem) {}
}
