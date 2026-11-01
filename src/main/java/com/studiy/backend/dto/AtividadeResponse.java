package com.studiy.backend.dto;

import java.time.LocalDate;

public record AtividadeResponse(
        Long id,
        String titulo,
        LocalDate dataAtividade,
        String tag,
        String tagTipo,
        boolean concluida
) {}
