package com.studiy.backend.dto;

public record MateriaResponse(
        Long id,
        String nome,
        String cor,
        Integer progressoAtual,
        Integer progressoTotal,
        long flashcardsPendentes
) {}
