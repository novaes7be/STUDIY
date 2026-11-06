package com.studiy.backend.service;

import com.studiy.backend.dto.GamificacaoResponse;
import com.studiy.backend.model.Usuario;
import com.studiy.backend.repository.UsuarioRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/**
 * Regras simples de gamificação: XP por ação concluída, streak diário e
 * nível calculado a partir do XP acumulado.
 */
@Service
public class GamificacaoService {

    private static final int XP_POR_ACAO = 10;
    private static final int XP_POR_NIVEL = 100;

    private final UsuarioRepository usuarioRepository;

    public GamificacaoService(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    /** Chamado sempre que o usuário faz algo que conta como "estudou hoje". */
    public void registrarAtividade(Usuario usuario) {
        LocalDate hoje = LocalDate.now();
        LocalDate ultima = usuario.getUltimaAtividade();

        if (ultima == null || ultima.isBefore(hoje.minusDays(1))) {
            usuario.setStreakAtual(1); // quebrou a sequência (ou é a primeira vez)
        } else if (ultima.equals(hoje.minusDays(1))) {
            usuario.setStreakAtual(usuario.getStreakAtual() + 1); // continuou ontem -> hoje
        }
        // se ultima == hoje, streak não muda (já contou hoje)

        if (ultima == null || !ultima.equals(hoje)) {
            usuario.setXp(usuario.getXp() + XP_POR_ACAO);
            usuario.setNivel((usuario.getXp() / XP_POR_NIVEL) + 1);
        }

        usuario.setUltimaAtividade(hoje);
        usuarioRepository.save(usuario);
    }

    public GamificacaoResponse status(Usuario usuario) {
        int xpNoNivel = usuario.getXp() % XP_POR_NIVEL;
        int faltam = XP_POR_NIVEL - xpNoNivel;
        int percentual = xpNoNivel * 100 / XP_POR_NIVEL;
        return new GamificacaoResponse(usuario.getXp(), usuario.getNivel(), usuario.getStreakAtual(),
                xpNoNivel, faltam, percentual);
    }
}
