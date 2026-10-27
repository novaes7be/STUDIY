package com.studiy.backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate limit simples em memória (janela fixa de 1 minuto):
 *  - /api/auth/**             -> por IP
 *  - POST .../gerar (IA)      -> por usuário
 *  - POST .../pdfs (upload)   -> por usuário
 * Para mais de uma instância do servidor, troque por Redis/Bucket4j.
 * Atrás de proxy/CDN configure server.forward-headers-strategy para o IP real.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final long JANELA_MS = 60_000L;

    private final int limiteAuth;
    private final int limiteIa;
    private final int limiteUpload;
    private final ConcurrentHashMap<String, long[]> janelas = new ConcurrentHashMap<>();

    public RateLimitFilter(
            @Value("${studiy.ratelimit.auth-por-minuto}") int limiteAuth,
            @Value("${studiy.ratelimit.ia-por-minuto}") int limiteIa,
            @Value("${studiy.ratelimit.upload-por-minuto}") int limiteUpload
    ) {
        this.limiteAuth = limiteAuth;
        this.limiteIa = limiteIa;
        this.limiteUpload = limiteUpload;
    }

    /** true se a chamada pode seguir; false se estourou o limite da janela atual. */
    public boolean permitir(String chave, int limite) {
        long agora = System.currentTimeMillis();
        if (janelas.size() > 20_000) {
            janelas.entrySet().removeIf(e -> agora - e.getValue()[0] > JANELA_MS);
        }
        boolean[] ok = {true};
        janelas.compute(chave, (k, v) -> {
            if (v == null || agora - v[0] > JANELA_MS) {
                return new long[]{agora, 1};
            }
            v[1]++;
            ok[0] = v[1] <= limite;
            return v;
        });
        return ok[0];
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest req, @NonNull HttpServletResponse res, @NonNull FilterChain chain
    ) throws ServletException, IOException {
        String path = req.getRequestURI();
        String chave = null;
        int limite = 0;

        if (path.startsWith("/api/auth/") && !"OPTIONS".equals(req.getMethod())) {
            chave = "auth:" + req.getRemoteAddr();
            limite = limiteAuth;
        } else if ("POST".equals(req.getMethod()) && path.matches("/api/materias/\\d+/gerar")) {
            chave = "ia:" + usuario(req);
            limite = limiteIa;
        } else if ("POST".equals(req.getMethod()) && path.matches("/api/materias/\\d+/pdfs")) {
            chave = "up:" + usuario(req);
            limite = limiteUpload;
        } else if ("PUT".equals(req.getMethod()) && path.matches("/api/usuarios/me/(senha|email)")) {
            chave = "conta:" + usuario(req); // protege contra tentativa de adivinhar a senha atual
            limite = limiteAuth;
        }

        if (chave != null && !permitir(chave, limite)) {
            res.setStatus(429);
            res.setHeader("Retry-After", "60");
            res.setContentType("application/json;charset=UTF-8");
            res.getWriter().write("{\"erro\":\"Muitas requisições. Aguarde um minuto e tente novamente.\"}");
            return;
        }
        chain.doFilter(req, res);
    }

    private String usuario(HttpServletRequest req) {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return a != null && a.isAuthenticated() ? a.getName() : req.getRemoteAddr();
    }
}