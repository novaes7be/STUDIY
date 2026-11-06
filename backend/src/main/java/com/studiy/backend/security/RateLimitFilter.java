package com.studiy.backend.security;

import com.studiy.backend.security.ratelimit.Limitador;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Rate limit (token bucket, Bucket4j). Roda DENTRO da cadeia do Spring Security, logo depois do
 * JwtAuthFilter, então o limite "por usuário" usa o usuário autenticado de verdade:
 *  - /api/auth/**             -> por IP
 *  - POST .../gerar (IA)      -> por usuário
 *  - POST .../pdfs (upload)   -> por usuário
 *  - checkout / troca de senha e e-mail -> por usuário
 * O IP vem de getRemoteAddr(), que só reflete X-Forwarded-For quando o proxy é confiável
 * (server.forward-headers-strategy + server.tomcat.remoteip.internal-proxies no perfil prod).
 * Não é @Component de propósito: não pode ser registrado também como filtro de servlet.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private final Limitador limitador;
    private final int limiteAuth;
    private final int limiteIa;
    private final int limiteUpload;

    public RateLimitFilter(Limitador limitador, int limiteAuth, int limiteIa, int limiteUpload) {
        this.limitador = limitador;
        this.limiteAuth = limiteAuth;
        this.limiteIa = limiteIa;
        this.limiteUpload = limiteUpload;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest req, @NonNull HttpServletResponse res, @NonNull FilterChain chain
    ) throws ServletException, IOException {
        String path = req.getRequestURI();
        String metodo = req.getMethod();
        String chave = null;
        int limite = 0;

        if (path.startsWith("/api/auth/") && !"OPTIONS".equals(metodo) && !"GET".equals(metodo)) {
            chave = "auth:ip:" + req.getRemoteAddr();
            limite = limiteAuth;
        } else if ("POST".equals(metodo) && path.matches("/api/materias/\\d+/gerar")) {
            chave = "ia:" + usuario(req);
            limite = limiteIa;
        } else if ("POST".equals(metodo) && path.matches("/api/materias/\\d+/pdfs")) {
            chave = "up:" + usuario(req);
            limite = limiteUpload;
        } else if ("POST".equals(metodo) && path.equals("/api/pagamentos/checkout")) {
            chave = "pay:" + usuario(req);
            limite = limiteUpload;
        } else if ("PUT".equals(metodo) && path.matches("/api/usuarios/me/(senha|email)")) {
            chave = "conta:" + usuario(req); // protege contra tentativa de adivinhar a senha atual
            limite = limiteAuth;
        }

        if (chave != null && !limitador.consumir(chave, limite)) {
            res.setStatus(429);
            res.setHeader("Retry-After", "60");
            res.setContentType("application/json;charset=UTF-8");
            res.getWriter().write("{\"erro\":\"Muitas requisições. Aguarde um minuto e tente novamente.\"}");
            return;
        }
        chain.doFilter(req, res);
    }

    /** Usuário autenticado (o JwtAuthFilter já rodou); sem sessão, cai para o IP. */
    static String usuario(HttpServletRequest req) {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        boolean logado = a != null && a.isAuthenticated() && !(a instanceof AnonymousAuthenticationToken);
        return logado ? "u:" + a.getName() : "ip:" + req.getRemoteAddr();
    }
}
