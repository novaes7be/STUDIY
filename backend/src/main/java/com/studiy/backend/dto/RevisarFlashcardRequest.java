package com.studiy.backend.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** qualidade segue a escala do SM-2: 0 (errou feio) a 5 (acertou na hora). */
public record RevisarFlashcardRequest(
        @NotNull @Min(0) @Max(5) Integer qualidade
) {}
