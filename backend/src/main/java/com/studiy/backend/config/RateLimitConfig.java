package com.studiy.backend.config;

import com.studiy.backend.security.JwtAuthFilter;
import com.studiy.backend.security.RateLimitFilter;
import com.studiy.backend.security.ratelimit.Limitador;
import com.studiy.backend.security.ratelimit.LimitadorLocal;
import com.studiy.backend.security.ratelimit.LimitadorRedis;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Rate limit e filtros de segurança. Os filtros entram SÓ na cadeia do Spring Security
 * (SecurityConfig); os FilterRegistrationBean desligados impedem o Spring Boot de registrá-los
 * também como filtros de servlet (o que os faria rodar fora de ordem, antes da autenticação).
 */
@Configuration
public class RateLimitConfig {

    private static final Logger log = LoggerFactory.getLogger(RateLimitConfig.class);

    /** Sem STUDIY_REDIS_URL: buckets em memória (uma instância). Com ela: Redis (várias instâncias). */
    @Bean
    public Limitador limitador(@Value("${studiy.ratelimit.redis-url:}") String redisUrl) {
        if (redisUrl == null || redisUrl.isBlank()) {
            log.info("Rate limit em memória (uma instância). Para várias instâncias defina STUDIY_REDIS_URL.");
            return new LimitadorLocal();
        }
        log.info("Rate limit compartilhado via Redis.");
        return new LimitadorRedis(redisUrl);
    }

    @Bean
    public RateLimitFilter rateLimitFilter(
            Limitador limitador,
            @Value("${studiy.ratelimit.auth-por-minuto}") int limiteAuth,
            @Value("${studiy.ratelimit.ia-por-minuto}") int limiteIa,
            @Value("${studiy.ratelimit.upload-por-minuto}") int limiteUpload
    ) {
        return new RateLimitFilter(limitador, limiteAuth, limiteIa, limiteUpload);
    }

    @Bean
    public FilterRegistrationBean<RateLimitFilter> naoRegistrarRateLimitNoServlet(RateLimitFilter f) {
        FilterRegistrationBean<RateLimitFilter> r = new FilterRegistrationBean<>(f);
        r.setEnabled(false);
        return r;
    }

    @Bean
    public FilterRegistrationBean<JwtAuthFilter> naoRegistrarJwtNoServlet(JwtAuthFilter f) {
        FilterRegistrationBean<JwtAuthFilter> r = new FilterRegistrationBean<>(f);
        r.setEnabled(false);
        return r;
    }
}
