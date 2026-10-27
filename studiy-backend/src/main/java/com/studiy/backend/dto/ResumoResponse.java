package com.studiy.backend.dto;

import java.time.LocalDateTime;

public record ResumoResponse(
        Long id,
        String conteudo,
        LocalDateTime criadoEm
) {}
