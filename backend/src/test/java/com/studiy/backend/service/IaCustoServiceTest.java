package com.studiy.backend.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class IaCustoServiceTest {

    private final IaCustoService s = new IaCustoService(null,
            new BigDecimal("0.10"), new BigDecimal("0.50"), new BigDecimal("0.125"), new BigDecimal("0.01"));

    @Test
    void custoEmMicroDolaresSegueOPrecoPorMilhaoDeTokens() {
        // 3000 entrada × 0,10 + 1000 saída × 0,50 = 300 + 500 micro-US$
        assertThat(s.custoMicroUsd(new RespostaIa("x", "m", 3000, 1000, 0, 0, "end_turn"))).isEqualTo(800);
        // cache: 2000 × 0,125 + 10000 × 0,01 = 250 + 100
        assertThat(s.custoMicroUsd(new RespostaIa("x", "m", 0, 0, 2000, 10000, "end_turn"))).isEqualTo(350);
        assertThat(s.custoMicroUsd(RespostaIa.mock("x"))).isZero();
    }
}
