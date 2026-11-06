package com.studiy.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CodigoMfaRequest(@NotBlank @Pattern(regexp = "\\d{6}", message = "Use os 6 dígitos do app autenticador") String codigo) {}
