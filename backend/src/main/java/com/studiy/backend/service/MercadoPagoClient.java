package com.studiy.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/** Cliente mínimo da API do Mercado Pago. O access token só existe aqui, no servidor. */
@Component
public class MercadoPagoClient {

    private static final Logger log = LoggerFactory.getLogger(MercadoPagoClient.class);

    private final RestClient rest;
    private final String token;

    public MercadoPagoClient(@Value("${studiy.pagamento.mp.access-token:}") String token) {
        this.token = token == null ? "" : token.trim();
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(5_000);
        fabrica.setReadTimeout(15_000);
        this.rest = RestClient.builder().baseUrl("https://api.mercadopago.com").requestFactory(fabrica).build();
    }

    public boolean configurado() {
        return !token.isBlank();
    }

    /** Credenciais de teste do Mercado Pago começam com TEST-. */
    public boolean sandbox() {
        return token.startsWith("TEST-");
    }

    public JsonNode criarPreferencia(Map<String, Object> corpo) {
        try {
            return rest.post().uri("/checkout/preferences")
                    .header("Authorization", "Bearer " + token)
                    .header("content-type", "application/json")
                    .body(corpo)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientException e) {
            log.error("Falha ao criar preferência no Mercado Pago: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Não foi possível iniciar o pagamento agora. Tente novamente.");
        }
    }

    /** paymentId já deve ter sido validado como numérico pelo chamador. */
    public JsonNode buscarPagamento(String paymentId) {
        try {
            return rest.get().uri("/v1/payments/{id}", paymentId)
                    .header("Authorization", "Bearer " + token)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientException e) {
            log.error("Falha ao consultar pagamento {} no Mercado Pago: {}", paymentId, e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Falha ao consultar o pagamento.");
        }
    }
}
