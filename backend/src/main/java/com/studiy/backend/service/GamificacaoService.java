package com.studiy.backend.service;

import com.studiy.backend.dto.GamificacaoResponse;
import com.studiy.backend.model.Usuario;
import com.studiy.backend.repository.UsuarioRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/**
 * Regras simples de gamificação: XP por ação de estudo, streak diário e
 * nível calculado a partir do XP acumulado.
 */
@Service
public class GamificacaoService {

    /** XP de cada ação de estudo. Soma SEMPRE (sem limite diário); conta nova começa em 0. */
    public static final int XP_POR_REVISAO = 5;
    public static final int XP_POR_GERACAO = 15;   // resumo, flashcards ou simulado
    public static final int XP_POR_ATIVIDADE = 10; // tarefa da matéria concluída
    private static final int XP_POR_NIVEL = 100;

    private final UsuarioRepository usuarioRepository;

    public GamificacaoService(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    /** Ação de estudo: mantém o streak e soma o XP dela. */
    public void registrarEstudo(Usuario usuario, int xp) {
        atualizarStreak(usuario);
        usuario.setXp(usuario.getXp() + xp);
        usuario.setNivel((usuario.getXp() / XP_POR_NIVEL) + 1);
        usuarioRepository.save(usuario);
    }

    /** Login: só mantém o streak vivo, não dá XP. */
    public void registrarLogin(Usuario usuario) {
        atualizarStreak(usuario);
        usuarioRepository.save(usuario);
    }

    private void atualizarStreak(Usuario usuario) {
        LocalDate hoje = LocalDate.now();
        LocalDate ultima = usuario.getUltimaAtividade();
        if (ultima == null || ultima.isBefore(hoje.minusDays(1))) {
            usuario.setStreakAtual(1); // quebrou a sequência (ou é a primeira vez)
        } else if (ultima.equals(hoje.minusDays(1))) {
            usuario.setStreakAtual(usuario.getStreakAtual() + 1); // ontem -> hoje
        }
        usuario.setUltimaAtividade(hoje); // se já era hoje, o streak não muda
    }

    public GamificacaoResponse status(Usuario usuario) {
        int xpNoNivel = usuario.getXp() % XP_POR_NIVEL;
        int faltam = XP_POR_NIVEL - xpNoNivel;
        int percentual = xpNoNivel * 100 / XP_POR_NIVEL;
        return new GamificacaoResponse(usuario.getXp(), usuario.getNivel(), usuario.getStreakAtual(),
                xpNoNivel, faltam, percentual);
    }
}
