package com.studiy.backend.config;

import com.studiy.backend.StudiyBackendApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.mock.env.MockEnvironment;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StartupGuardTest {

    private static final String JWT_FORTE = "um-segredo-aleatorio-bem-grande-0123456789-abcdefghij";
    private static final String CHAVE_FORTE = "q3Jx3b2c0m8Yt4yZ6S0nG9aQ8wE1rT5uI7oP2lK4jH0=";
    private static final String JWT_DEV = StartupGuard.JWT_SECRETS_CONHECIDOS.iterator().next();
    private static final String CHAVE_DEV = StartupGuard.CHAVES_CRIPTO_CONHECIDAS.iterator().next();

    private static MockEnvironment env(String jwt, String chave, String... perfis) {
        MockEnvironment e = new MockEnvironment();
        if (jwt != null) e.setProperty("studiy.jwt.secret", jwt);
        if (chave != null) e.setProperty("studiy.crypto.key", chave);
        e.setProperty("studiy.cors.origins", "https://studiy.app");
        e.setActiveProfiles(perfis);
        return e;
    }

    @Test
    void producaoComSegredosFortesSobe() {
        assertThat(StartupGuard.verificar(env(JWT_FORTE, CHAVE_FORTE, "prod"))).isEmpty();
    }

    @Test
    void semPerfilESemSegredosNaoSobe() {
        List<String> erros = StartupGuard.verificar(env(null, null));
        assertThat(erros).anyMatch(s -> s.contains("studiy.jwt.secret"))
                .anyMatch(s -> s.contains("studiy.crypto.key"));
    }

    @Test
    void valoresDeDevForaDoPerfilDevSaoBloqueados() {
        assertThat(StartupGuard.verificar(env(JWT_DEV, CHAVE_FORTE, "prod"))).anyMatch(s -> s.contains("JWT_SECRET"));
        assertThat(StartupGuard.verificar(env(JWT_FORTE, CHAVE_DEV))).anyMatch(s -> s.contains("STUDIY_ENCRYPTION_KEY"));
    }

    @Test
    void valoresDeDevNoPerfilDevSaoPermitidos() {
        MockEnvironment e = env(JWT_DEV, CHAVE_DEV, "dev");
        e.setProperty("studiy.pagamento.simulacao", "true");
        e.setProperty("studiy.auth.expor-token-reset-dev", "true");
        assertThat(StartupGuard.verificar(e)).isEmpty();
    }

    @Test
    void jwtCurtoEhBloqueadoEmQualquerPerfil() {
        assertThat(StartupGuard.verificar(env("curto", CHAVE_FORTE, "dev"))).anyMatch(s -> s.contains("32 bytes"));
    }

    @Test
    void simulacaoEExposicaoDeTokenForaDeDevSaoBloqueadas() {
        MockEnvironment e = env(JWT_FORTE, CHAVE_FORTE, "prod");
        e.setProperty("studiy.pagamento.simulacao", "true");
        e.setProperty("studiy.auth.expor-token-reset-dev", "true");
        assertThat(StartupGuard.verificar(e)).hasSize(2);
    }

    @Test
    void cookieSemSecureForaDeDevECorsComCuringaSaoBloqueados() {
        MockEnvironment e = env(JWT_FORTE, CHAVE_FORTE, "prod");
        e.setProperty("studiy.auth.cookie-secure", "false");
        e.setProperty("studiy.cors.origins", "https://*.studiy.app");
        assertThat(StartupGuard.verificar(e)).hasSize(2);
    }

    @Test
    void devEProdJuntosSaoBloqueados() {
        assertThat(StartupGuard.verificar(env(JWT_FORTE, CHAVE_FORTE, "dev", "prod"))).isNotEmpty();
    }

    /** A guarda roda de verdade na subida: o Spring aborta antes de criar qualquer bean (nem precisa de banco). */
    @Test
    void aplicacaoRealAbortaComSimulacaoForaDeDev() {
        assertThatThrownBy(() -> new SpringApplicationBuilder(StudiyBackendApplication.class)
                .web(WebApplicationType.NONE)
                .run("--spring.profiles.active=prod",
                        "--studiy.jwt.secret=" + JWT_FORTE,
                        "--studiy.crypto.key=" + CHAVE_FORTE,
                        "--studiy.cors.origins=https://studiy.app",
                        "--studiy.pagamento.simulacao=true"))
                .hasMessageContaining("StartupGuard");
    }
}
