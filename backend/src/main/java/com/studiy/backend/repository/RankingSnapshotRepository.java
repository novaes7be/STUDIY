package com.studiy.backend.repository;

import com.studiy.backend.model.RankingSnapshot;
import com.studiy.backend.model.TipoRanking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface RankingSnapshotRepository extends JpaRepository<RankingSnapshot, Long> {

    List<RankingSnapshot> findTop50ByTipoOrderByPosicaoAscApelidoAsc(TipoRanking tipo);

    Optional<RankingSnapshot> findFirstByTipoAndUsuarioId(TipoRanking tipo, Long usuarioId);

    Optional<RankingSnapshot> findFirstByOrderByGeradoEmDesc();

    /** Só um servidor recalcula por vez (lock liberado no fim da transação). */
    @Query(value = "select pg_try_advisory_xact_lock(724501)", nativeQuery = true)
    boolean tentarTravar();

    @Modifying
    @Query(value = "delete from ranking_snapshot", nativeQuery = true)
    void limpar();

    /* Participam só contas com apelido e e-mail verificado; empates dividem a posição (1, 2, 2, 4). */

    @Modifying
    @Query(value = """
            insert into ranking_snapshot (tipo, posicao, usuario_id, apelido, valor, gerado_em)
            select 'SEMANA', rank() over (order by r.total desc), u.id, u.apelido, r.total, :agora
            from usuarios u
            join (select usuario_id, count(*) as total from revisoes_log
                  where criado_em >= :desde group by usuario_id) r on r.usuario_id = u.id
            where u.apelido is not null and u.email_verificado""", nativeQuery = true)
    int gerarSemana(@Param("desde") LocalDateTime desde, @Param("agora") LocalDateTime agora);

    /** Sequência que já quebrou (última atividade antes de ontem) vale 0 e não entra. */
    @Modifying
    @Query(value = """
            insert into ranking_snapshot (tipo, posicao, usuario_id, apelido, valor, gerado_em)
            select 'SEQUENCIA', rank() over (order by u.streak_atual desc), u.id, u.apelido, u.streak_atual, :agora
            from usuarios u
            where u.apelido is not null and u.email_verificado
              and u.streak_atual > 0 and u.ultima_atividade >= :ontem""", nativeQuery = true)
    int gerarSequencia(@Param("ontem") LocalDate ontem, @Param("agora") LocalDateTime agora);

    @Modifying
    @Query(value = """
            insert into ranking_snapshot (tipo, posicao, usuario_id, apelido, valor, gerado_em)
            select 'XP', rank() over (order by u.xp desc), u.id, u.apelido, u.xp, :agora
            from usuarios u
            where u.apelido is not null and u.email_verificado and u.xp > 0""", nativeQuery = true)
    int gerarXp(@Param("agora") LocalDateTime agora);
}
