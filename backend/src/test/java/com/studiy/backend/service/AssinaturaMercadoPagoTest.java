package com.studiy.backend.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AssinaturaMercadoPagoTest {

    private static final String SEGREDO = "segredo-de-teste";

    private String cabecalho(String dataId, String requestId, String ts) {
        String v1 = AssinaturaMercadoPago.assinar(SEGREDO, "id:" + dataId.toLowerCase() + ";request-id:" + requestId + ";ts:" + ts + ";");
        return "ts=" + ts + ",v1=" + v1;
    }

    @Test
    void aceitaAssinaturaCorreta() {
        assertTrue(AssinaturaMercadoPago.valida(SEGREDO, "123456", cabecalho("123456", "req-1", "1700000000"), "req-1"));
    }

    @Test
    void rejeitaSegredoErradoOuDadosAdulterados() {
        String h = cabecalho("123456", "req-1", "1700000000");
        assertFalse(AssinaturaMercadoPago.valida("outro-segredo", "123456", h, "req-1"));
        assertFalse(AssinaturaMercadoPago.valida(SEGREDO, "999999", h, "req-1"));   // outro id de pagamento
        assertFalse(AssinaturaMercadoPago.valida(SEGREDO, "123456", h, "req-2"));   // outro request-id
    }

    @Test
    void rejeitaCabecalhosAusentesOuMalformados() {
        assertFalse(AssinaturaMercadoPago.valida(SEGREDO, "1", null, "r"));
        assertFalse(AssinaturaMercadoPago.valida(SEGREDO, "1", "lixo", "r"));
        assertFalse(AssinaturaMercadoPago.valida(SEGREDO, null, "ts=1,v1=abc", "r"));
        assertFalse(AssinaturaMercadoPago.valida("", "1", "ts=1,v1=abc", "r")); // sem segredo configurado = tudo recusado
    }
}
