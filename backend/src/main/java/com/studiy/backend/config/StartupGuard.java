package com.studiy.backend.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Guarda de inicialização (fail-closed). Roda antes de qualquer bean e ABORTA a subida se:
 *  - o JWT secret tiver menos de 32 bytes (em qualquer perfil);
 *  - fora do perfil dev: o JWT secret ou a chave de criptografia forem valores públicos conhecidos,
 *    ou a simulação de pagamento / exposição do token de reset estiverem ligadas;
 *  - cookies sem Secure fora de dev, ou CORS com curinga;
 *  - o perfil dev estiver ativo junto com o prod.
 * Registrada em META-INF/spring.factories.
 */
public class StartupGuard implements EnvironmentPostProcessor, Ordered {

    /** Valores que já estiveram no repositório público: nunca podem proteger dados reais. */
    static final Set<String> JWT_SECRETS_CONHECIDOS = Set.of(
            "dev-only-jwt-secret-change-me-0123456789abcdef");
    static final Set<String> CHAVES_CRIPTO_CONHECIDAS = Set.of(
            "7a0KtYJAFva4wUDkbHJ9lAPMQZBhbqDvKI2S/YVKUcg=");

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment env, SpringApplication app) {
        List<String> erros = verificar(env);
        if (!erros.isEmpty()) {
            throw new IllegalStateException("StartupGuard: configuração insegura, a aplicação NÃO vai subir:\n - "
                    + String.join("\n - ", erros));
        }
    }

    /** Lista de problemas encontrados (vazia = ok). Pública para os testes. */
    public static List<String> verificar(Environment env) {
        List<String> erros = new ArrayList<>();
        List<String> perfis = Arrays.asList(env.getActiveProfiles());
        boolean dev = perfis.contains("dev");

        if (dev && perfis.contains("prod")) {
            erros.add("os perfis dev e prod não podem estar ativos ao mesmo tempo");
        }

        String jwt = valor(env, "studiy.jwt.secret", erros);
        String chave = valor(env, "studiy.crypto.key", erros);

        if (jwt != null && jwt.getBytes(StandardCharsets.UTF_8).length < 32) {
            erros.add("JWT_SECRET precisa ter pelo menos 32 bytes (use: openssl rand -base64 48)");
        }
        if (!dev) {
            if (jwt != null && JWT_SECRETS_CONHECIDOS.contains(jwt.trim())) {
                erros.add("JWT_SECRET é o valor público de desenvolvimento; gere um novo");
            }
            if (chave != null && CHAVES_CRIPTO_CONHECIDAS.contains(chave.trim())) {
                erros.add("STUDIY_ENCRYPTION_KEY é a chave pública de desenvolvimento; gere uma nova");
            }
            if (env.getProperty("studiy.pagamento.simulacao", Boolean.class, false)) {
                erros.add("studiy.pagamento.simulacao=true só é permitido no perfil dev");
            }
            if (env.getProperty("studiy.auth.expor-token-reset-dev", Boolean.class, false)) {
                erros.add("studiy.auth.expor-token-reset-dev=true só é permitido no perfil dev");
            }
            if (!env.getProperty("studiy.auth.cookie-secure", Boolean.class, true)) {
                erros.add("studiy.auth.cookie-secure=false só é permitido no perfil dev");
            }
        }
        String extrator = env.getProperty("studiy.extrator.url", "");
        if (!extrator.isBlank() && env.getProperty("studiy.extrator.token", "").length() < 16) {
            erros.add("EXTRATOR_TOKEN precisa ter 16+ caracteres quando EXTRATOR_URL está definido (use: openssl rand -hex 32)");
        }
        String origens = valor(env, "studiy.cors.origins", erros);
        if (origens != null && origens.contains("*")) {
            erros.add("studiy.cors.origins não aceita curinga (*): liste as origens exatas do front");
        }
        return erros;
    }

    private static String valor(Environment env, String chave, List<String> erros) {
        try {
            String v = env.getProperty(chave);
            if (v == null || v.isBlank()) {
                erros.add(chave + " não definido");
                return null;
            }
            return v;
        } catch (IllegalArgumentException e) { // placeholder ${VAR} sem valor
            erros.add(chave + " não definido (variável de ambiente ausente)");
            return null;
        }
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE; // depois de carregar todos os application-*.properties
    }
}
