package com.studiy.backend.service;

import com.studiy.backend.dto.AdminDtos;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Visão geral do painel admin: só agregados (contagens, somas, médias), nunca conteúdo de estudo.
 * Consultas nativas com count/sum direto no banco, para não carregar entidades na memória.
 * Todas as datas usam o relógio do servidor (o mesmo que grava criado_em).
 */
@Service
@Transactional(readOnly = true)
public class AdminMetricasService {

    private final EntityManager em;

    public AdminMetricasService(EntityManager em) {
        this.em = em;
    }

    public AdminDtos.Metricas metricas(int dias) {
        int d = Math.min(Math.max(dias, 1), 365);
        LocalDate inicio = LocalDate.now().minusDays(d - 1L);
        LocalDateTime desde = inicio.atStartOfDay();
        return new AdminDtos.Metricas(d, LocalDateTime.now(), usuarios(desde, inicio), vendas(desde, inicio),
                conteudo(desde, inicio), ia(desde, inicio));
    }

    private AdminDtos.MetricasUsuarios usuarios(LocalDateTime desde, LocalDate inicio) {
        LocalDate hoje = LocalDate.now();
        return new AdminDtos.MetricasUsuarios(
                numero("select count(*) from usuarios", null),
                numero("select count(*) from usuarios where criado_em >= :desde", desde),
                numero("select count(*) from usuarios where email_verificado", null),
                // mesma regra de Usuario.planoEfetivo(): PREMIUM com expiração no futuro
                numero("select count(*) from usuarios where plano = 'PREMIUM' and plano_expira_em > now()", null),
                numero("select count(*) from usuarios where ultima_atividade >= :desde", hoje.minusDays(6)),
                numero("select count(*) from usuarios where ultima_atividade >= :desde", hoje.minusDays(29)),
                serie("select cast(criado_em as date), count(*) from usuarios where criado_em >= :desde group by 1", desde, inicio));
    }

    private AdminDtos.MetricasVendas vendas(LocalDateTime desde, LocalDate inicio) {
        long receitaPeriodo = numero("select coalesce(sum(valor_centavos), 0) from pedidos where status = 'PAGO' and pago_em >= :desde", desde);
        long receitaTotal = numero("select coalesce(sum(valor_centavos), 0) from pedidos where status = 'PAGO'", null);
        Map<String, Long> porStatus = mapa("select status, count(*) from pedidos where criado_em >= :desde group by 1 order by 1", desde);
        long pagos = numero("select count(*) from pedidos where status = 'PAGO' and pago_em >= :desde", desde);
        return new AdminDtos.MetricasVendas(
                PagamentoService.formatarBrl((int) receitaPeriodo),
                PagamentoService.formatarBrl((int) receitaTotal),
                pagos == 0 ? null : PagamentoService.formatarBrl((int) (receitaPeriodo / pagos)),
                pagos,
                porStatus.getOrDefault("REEMBOLSADO", 0L),
                taxaConversao(porStatus),
                mapa("select metodo, count(*) from pedidos where status = 'PAGO' and pago_em >= :desde group by 1 order by 1", desde),
                porStatus,
                serie("select cast(pago_em as date), sum(valor_centavos) from pedidos where status = 'PAGO' and pago_em >= :desde group by 1", desde, inicio));
    }

    /**
     * Fração dos pedidos criados no período que viraram venda (0..1), a partir da contagem por status
     * (chaves PENDENTE, PAGO, REJEITADO, REEMBOLSADO; status sem pedido não aparecem no mapa).
     * Devolve null quando não há base para calcular.
     */
    static Double taxaConversao(Map<String, Long> porStatus) {
        // Reembolsado também converteu (o reembolso tem métrica própria); o denominador é todo pedido criado,
        // inclusive pendente: checkout abandonado é justamente o que a conversão deve mostrar.
        long total = porStatus.values().stream().mapToLong(Long::longValue).sum();
        if (total == 0) return null;
        long vendidos = porStatus.getOrDefault("PAGO", 0L) + porStatus.getOrDefault("REEMBOLSADO", 0L);
        return (double) vendidos / total;
    }

    private AdminDtos.MetricasConteudo conteudo(LocalDateTime desde, LocalDate inicio) {
        Object media = em.createNativeQuery("select avg(nota) from simulados where data_realizacao >= :desde")
                .setParameter("desde", inicio).getSingleResult();
        return new AdminDtos.MetricasConteudo(
                contagem("resumos", "criado_em", desde),
                contagem("flashcards", "criado_em", desde),
                contagem("simulados", "data_realizacao", inicio),
                contagem("pdfs", "criado_em", desde),
                numero("select count(*) from materias", null),
                numero("select count(*) from revisoes_log where criado_em >= :desde", desde),
                media == null ? null : Math.round(((Number) media).doubleValue() * 10) / 10.0,
                serie("select cast(criado_em as date), count(*) from resumos where criado_em >= :desde group by 1", desde, inicio),
                serie("select data_realizacao, count(*) from simulados where data_realizacao >= :desde group by 1", inicio, inicio),
                serie("select cast(criado_em as date), count(*) from flashcards where criado_em >= :desde group by 1", desde, inicio));
    }

    private AdminDtos.MetricasIa ia(LocalDateTime desde, LocalDate inicio) {
        return new AdminDtos.MetricasIa(
                AdminService.usd(numero("select coalesce(sum(custo_micro_usd), 0) from ia_chamadas where criado_em >= :desde", desde)),
                numero("select count(*) from ia_chamadas where criado_em >= :desde", desde),
                numero("select count(*) from ia_chamadas where not sucesso and criado_em >= :desde", desde),
                mapa("select tipo, count(*) from ia_chamadas where criado_em >= :desde group by 1 order by 1", desde),
                serie("select cast(criado_em as date), count(*) from ia_chamadas where criado_em >= :desde group by 1", desde, inicio));
    }

    // ---- utilitários de consulta (SQL fixo no código; só :desde vem de fora, como parâmetro) ----

    private AdminDtos.Contagem contagem(String tabela, String coluna, Object desde) {
        return new AdminDtos.Contagem(numero("select count(*) from " + tabela, null),
                numero("select count(*) from " + tabela + " where " + coluna + " >= :desde", desde));
    }

    private long numero(String sql, Object desde) {
        Object r = consulta(sql, desde).getSingleResult();
        return r == null ? 0 : ((Number) r).longValue();
    }

    private Map<String, Long> mapa(String sql, Object desde) {
        Map<String, Long> m = new LinkedHashMap<>();
        for (Object o : consulta(sql, desde).getResultList()) {
            Object[] r = (Object[]) o;
            m.put(String.valueOf(r[0]), ((Number) r[1]).longValue());
        }
        return m;
    }

    /** Série diária de [dia, valor] completada com zero em todos os dias de inicio até hoje. */
    private List<AdminDtos.Ponto> serie(String sql, Object desde, LocalDate inicio) {
        Map<LocalDate, Long> porDia = new TreeMap<>();
        for (LocalDate dia = inicio; !dia.isAfter(LocalDate.now()); dia = dia.plusDays(1)) porDia.put(dia, 0L);
        for (Object o : consulta(sql, desde).getResultList()) {
            Object[] r = (Object[]) o;
            LocalDate dia = r[0] instanceof java.sql.Date sd ? sd.toLocalDate() : (LocalDate) r[0];
            porDia.computeIfPresent(dia, (k, v) -> v + ((Number) r[1]).longValue());
        }
        List<AdminDtos.Ponto> pontos = new ArrayList<>(porDia.size());
        porDia.forEach((dia, v) -> pontos.add(new AdminDtos.Ponto(dia, v)));
        return pontos;
    }

    private Query consulta(String sql, Object desde) {
        Query q = em.createNativeQuery(sql);
        if (desde != null) q.setParameter("desde", desde);
        return q;
    }
}
