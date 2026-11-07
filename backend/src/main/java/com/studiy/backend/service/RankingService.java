package com.studiy.backend.service;

import com.studiy.backend.dto.RankingResponse;
import com.studiy.backend.model.RankingSnapshot;
import com.studiy.backend.model.TipoRanking;
import com.studiy.backend.model.Usuario;
import com.studiy.backend.repository.RankingSnapshotRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Recalcula a "foto" do ranking em segundo plano. Apagar e regravar acontecem na MESMA
 * transação: quem lê durante o cálculo continua vendo a foto anterior inteira (MVCC do
 * Postgres) e passa a ver a nova de uma vez no commit. Nada é calculado na requisição.
 */
@Service
public class RankingService {

    private static final Logger log = LoggerFactory.getLogger(RankingService.class);

    private final RankingSnapshotRepository snapshots;

    public RankingService(RankingSnapshotRepository snapshots) {
        this.snapshots = snapshots;
    }

    /** Lê a foto pronta: top 50 do quadro + a linha de quem pergunta (mesmo fora do top 50). */
    @Transactional(readOnly = true)
    public RankingResponse ler(Usuario u, TipoRanking tipo) {
        List<RankingResponse.Item> itens = snapshots.findTop50ByTipoOrderByPosicaoAscApelidoAsc(tipo).stream()
                .map(r -> item(r, u)).toList();
        RankingResponse.Item voce = snapshots.findFirstByTipoAndUsuarioId(tipo, u.getId()).map(r -> item(r, u)).orElse(null);
        LocalDateTime geradoEm = snapshots.findFirstByOrderByGeradoEmDesc().map(RankingSnapshot::getGeradoEm).orElse(null);
        return new RankingResponse(tipo.name(), titulo(tipo), unidade(tipo), geradoEm, u.getApelido() != null, itens, voce);
    }

    private static RankingResponse.Item item(RankingSnapshot r, Usuario u) {
        return new RankingResponse.Item(r.getPosicao(), r.getApelido(), r.getValor(), r.getUsuarioId().equals(u.getId()));
    }

    private static String titulo(TipoRanking t) {
        return switch (t) {
            case SEMANA -> "Revisões nos últimos 7 dias";
            case SEQUENCIA -> "Dias seguidos estudando";
            case XP -> "XP total";
        };
    }

    private static String unidade(TipoRanking t) {
        return switch (t) {
            case SEMANA -> "revisões";
            case SEQUENCIA -> "dias";
            case XP -> "XP";
        };
    }

    @Scheduled(fixedDelayString = "${studiy.ranking.intervalo-ms}", initialDelayString = "${studiy.ranking.atraso-inicial-ms}")
    @Transactional
    public void atualizar() {
        if (!snapshots.tentarTravar()) {
            log.debug("Ranking já está sendo recalculado por outro servidor.");
            return;
        }
        LocalDateTime agora = LocalDateTime.now();
        snapshots.limpar();
        int semana = snapshots.gerarSemana(agora.minusDays(7), agora);
        int sequencia = snapshots.gerarSequencia(LocalDate.now().minusDays(1), agora);
        int xp = snapshots.gerarXp(agora);
        log.info("Ranking recalculado: {} na semana, {} em sequência, {} em XP.", semana, sequencia, xp);
    }
}
