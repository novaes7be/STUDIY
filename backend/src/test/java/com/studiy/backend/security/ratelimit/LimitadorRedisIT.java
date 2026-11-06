package com.studiy.backend.security.ratelimit;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/** Duas "instâncias" da API apontando para o mesmo Redis dividem o mesmo limite. */
@Testcontainers
class LimitadorRedisIT {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @Test
    void limiteEhCompartilhadoEntreInstancias() {
        String url = "redis://" + redis.getHost() + ":" + redis.getMappedPort(6379);
        try (LimitadorRedis instanciaA = new LimitadorRedis(url); LimitadorRedis instanciaB = new LimitadorRedis(url)) {
            assertThat(instanciaA.consumir("ia:u:ana", 3)).isTrue();
            assertThat(instanciaB.consumir("ia:u:ana", 3)).isTrue();
            assertThat(instanciaA.consumir("ia:u:ana", 3)).isTrue();
            assertThat(instanciaB.consumir("ia:u:ana", 3)).isFalse();
            assertThat(instanciaB.consumir("ia:u:beto", 3)).isTrue();
        }
    }
}
