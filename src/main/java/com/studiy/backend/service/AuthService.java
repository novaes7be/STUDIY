package com.studiy.backend.service;

import com.studiy.backend.dto.AuthResponse;
import com.studiy.backend.dto.ForgotPasswordRequest;
import com.studiy.backend.dto.LoginRequest;
import com.studiy.backend.dto.RegisterRequest;
import com.studiy.backend.dto.ResetPasswordRequest;
import com.studiy.backend.model.Plano;
import com.studiy.backend.model.Usuario;
import com.studiy.backend.repository.UsuarioRepository;
import com.studiy.backend.security.JwtUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;

@Service
public class AuthService {

    private static final String MSG_GENERICA_RESET = "Se o e-mail existir, as instruções de recuperação foram enviadas.";

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final boolean exporTokenResetDev;
    private final String versaoTermos;
    private final GamificacaoService gamificacaoService;
    private final SecureRandom random = new SecureRandom();

    public AuthService(
            UsuarioRepository usuarioRepository,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager,
            JwtUtil jwtUtil,
            @Value("${studiy.auth.expor-token-reset-dev}") boolean exporTokenResetDev,
            @Value("${studiy.termos.versao}") String versaoTermos,
            GamificacaoService gamificacaoService
    ) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtUtil = jwtUtil;
        this.exporTokenResetDev = exporTokenResetDev;
        this.versaoTermos = versaoTermos;
        this.gamificacaoService = gamificacaoService;
    }

    private static String normalizar(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    public AuthResponse registrar(RegisterRequest request) {
        String email = normalizar(request.email());
        if (usuarioRepository.existsByEmail(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Não foi possível criar a conta. Se você já tem cadastro, entre ou recupere a senha.");
        }

        Usuario usuario = Usuario.builder()
                .nome(request.nome().trim())
                .email(email)
                .senha(passwordEncoder.encode(request.senha()))
                .plano(Plano.FREE) // o plano NUNCA vem do cliente
                .termosAceitosEm(LocalDateTime.now())
                .termosVersao(versaoTermos)
                .build();
        usuarioRepository.save(usuario);

        return resposta(usuario);
    }

    public AuthResponse login(LoginRequest request) {
        String email = normalizar(request.email());
        // Lança BadCredentialsException (tratada como 401 genérico) se e-mail/senha não baterem.
        authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(email, request.senha()));

        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciais inválidas"));

        gamificacaoService.registrarAtividade(usuario);
        return resposta(usuario);
    }

    /** Encerra todas as sessões do usuário (invalida tokens já emitidos). */
    public void logout(Usuario usuario) {
        revogarSessoes(usuario);
    }

    /**
     * Token aleatório de 256 bits, uso único, validade de 30 minutos.
     * Só o hash SHA-256 fica no banco e o token NUNCA é escrito nos logs.
     * TODO produção: enviar por e-mail um link como
     * https://seusite/recuperar-senha.html#token=... (JavaMailSender).
     */
    public String esqueciSenha(ForgotPasswordRequest request) {
        Usuario usuario = usuarioRepository.findByEmail(normalizar(request.email())).orElse(null);
        if (usuario == null) {
            return MSG_GENERICA_RESET; // não revela se o e-mail existe
        }

        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        usuario.setResetTokenHash(sha256(token));
        usuario.setResetTokenExpira(LocalDateTime.now().plusMinutes(30));
        usuarioRepository.save(usuario);

        if (exporTokenResetDev) {
            return "Modo desenvolvimento — seu token: " + token;
        }
        return MSG_GENERICA_RESET;
    }

    public void redefinirSenha(ResetPasswordRequest request) {
        Usuario usuario = usuarioRepository.findByResetTokenHash(sha256(request.token()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Token inválido ou expirado"));

        if (usuario.getResetTokenExpira() == null || usuario.getResetTokenExpira().isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Token inválido ou expirado");
        }

        usuario.setSenha(passwordEncoder.encode(request.novaSenha()));
        usuario.setResetTokenHash(null);       // uso único
        usuario.setResetTokenExpira(null);
        revogarSessoes(usuario);               // troca de senha derruba as sessões abertas
    }

    private void revogarSessoes(Usuario usuario) {
        usuario.setTokenVersion(usuario.getTokenVersion() + 1);
        usuarioRepository.save(usuario);
    }

    private AuthResponse resposta(Usuario usuario) {
        String token = jwtUtil.gerarToken(usuario.getEmail(), usuario.getTokenVersion());
        return new AuthResponse(token, usuario.getNome(), usuario.getEmail(), usuario.planoEfetivo());
    }

    static String sha256(String valor) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(valor.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
