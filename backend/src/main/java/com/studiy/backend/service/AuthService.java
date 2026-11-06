package com.studiy.backend.service;

import com.studiy.backend.dto.ForgotPasswordRequest;
import com.studiy.backend.dto.LoginRequest;
import com.studiy.backend.dto.RegisterRequest;
import com.studiy.backend.dto.ResetPasswordRequest;
import com.studiy.backend.model.Plano;
import com.studiy.backend.model.Usuario;
import com.studiy.backend.repository.UsuarioRepository;
import com.studiy.backend.security.SessaoService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
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
    static final String MSG_GENERICA_CADASTRO =
            "Pronto! Se o e-mail puder ser usado, enviamos um link para confirmar. Depois é só entrar.";
    private static final String MSG_GENERICA_VERIF = "Se o e-mail existir e ainda não estiver confirmado, enviamos um novo link.";

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final BloqueioLoginService bloqueio;
    private final String hashFicticio;
    private final long tempoMinimoLoginMs;
    private final SessaoService sessaoService;
    private final boolean exporTokenResetDev;
    private final String versaoTermos;
    private final GamificacaoService gamificacaoService;
    private final EmailService emailService;
    private final SecureRandom random = new SecureRandom();

    public AuthService(
            UsuarioRepository usuarioRepository,
            PasswordEncoder passwordEncoder,
            BloqueioLoginService bloqueio,
            @Value("${studiy.auth.login-tempo-minimo-ms:400}") long tempoMinimoLoginMs,
            SessaoService sessaoService,
            @Value("${studiy.auth.expor-token-reset-dev}") boolean exporTokenResetDev,
            @Value("${studiy.termos.versao}") String versaoTermos,
            GamificacaoService gamificacaoService,
            EmailService emailService
    ) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.bloqueio = bloqueio;
        this.tempoMinimoLoginMs = tempoMinimoLoginMs;
        // Hash bcrypt de uma senha aleatória: comparado quando o e-mail não existe, para o custo ser o mesmo.
        this.hashFicticio = passwordEncoder.encode(java.util.UUID.randomUUID().toString());
        this.sessaoService = sessaoService;
        this.exporTokenResetDev = exporTokenResetDev;
        this.versaoTermos = versaoTermos;
        this.gamificacaoService = gamificacaoService;
        this.emailService = emailService;
    }

    private static String normalizar(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Cadastro sem enumeração: a resposta (e o trabalho feito, incluindo o bcrypt) é a mesma para e-mail
     * novo ou já cadastrado. Quem já tem conta recebe um e-mail avisando; conta nova recebe o link de verificação.
     * Nenhuma sessão é criada aqui: o usuário entra pelo login.
     */
    public String registrar(RegisterRequest request) {
        String email = normalizar(request.email());
        String senhaHash = passwordEncoder.encode(request.senha()); // sempre, para o tempo não denunciar o caminho

        if (usuarioRepository.existsByEmail(email)) {
            emailService.enviarAvisoContaExistente(email);
            return MSG_GENERICA_CADASTRO;
        }
        Usuario usuario = Usuario.builder()
                .nome(request.nome().trim())
                .email(email)
                .senha(senhaHash)
                .plano(Plano.FREE) // o plano NUNCA vem do cliente
                .termosAceitosEm(LocalDateTime.now())
                .termosVersao(versaoTermos)
                .emailVerificado(false)
                .onboardingConcluido(false)
                .build();
        try {
            usuarioRepository.saveAndFlush(usuario);
        } catch (DataIntegrityViolationException corrida) { // dois cadastros simultâneos do mesmo e-mail
            emailService.enviarAvisoContaExistente(email);
            return MSG_GENERICA_CADASTRO;
        }
        enviarVerificacao(usuario);
        return MSG_GENERICA_CADASTRO;
    }

    /**
     * Login com tempo de resposta constante e sem enumeração:
     *  - sempre exatamente UMA verificação bcrypt (contra um hash fictício se o e-mail não existir);
     *  - conta bloqueada, e-mail inexistente e senha errada dão a MESMA resposta 401;
     *  - a resposta nunca sai antes de um tempo mínimo (absorve a diferença das escritas no banco).
     */
    public Usuario login(LoginRequest request) {
        long inicio = System.nanoTime();
        try {
            String email = normalizar(request.email());
            Usuario usuario = usuarioRepository.findByEmail(email).orElse(null);
            boolean senhaOk = passwordEncoder.matches(request.senha(), usuario != null ? usuario.getSenha() : hashFicticio);

            if (usuario == null) {
                throw credenciaisInvalidas();
            }
            if (bloqueio.bloqueado(usuario)) {
                throw credenciaisInvalidas(); // nem a senha certa entra durante o bloqueio
            }
            if (!senhaOk) {
                bloqueio.registrarFalha(usuario.getId());
                throw credenciaisInvalidas();
            }
            bloqueio.limpar(usuario);
            gamificacaoService.registrarAtividade(usuario); // salva a entidade (inclui o contador zerado)
            return usuario;
        } finally {
            aguardarTempoMinimo(inicio);
        }
    }

    private static ResponseStatusException credenciaisInvalidas() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                "E-mail ou senha inválidos. Depois de várias tentativas, o acesso fica bloqueado por alguns minutos.");
    }

    private void aguardarTempoMinimo(long inicioNanos) {
        long restante = tempoMinimoLoginMs - (System.nanoTime() - inicioNanos) / 1_000_000;
        if (restante > 0) {
            try {
                Thread.sleep(restante);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /** Encerra todas as sessões do usuário (invalida tokens já emitidos). */
    public void logout(Usuario usuario) {
        revogarSessoes(usuario);
    }

    /**
     * Token aleatório de 256 bits, uso único, validade de 30 minutos.
     * Só o hash SHA-256 fica no banco e o token NUNCA é escrito nos logs.
     * Envia por e-mail o link {front}/recuperar-senha.html#token=... (EmailService; só se o SMTP estiver configurado).
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
        emailService.enviarResetSenha(usuario.getEmail(), token);

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
        bloqueio.limpar(usuario);              // provou ser o dono do e-mail: sai do bloqueio
        revogarSessoes(usuario);               // troca de senha derruba as sessões abertas
    }

    /** Token de 256 bits, uso único, 24 h; só o hash fica no banco e o token nunca vai para os logs. */
    void enviarVerificacao(Usuario usuario) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        usuario.setVerifTokenHash(sha256(token));
        usuario.setVerifTokenExpira(LocalDateTime.now().plusHours(24));
        usuarioRepository.save(usuario);
        emailService.enviarVerificacao(usuario.getEmail(), token);
    }

    public void verificarEmail(String token) {
        Usuario usuario = usuarioRepository.findByVerifTokenHash(sha256(token))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Link inválido ou expirado"));
        if (usuario.getVerifTokenExpira() == null || usuario.getVerifTokenExpira().isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Link inválido ou expirado");
        }
        usuario.setEmailVerificado(true);
        usuario.setVerifTokenHash(null);   // uso único
        usuario.setVerifTokenExpira(null);
        usuarioRepository.save(usuario);
    }

    public String reenviarVerificacao(ForgotPasswordRequest request) {
        usuarioRepository.findByEmail(normalizar(request.email()))
                .filter(u -> !u.isEmailVerificado())
                .ifPresent(this::enviarVerificacao);
        return MSG_GENERICA_VERIF; // não revela se o e-mail existe
    }

    /** Recursos que custam dinheiro (IA, pagamento) só para quem confirmou o e-mail. */
    public static void exigirEmailVerificado(Usuario usuario) {
        if (!usuario.isEmailVerificado()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Confirme seu e-mail para usar este recurso. Não recebeu o link? Peça outro no seu perfil.");
        }
    }

    /** Invalida tokens de acesso já emitidos (versão) e todos os refresh tokens. */
    public void revogarSessoes(Usuario usuario) {
        usuario.setTokenVersion(usuario.getTokenVersion() + 1);
        usuarioRepository.save(usuario);
        sessaoService.revogarTodas(usuario);
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
