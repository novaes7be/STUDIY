package com.studiy.backend.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Size(max = 100) String nome,
        @Email @NotBlank @Size(max = 254) String email,
        @NotBlank @Size(min = 8, max = 72, message = "A senha deve ter entre 8 e 72 caracteres") String senha,
        @AssertTrue(message = "É preciso aceitar os Termos e a Política de Privacidade") boolean aceitoTermos
) {}
