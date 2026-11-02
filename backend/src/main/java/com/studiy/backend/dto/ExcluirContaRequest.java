package com.studiy.backend.dto;

import jakarta.validation.constraints.NotBlank;

public record ExcluirContaRequest(@NotBlank String senha) {}
