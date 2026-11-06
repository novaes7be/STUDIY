package com.studiy.backend.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/** Documentação da API (Swagger UI em /swagger-ui.html). Só existe no perfil dev. */
@Configuration
@Profile("dev")
public class SwaggerConfig {

    @Bean
    public OpenAPI studiyOpenApi() {
        return new OpenAPI()
                .info(new Info().title("Studiy API").version("0.1.0")
                        .description("Sessão em cookies HttpOnly (studiy_at/studiy_rt). Faça login pelo front em "
                                + "localhost e as chamadas daqui usam os mesmos cookies; escritas exigem X-XSRF-TOKEN."));
    }

    /** Cadeia própria (CSP mais branda) só para a documentação; o restante da API segue a cadeia principal. */
    @Bean
    @Order(1)
    public SecurityFilterChain swaggerFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**")
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(h -> h.contentSecurityPolicy(c -> c.policyDirectives(
                        "default-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; frame-ancestors 'none'")))
                .authorizeHttpRequests(a -> a.anyRequest().permitAll());
        return http.build();
    }
}
