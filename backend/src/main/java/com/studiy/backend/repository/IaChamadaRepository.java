package com.studiy.backend.repository;

import com.studiy.backend.model.IaChamada;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface IaChamadaRepository extends JpaRepository<IaChamada, Long> {

    /** Por dia: [dia, chamadas, falhas, tokens entrada, tokens saída, custo micro-USD]. */
    @Query(value = """
            select cast(criado_em as date) as dia, count(*), sum(case when sucesso then 0 else 1 end),
                   sum(tokens_entrada), sum(tokens_saida), sum(custo_micro_usd)
            from ia_chamadas where criado_em >= :desde
            group by cast(criado_em as date) order by dia desc""", nativeQuery = true)
    List<Object[]> resumoPorDia(@Param("desde") LocalDateTime desde);

    /** Maiores consumidores: [e-mail, chamadas, custo micro-USD]. */
    @Query(value = """
            select u.email, count(*), sum(c.custo_micro_usd)
            from ia_chamadas c join usuarios u on u.id = c.usuario_id
            where c.criado_em >= :desde
            group by u.email order by sum(c.custo_micro_usd) desc limit 10""", nativeQuery = true)
    List<Object[]> maioresConsumidores(@Param("desde") LocalDateTime desde);

    @Modifying
    @Query("delete from IaChamada c where c.criadoEm < :limite")
    int apagarAnterioresA(@Param("limite") LocalDateTime limite);
}
