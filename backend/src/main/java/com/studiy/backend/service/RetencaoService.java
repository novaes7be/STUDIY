package com.studiy.backend.service;

import com.studiy.backend.repository.IaChamadaRepository;
import com.studiy.backend.repository.SessaoRefreshRepository;
import com.studiy.backend.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Limpeza diária (minimização de dados, LGPD): apaga refresh tokens vencidos, zera tokens de
 * redefinição de senha e de verificação expirados e remove o log de IA mais antigo que a retenção.
 * Com várias instâncias, rodar em todas é seguro (as operações são idempotentes).
 */
@Service
public class RetencaoService {

    private static final Logger log = LoggerFactory.getLogger(RetencaoService.class);

    private final SessaoRefreshRepository sessoes;
    private final UsuarioRepository usuarios;
    private final IaChamadaRepository iaChamadas;
    private final int diasLogIa;

    public RetencaoService(SessaoRefreshRepository sessoes, UsuarioRepository usuarios, IaChamadaRepository iaChamadas,
                           @Value("${studiy.retencao.ia-dias}") int diasLogIa) {
        this.sessoes = sessoes;
        this.usuarios = usuarios;
        this.iaChamadas = iaChamadas;
        this.diasLogIa = diasLogIa;
    }

    @Scheduled(cron = "${studiy.retencao.cron}")
    @Transactional
    public void limpar() {
        LocalDateTime agora = LocalDateTime.now();
        int refresh = sessoes.apagarExpiradas(agora);
        int reset = usuarios.limparTokensResetExpirados(agora);
        int verif = usuarios.limparTokensVerificacaoExpirados(agora);
        int ia = iaChamadas.apagarAnterioresA(agora.minusDays(diasLogIa));
        log.info("Retenção: {} refresh tokens, {} tokens de senha, {} tokens de verificação, {} registros de IA removidos.",
                refresh, reset, verif, ia);
    }
}
