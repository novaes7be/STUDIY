package com.studiy.backend.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

public record AtividadeRequest(
        @NotBlank String titulo,
        LocalDate dataAtividade,
        String tag,
        String tagTipo
) {}
