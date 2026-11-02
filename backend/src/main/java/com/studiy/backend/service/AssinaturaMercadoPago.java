package com.studiy.backend.service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Valida a assinatura (x-signature) dos webhooks do Mercado Pago.
 * Cabeçalho: "ts=1700000000,v1=<hmac>". Manifesto assinado:
 * "id:<data.id em minúsculas>;request-id:<x-request-id>;ts:<ts>;"  (HMAC-SHA256 com o segredo do webhook).
 */
public final class AssinaturaMercadoPago {

    private AssinaturaMercadoPago() {}

    public static String assinar(String segredo, String manifesto) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(segredo.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(manifesto.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static boolean valida(String segredo, String dataId, String xSignature, String xRequestId) {
        if (segredo == null || segredo.isBlank() || dataId == null || xSignature == null || xRequestId == null) {
            return false;
        }
        String ts = null;
        String v1 = null;
        for (String parte : xSignature.split(",")) {
            String[] kv = parte.trim().split("=", 2);
            if (kv.length == 2) {
                if (kv[0].equals("ts")) ts = kv[1];
                else if (kv[0].equals("v1")) v1 = kv[1];
            }
        }
        if (ts == null || v1 == null) return false;
        String manifesto = "id:" + dataId.toLowerCase(Locale.ROOT) + ";request-id:" + xRequestId + ";ts:" + ts + ";";
        String esperado = assinar(segredo, manifesto);
        // comparação em tempo constante
        return MessageDigest.isEqual(esperado.getBytes(StandardCharsets.UTF_8), v1.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
    }
}
