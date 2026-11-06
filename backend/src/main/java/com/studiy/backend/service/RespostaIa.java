package com.studiy.backend.service;

/** Texto gerado + uso de tokens informado pela API (campo usage da resposta). */
public record RespostaIa(
        String texto,
        String modelo,
        int tokensEntrada,
        int tokensSaida,
        int tokensCacheEscrita,
        int tokensCacheLeitura,
        String motivoParada
) {
    static RespostaIa mock(String texto) {
        return new RespostaIa(texto, "mock", 0, 0, 0, 0, "mock");
    }
}
