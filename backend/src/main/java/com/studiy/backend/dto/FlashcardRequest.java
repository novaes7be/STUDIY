package com.studiy.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FlashcardRequest(
        @NotBlank @Size(max = 1000) String pergunta,
        @NotBlank @Size(max = 2000) String resposta
) {}
