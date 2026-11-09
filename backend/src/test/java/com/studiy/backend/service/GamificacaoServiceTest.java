package com.studiy.backend.service;

import com.studiy.backend.model.Usuario;
import com.studiy.backend.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class GamificacaoServiceTest {

    private final GamificacaoService servico = new GamificacaoService(mock(UsuarioRepository.class));

    @Test
    void contaNovaComecaEmZeroELoginNaoDaXp() {
        Usuario u = new Usuario();
        servico.registrarLogin(u);
        assertEquals(0, u.getXp());
        assertEquals(1, u.getStreakAtual());
    }

    @Test
    void xpSomaEmTodaAcaoDoMesmoDia() {
        Usuario u = new Usuario();
        servico.registrarLogin(u); // antes o login "gastava" o XP do dia
        servico.registrarEstudo(u, GamificacaoService.XP_POR_GERACAO);
        servico.registrarEstudo(u, GamificacaoService.XP_POR_GERACAO);
        for (int i = 0; i < 4; i++) servico.registrarEstudo(u, GamificacaoService.XP_POR_REVISAO);

        assertEquals(2 * GamificacaoService.XP_POR_GERACAO + 4 * GamificacaoService.XP_POR_REVISAO, u.getXp());
        assertEquals(1, u.getStreakAtual()); // várias ações no mesmo dia não inflam o streak
    }

    @Test
    void nivelSobeACada100Xp() {
        Usuario u = new Usuario();
        for (int i = 0; i < 7; i++) servico.registrarEstudo(u, GamificacaoService.XP_POR_GERACAO); // 105 XP
        assertEquals(2, u.getNivel());
    }
}
