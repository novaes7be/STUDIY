package com.studiy.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Requisições de edição de perfil/conta. */
public final class PerfilDtos {

    private PerfilDtos() {}

    public record NomeRequest(@NotBlank @Size(max = 100) String nome) {}

    public record EmailRequest(
            @Email @NotBlank @Size(max = 254) String novoEmail,
            @NotBlank @Size(max = 200) String senha) {}

    public record SenhaRequest(
            @NotBlank @Size(max = 200) String senhaAtual,
            @NotBlank @Size(min = 8, max = 72, message = "A nova senha deve ter entre 8 e 72 caracteres") String novaSenha) {}

    /** avatar = data URL (png/jpeg/webp) ou vazio/null para remover a foto. */
    public record AvatarRequest(@Size(max = 200000) String avatar) {}
}
