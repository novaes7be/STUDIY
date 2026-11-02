package com.studiy.backend.dto;

import java.time.LocalDateTime;

public record PdfResponse(Long id, String nomeArquivo, LocalDateTime criadoEm) {}
