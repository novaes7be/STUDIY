package com.studiy.backend.dto;

import com.studiy.backend.model.TipoGeracao;
import jakarta.validation.constraints.NotNull;

public record GerarRequest(
        @NotNull Long pdfId,
        @NotNull TipoGeracao tipo
) {}
