package com.studiy.backend.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SegurancaServicosTest {

    @Test
    void tokenDeResetEhGuardadoComoHashDeterministico() {
        String h1 = AuthService.sha256("token-secreto");
        assertEquals(64, h1.length());
        assertEquals(h1, AuthService.sha256("token-secreto"));
        assertNotEquals(h1, AuthService.sha256("outro"));
        assertFalse(h1.contains("token-secreto"));
    }

    @Test
    void nomeDeArquivoEhSanitizado() {
        assertEquals("passwd.pdf", PdfService.nomeSeguro("../../etc/passwd.pdf"));
        assertEquals("a_b_.pdf", PdfService.nomeSeguro("a<b>.pdf").replace("__", "_"));
        assertEquals("documento.pdf", PdfService.nomeSeguro(null));
    }
}
