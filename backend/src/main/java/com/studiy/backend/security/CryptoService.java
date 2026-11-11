package com.studiy.backend.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Criptografia em repouso (AES-256-GCM) para conteúdo privado do aluno:
 * texto extraído dos PDFs, resumos e flashcards.
 * A chave vem de STUDIY_ENCRYPTION_KEY (Base64, 32 bytes) e nunca vai pro Git.
 * Valores sem o prefixo "enc:v1:" (dados antigos) são lidos como estão e
 * passam a ser cifrados na próxima gravação.
 */
@Service
public class CryptoService {

    static final String PREFIXO = "enc:v1:";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec chave;
    private final SecureRandom random = new SecureRandom();

    public CryptoService(@Value("${studiy.crypto.key}") String chaveBase64) {
        byte[] bytes = Base64.getDecoder().decode(chaveBase64.trim());
        if (bytes.length != 32) {
            throw new IllegalStateException("studiy.crypto.key deve ter exatamente 32 bytes em Base64 (use: openssl rand -base64 32)");
        }
        this.chave = new SecretKeySpec(bytes, "AES");
    }

    public String cifrar(String texto) {
        if (texto == null) return null;
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, chave, new GCMParameterSpec(TAG_BITS, iv));
            byte[] cifrado = cipher.doFinal(texto.getBytes(StandardCharsets.UTF_8));
            // Concatena sem somar tamanhos: o tamanho do texto vem do usuário e a soma poderia estourar o int.
            ByteArrayOutputStream saida = new ByteArrayOutputStream();
            saida.writeBytes(iv);
            saida.writeBytes(cifrado);
            return PREFIXO + Base64.getEncoder().encodeToString(saida.toByteArray());
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao cifrar dado", e);
        }
    }

    /**
     * Impressão digital de um texto: HMAC-SHA256 com chave derivada da chave do servidor.
     * Igual para o mesmo texto (serve para reaproveitar gerações), mas sem a chave não dá para
     * testar se um documento conhecido está no banco, como daria com um SHA-256 puro.
     */
    public String impressaoDigital(String texto) {
        try {
            Mac derivar = Mac.getInstance("HmacSHA256");
            derivar.init(new SecretKeySpec(chave.getEncoded(), "HmacSHA256"));
            byte[] chaveHmac = derivar.doFinal("studiy-impressao-digital-v1".getBytes(StandardCharsets.UTF_8));
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(chaveHmac, "HmacSHA256"));
            return java.util.HexFormat.of().formatHex(mac.doFinal(texto.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao calcular impressão digital", e);
        }
    }

    public String decifrar(String valor) {
        if (valor == null || !valor.startsWith(PREFIXO)) return valor;
        try {
            byte[] tudo = Base64.getDecoder().decode(valor.substring(PREFIXO.length()));
            ByteBuffer buf = ByteBuffer.wrap(tudo);
            byte[] iv = new byte[IV_BYTES];
            buf.get(iv);
            byte[] cifrado = new byte[buf.remaining()];
            buf.get(cifrado);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, chave, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(cifrado), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao decifrar dado (chave incorreta ou dado corrompido)", e);
        }
    }
}
