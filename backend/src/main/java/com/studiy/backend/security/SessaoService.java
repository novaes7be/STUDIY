package com.studiy.backend.security;

import com.studiy.backend.model.SessaoRefresh;
import com.studiy.backend.model.Usuario;
import com.studiy.backend.repository.SessaoRefreshRepository;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Sessão em cookies (o JavaScript do front nunca vê nenhum token):
 *  - studiy_at: JWT de acesso curto (15 min), HttpOnly, Secure, SameSite=Strict, Path=/api;
 *  - studiy_rt: refresh token opaco (256 bits), HttpOnly, Secure, SameSite=Strict, Path=/api/auth,
 *    rotativo: cada renovação troca o token; reusar um token já trocado revoga a família (roubo provável).
 * O CSRF (double-submit) fica no SecurityConfig.
 */
@Service
public class SessaoService {

    public static final String COOKIE_ACESSO = "studiy_at";
    public static final String COOKIE_REFRESH = "studiy_rt";

    private final JwtUtil jwtUtil;
    private final SessaoRefreshRepository repo;
    private final boolean secure;
    private final Duration duracaoRefresh;
    private final SecureRandom random = new SecureRandom();

    public SessaoService(JwtUtil jwtUtil, SessaoRefreshRepository repo,
                         @Value("${studiy.auth.cookie-secure:true}") boolean secure,
                         @Value("${studiy.jwt.refresh-dias:14}") long refreshDias) {
        this.jwtUtil = jwtUtil;
        this.repo = repo;
        this.secure = secure;
        this.duracaoRefresh = Duration.ofDays(refreshDias);
    }

    /** Login (ou troca de senha/e-mail): nova família de refresh e novo token de acesso, sem MFA. */
    @Transactional
    public void emitir(Usuario usuario, HttpServletResponse res) {
        emitir(usuario, UUID.randomUUID().toString(), false, res);
    }

    /** Depois de verificar o TOTP: sessão nova (nova família) marcada com MFA. */
    @Transactional
    public void emitirComMfa(Usuario usuario, HttpServletResponse res) {
        emitir(usuario, UUID.randomUUID().toString(), true, res);
    }

    /** Troca o refresh token por um novo (mesma família) e emite novo token de acesso. */
    @Transactional(noRollbackFor = ResponseStatusException.class)
    public Usuario renovar(HttpServletRequest req, HttpServletResponse res) {
        String token = lerCookie(req, COOKIE_REFRESH);
        if (token == null) throw naoAutenticado(res);

        SessaoRefresh atual = repo.buscarComTrava(sha256(token)).orElse(null);
        if (atual == null) throw naoAutenticado(res);
        if (atual.getUsadoEm() != null) {
            repo.revogarFamilia(atual.getFamilia()); // token já trocado sendo reusado: derruba a família inteira
            throw naoAutenticado(res);
        }
        if (atual.getExpiraEm().isBefore(LocalDateTime.now())) {
            repo.delete(atual);
            throw naoAutenticado(res);
        }
        atual.setUsadoEm(LocalDateTime.now());
        repo.save(atual);
        emitir(atual.getUsuario(), atual.getFamilia(), atual.isMfa(), res);
        return atual.getUsuario();
    }

    /** Revoga TODOS os refresh tokens do usuário (logout, troca de senha, exclusão). */
    @Transactional
    public void revogarTodas(Usuario usuario) {
        repo.revogarDoUsuario(usuario.getId());
    }

    public void limparCookies(HttpServletResponse res) {
        res.addHeader(HttpHeaders.SET_COOKIE, cookie(COOKIE_ACESSO, "", "/api", Duration.ZERO).toString());
        res.addHeader(HttpHeaders.SET_COOKIE, cookie(COOKIE_REFRESH, "", "/api/auth", Duration.ZERO).toString());
    }

    public static String lerCookie(HttpServletRequest req, String nome) {
        if (req.getCookies() == null) return null;
        for (Cookie c : req.getCookies()) {
            if (nome.equals(c.getName()) && c.getValue() != null && !c.getValue().isBlank()) return c.getValue();
        }
        return null;
    }

    private void emitir(Usuario usuario, String familia, boolean mfa, HttpServletResponse res) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String refresh = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        LocalDateTime agora = LocalDateTime.now();
        repo.save(SessaoRefresh.builder()
                .usuario(usuario)
                .tokenHash(sha256(refresh))
                .familia(familia)
                .mfa(mfa)
                .criadoEm(agora)
                .expiraEm(agora.plus(duracaoRefresh))
                .build());

        String acesso = jwtUtil.gerarToken(usuario.getEmail(), usuario.getTokenVersion(), mfa);
        res.addHeader(HttpHeaders.SET_COOKIE,
                cookie(COOKIE_ACESSO, acesso, "/api", Duration.ofMillis(jwtUtil.getExpirationMs())).toString());
        res.addHeader(HttpHeaders.SET_COOKIE, cookie(COOKIE_REFRESH, refresh, "/api/auth", duracaoRefresh).toString());
    }

    private ResponseStatusException naoAutenticado(HttpServletResponse res) {
        limparCookies(res);
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sessão expirada. Entre novamente.");
    }

    private ResponseCookie cookie(String nome, String valor, String path, Duration maxAge) {
        return ResponseCookie.from(nome, valor)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                .path(path)
                .maxAge(maxAge)
                .build();
    }

    static String sha256(String valor) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(valor.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
