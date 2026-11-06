package com.studiy.backend.dto;

import com.studiy.backend.model.Plano;
import com.studiy.backend.model.Usuario;

/** Dados exibíveis da conta. O token NÃO vem aqui: a sessão vive em cookies HttpOnly. */
public record AuthResponse(String nome, String email, Plano plano) {
    public static AuthResponse de(Usuario u) {
        return new AuthResponse(u.getNome(), u.getEmail(), u.planoEfetivo());
    }
}
