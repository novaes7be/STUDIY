package com.studiy.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record AtividadeRequest(
        @NotBlank @Size(max = 255) String titulo,
        LocalDate dataAtividade,
        @Size(max = 50) String tag,
        @Size(max = 20) String tagTipo
) {}
