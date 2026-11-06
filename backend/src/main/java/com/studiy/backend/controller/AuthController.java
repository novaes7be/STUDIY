package com.studiy.backend.controller;

import com.studiy.backend.dto.AuthResponse;
import com.studiy.backend.dto.ForgotPasswordRequest;
import com.studiy.backend.dto.LoginRequest;
import com.studiy.backend.dto.RegisterRequest;
import com.studiy.backend.dto.ResetPasswordRequest;
import com.studiy.backend.dto.VerificarEmailRequest;
import com.studiy.backend.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final com.studiy.backend.security.UsuarioAtual usuarioAtual;

    public AuthController(AuthService authService, com.studiy.backend.security.UsuarioAtual usuarioAtual) {
        this.authService = authService;
        this.usuarioAtual = usuarioAtual;
    }

    @PostMapping("/registrar")
    public AuthResponse registrar(@Valid @RequestBody RegisterRequest request) {
        return authService.registrar(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
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
    public void logout(org.springframework.security.core.Authentication auth) {
        authService.logout(usuarioAtual.resolver(auth));
    }

    public record MensagemResponse(String mensagem) {}
}
