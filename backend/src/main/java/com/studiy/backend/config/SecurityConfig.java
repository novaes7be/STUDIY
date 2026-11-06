package com.studiy.backend.config;

import com.studiy.backend.security.JwtAuthFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.lang.NonNull;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final String JSON_401 = "{\"erro\":\"Não autenticado\"}";
    private static final String JSON_403 = "{\"erro\":\"Acesso negado\"}";

    private final JwtAuthFilter jwtAuthFilter;
    private final List<String> origensPermitidas;
    private final boolean cookieSecure;

    public SecurityConfig(
            JwtAuthFilter jwtAuthFilter,
            @Value("${studiy.cors.origins}") String origens,
            @Value("${studiy.auth.cookie-secure:true}") boolean cookieSecure
    ) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.origensPermitidas = Arrays.stream(origens.split(",")).map(String::trim).filter(s -> !s.isBlank()).toList();
        this.cookieSecure = cookieSecure;
    }

    /** bcrypt (custo 12). Senhas nunca são guardadas em texto puro. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        // CSRF double-submit: cookie XSRF-TOKEN legível pelo JS + header X-XSRF-TOKEN igual em toda escrita.
        // Complementa o SameSite=Strict dos cookies de sessão.
        CookieCsrfTokenRepository csrfRepo = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfRepo.setCookieCustomizer(c -> c.secure(cookieSecure).sameSite("Strict").path("/"));

        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfRepo)
                        // valor puro no header (o SPA lê do cookie); sem a máscara BREACH, que não se aplica aqui
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                        // servidor-a-servidor, autenticado pela assinatura x-signature do Mercado Pago
                        .ignoringRequestMatchers("/api/pagamentos/webhook"))
                .addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(h -> h
                        .frameOptions(f -> f.deny())
                        .httpStrictTransportSecurity(s -> s.includeSubDomains(true).maxAgeInSeconds(31536000))
                        .referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                        .contentSecurityPolicy(c -> c.policyDirectives("default-src 'none'; frame-ancestors 'none'")))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(HttpMethod.POST,
                                "/api/auth/registrar", "/api/auth/login", "/api/auth/refresh",
                                "/api/auth/esqueci-senha", "/api/auth/redefinir-senha",
                                "/api/auth/verificar-email", "/api/auth/reenviar-verificacao").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/saude", "/api/auth/csrf").permitAll()
                        // Webhook do Mercado Pago: público, mas o serviço só aceita x-signature válido
                        .requestMatchers(HttpMethod.POST, "/api/pagamentos/webhook").permitAll()
                        .requestMatchers("/error").permitAll()
                        // Tudo o mais exige sessão válida (negado por padrão).
                        .anyRequest().authenticated())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) -> escrever(res, 401, JSON_401))
                        .accessDeniedHandler((req, res, ex) -> escrever(res, 403, JSON_403)))
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /** Origens EXATAS (sem curinga): com allowCredentials, um padrão largo exporia a sessão a outro site. */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(origensPermitidas);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Content-Type", "X-XSRF-TOKEN"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

    /** O token CSRF é carregado de forma preguiçosa; isto força a gravação do cookie XSRF-TOKEN em toda resposta. */
    static final class CsrfCookieFilter extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(@NonNull HttpServletRequest req, @NonNull HttpServletResponse res,
                                        @NonNull FilterChain chain) throws ServletException, IOException {
            CsrfToken token = (CsrfToken) req.getAttribute(CsrfToken.class.getName());
            if (token != null) token.getToken();
            chain.doFilter(req, res);
        }
    }

    private static void escrever(HttpServletResponse res, int status, String json) throws IOException {
        res.setStatus(status);
        res.setContentType("application/json;charset=UTF-8");
        res.getWriter().write(json);
    }
}
