package com.studiy.backend.security;

import com.studiy.backend.model.Papel;
import com.studiy.backend.model.Usuario;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import com.studiy.backend.repository.UsuarioRepository;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UsuarioRepository usuarioRepository;

    public JwtAuthFilter(JwtUtil jwtUtil, UsuarioRepository usuarioRepository) {
        this.jwtUtil = jwtUtil;
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        // O token de acesso vem SÓ do cookie HttpOnly (o JavaScript do front nunca o vê).
        String token = SessaoService.lerCookie(request, SessaoService.COOKIE_ACESSO);

        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            Claims claims = jwtUtil.validar(token);
            if (claims != null) {
                Usuario usuario = usuarioRepository.findByEmail(claims.getSubject()).orElse(null);
                Integer versaoToken = claims.get("tv", Integer.class);
                // Token só vale se a versão bater (logout/troca de senha invalidam tokens antigos).
                if (usuario != null && versaoToken != null && versaoToken.equals(usuario.getTokenVersion())) {
                    var auth = new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, papeis(usuario, claims));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            }
        }
        filterChain.doFilter(request, response);
    }

    /**
     * ROLE_ADMIN só existe se as TRÊS coisas forem verdade: papel ADMIN no banco (lido agora, não do token),
     * MFA ativo na conta e esta sessão ter passado pelo TOTP. Senha sozinha nunca dá acesso de admin.
     */
    static List<SimpleGrantedAuthority> papeis(Usuario usuario, Claims claims) {
        boolean sessaoComMfa = Boolean.TRUE.equals(claims.get("mfa", Boolean.class));
        if (usuario.getPapel() == Papel.ADMIN && usuario.isMfaAtivo() && sessaoComMfa) {
            return List.of(new SimpleGrantedAuthority("ROLE_USUARIO"), new SimpleGrantedAuthority("ROLE_ADMIN"));
        }
        return List.of(new SimpleGrantedAuthority("ROLE_USUARIO"));
    }
}
