package com.studiy.backend.service;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class BloqueioLoginServiceTest {

    private final BloqueioLoginService s = new BloqueioLoginService(null, 5, 60, 60);

    @Test
    void esperaCresceExponencialmenteAteOTeto() {
        assertThat(s.espera(4)).isZero();
        assertThat(s.espera(5)).isEqualTo(Duration.ofMinutes(1));
        assertThat(s.espera(6)).isEqualTo(Duration.ofMinutes(2));
        assertThat(s.espera(7)).isEqualTo(Duration.ofMinutes(4));
        assertThat(s.espera(50)).isEqualTo(Duration.ofMinutes(60));
    }
}
