package com.studiy.backend.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtil {

    private final SecretKey secretKey;
    private final long expirationMs;

    public JwtUtil(
            @Value("${studiy.jwt.secret}") String secret,
            @Value("${studiy.jwt.expiration-ms}") long expirationMs
    ) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("JWT_SECRET precisa ter pelo menos 32 caracteres");
        }
        this.secretKey = Keys.hmacShaKeyFor(bytes);
        this.expirationMs = expirationMs;
    }

    public long getExpirationMs() {
        return expirationMs;
    }

    /** O claim "tv" (token version) permite revogar todas as sessões de um usuário. */
    public String gerarToken(String email, int tokenVersion) {
        Date agora = new Date();
        return Jwts.builder()
                .subject(email)
                .claim("tv", tokenVersion)
                .issuedAt(agora)
                .expiration(new Date(agora.getTime() + expirationMs))
                .signWith(secretKey)
                .compact();
    }

    public Claims lerClaims(String token) {
        return Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).getPayload();
    }

    /** Retorna os claims se o token for válido e não expirado; senão null. */
    public Claims validar(String token) {
        try {
            Claims c = lerClaims(token);
            return c.getExpiration().after(new Date()) ? c : null;
        } catch (Exception e) {
            return null;
        }
    }
}
