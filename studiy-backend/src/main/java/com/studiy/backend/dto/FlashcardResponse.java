package com.studiy.backend.dto;

import java.time.LocalDate;

public record FlashcardResponse(
        Long id,
        String pergunta,
        String resposta,
        Integer repeticoes,
        LocalDate proximaRevisao
) {}
