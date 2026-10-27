package com.studiy.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** cor = chave da paleta (azul, roxo, vermelho, grafite), não um valor CSS livre. */
public record MateriaRequest(
        @NotBlank @Size(max = 100) String nome,
        @Pattern(regexp = "azul|roxo|vermelho|grafite", message = "Cor inválida") String cor
) {}
