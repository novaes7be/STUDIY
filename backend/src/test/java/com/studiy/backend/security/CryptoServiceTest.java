package com.studiy.backend.security;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class CryptoServiceTest {

    private final CryptoService crypto = new CryptoService(Base64.getEncoder().encodeToString(new byte[32]));

    @Test
    void cifraEDecifraSemPerderConteudo() {
        String original = "Resumo de Biologia: alelo recessivo ção ñ 😀";
        String cifrado = crypto.cifrar(original);
        assertTrue(cifrado.startsWith("enc:v1:"));
        assertFalse(cifrado.contains("Biologia"));
        assertEquals(original, crypto.decifrar(cifrado));
    }

    @Test
    void cifraEDecifraTextoGrandeEVazio() {
        String grande = "Fotossíntese converte luz em energia. ".repeat(100_000);   // ~3,7 MB, como um PDF longo
        assertEquals(grande, crypto.decifrar(crypto.cifrar(grande)));
        assertEquals("", crypto.decifrar(crypto.cifrar("")));
    }

    @Test
    void cadaCifragemUsaIvDiferente() {
        assertNotEquals(crypto.cifrar("igual"), crypto.cifrar("igual"));
    }

    @Test
    void dadoAntigoSemPrefixoContinuaLegivel() {
        assertEquals("texto antigo", crypto.decifrar("texto antigo"));
    }

    @Test
    void chaveComTamanhoErradoEhRejeitada() {
        assertThrows(IllegalStateException.class, () -> new CryptoService(Base64.getEncoder().encodeToString(new byte[16])));
    }

    @Test
    void adulteracaoDoDadoEhDetectada() {
        String cifrado = crypto.cifrar("conteudo");
        String adulterado = cifrado.substring(0, cifrado.length() - 4) + "AAAA";
        assertThrows(IllegalStateException.class, () -> crypto.decifrar(adulterado));
    }
}
