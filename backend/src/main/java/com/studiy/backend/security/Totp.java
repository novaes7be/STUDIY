package com.studiy.backend.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;

/**
 * TOTP (RFC 6238): HMAC-SHA1, passos de 30 s, 6 dígitos, tolerância de ±1 passo para relógios
 * desalinhados. Compatível com Google Authenticator, 1Password, Authy etc.
 */
public final class Totp {

    private static final String BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final int PASSO_SEGUNDOS = 30;
    private static final SecureRandom RANDOM = new SecureRandom();

    private Totp() {}

    /** 160 bits aleatórios em Base32 (o tamanho recomendado pela RFC 4226). */
    public static String novoSegredo() {
        byte[] b = new byte[20];
        RANDOM.nextBytes(b);
        return base32(b);
    }

    public static String uri(String emissor, String conta, String segredo) {
        return "otpauth://totp/" + url(emissor) + ":" + url(conta) + "?secret=" + segredo
                + "&issuer=" + url(emissor) + "&algorithm=SHA1&digits=6&period=" + PASSO_SEGUNDOS;
    }

    public static long passoAtual(Instant agora) {
        return agora.getEpochSecond() / PASSO_SEGUNDOS;
    }

    /** Devolve o passo que bateu com o código (para impedir reuso) ou -1. Comparação em tempo constante. */
    public static long verificar(String segredo, String codigo, Instant agora) {
        if (codigo == null || !codigo.matches("\\d{6}")) return -1;
        long atual = passoAtual(agora);
        long achado = -1;
        for (long p = atual - 1; p <= atual + 1; p++) {
            if (MessageDigest.isEqual(codigo(segredo, p).getBytes(), codigo.getBytes())) achado = p;
        }
        return achado;
    }

    /** Código de 6 dígitos de um passo (público para testes e ferramentas; não expõe o segredo). */
    public static String codigo(String segredoBase32, long passo) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(debase32(segredoBase32), "HmacSHA1"));
            byte[] h = mac.doFinal(ByteBuffer.allocate(8).putLong(passo).array());
            int o = h[h.length - 1] & 0x0f;
            int bin = ((h[o] & 0x7f) << 24) | ((h[o + 1] & 0xff) << 16) | ((h[o + 2] & 0xff) << 8) | (h[o + 3] & 0xff);
            return String.format("%06d", bin % 1_000_000);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static String base32(byte[] dados) {
        StringBuilder sb = new StringBuilder();
        int buffer = 0, bits = 0;
        for (byte b : dados) {
            buffer = (buffer << 8) | (b & 0xff);
            bits += 8;
            while (bits >= 5) {
                sb.append(BASE32.charAt((buffer >> (bits - 5)) & 31));
                bits -= 5;
            }
        }
        if (bits > 0) sb.append(BASE32.charAt((buffer << (5 - bits)) & 31));
        return sb.toString();
    }

    static byte[] debase32(String s) {
        String limpo = s.replace("=", "").replace(" ", "").toUpperCase();
        ByteBuffer out = ByteBuffer.allocate(limpo.length() * 5 / 8);
        int buffer = 0, bits = 0;
        for (char c : limpo.toCharArray()) {
            int v = BASE32.indexOf(c);
            if (v < 0) throw new IllegalArgumentException("Base32 inválido");
            buffer = (buffer << 5) | v;
            bits += 5;
            if (bits >= 8) {
                out.put((byte) (buffer >> (bits - 8)));
                bits -= 8;
            }
        }
        return out.array();
    }

    private static String url(String s) {
        return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
    }
}
