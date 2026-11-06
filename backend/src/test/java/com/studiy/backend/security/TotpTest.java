package com.studiy.backend.security;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class TotpTest {

    // RFC 6238, Apêndice B: segredo ASCII "12345678901234567890", SHA1, 8 dígitos → usamos os 6 finais.
    private static final String SEGREDO_RFC = Totp.base32("12345678901234567890".getBytes(StandardCharsets.US_ASCII));

    @Test
    void bateComOsVetoresDaRfc6238() {
        assertThat(Totp.codigo(SEGREDO_RFC, 59 / 30)).isEqualTo("287082");          // 94287082
        assertThat(Totp.codigo(SEGREDO_RFC, 1111111109L / 30)).isEqualTo("081804"); // 07081804
        assertThat(Totp.codigo(SEGREDO_RFC, 1234567890L / 30)).isEqualTo("005924"); // 89005924
        assertThat(Totp.codigo(SEGREDO_RFC, 2000000000L / 30)).isEqualTo("279037"); // 69279037
    }

    @Test
    void aceitaUmPassoDeToleranciaERecusaOResto() {
        String s = Totp.novoSegredo();
        Instant agora = Instant.ofEpochSecond(1_800_000_000L);
        long passo = Totp.passoAtual(agora);
        assertThat(Totp.verificar(s, Totp.codigo(s, passo), agora)).isEqualTo(passo);
        assertThat(Totp.verificar(s, Totp.codigo(s, passo - 1), agora)).isEqualTo(passo - 1);
        assertThat(Totp.verificar(s, Totp.codigo(s, passo - 2), agora)).isEqualTo(-1);
        assertThat(Totp.verificar(s, "12345", agora)).isEqualTo(-1);
        assertThat(Totp.verificar(s, null, agora)).isEqualTo(-1);
    }

    @Test
    void base32IdaEVolta() {
        byte[] b = {1, 2, 3, (byte) 250, 99, 0, 17};
        assertThat(Totp.debase32(Totp.base32(b))).isEqualTo(b);
    }
}
