package com.studiy.backend.security;

import com.studiy.backend.model.Usuario;
import com.studiy.backend.repository.UsuarioRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
public class UsuarioAtual {

    private final UsuarioRepository usuarioRepository;

    public UsuarioAtual(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    /** Resolve o Usuario autenticado a partir do e-mail guardado no token JWT. */
    public Usuario resolver(Authentication authentication) {
        String email = authentication.getName();
        return usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Usuário autenticado não encontrado no banco"));
    }
}
