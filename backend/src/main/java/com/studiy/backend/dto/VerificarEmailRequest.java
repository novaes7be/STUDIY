package com.studiy.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record VerificarEmailRequest(@NotBlank @Size(max = 100) String token) {}
