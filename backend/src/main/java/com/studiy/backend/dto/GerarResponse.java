package com.studiy.backend.dto;

public record GerarResponse(
        String conteudo,
        /** true: o mesmo conteúdo já tinha sido gerado; não chamou a IA nem gastou o limite. */
        boolean reaproveitado
) {}
