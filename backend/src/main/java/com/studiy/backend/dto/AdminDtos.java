package com.studiy.backend.dto;

import com.studiy.backend.model.MetodoPagamento;
import com.studiy.backend.model.Papel;
import com.studiy.backend.model.Plano;
import com.studiy.backend.model.StatusPedido;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** Respostas do painel admin: só metadados (nunca conteúdo de estudo, senha, tokens ou segredo MFA). */
public final class AdminDtos {

    private AdminDtos() {}

    public record Pagina<T>(List<T> itens, int pagina, int totalPaginas, long total) {}

    public record UsuarioItem(Long id, String nome, String email, Papel papel, Plano plano, boolean emailVerificado,
                              boolean mfaAtivo, boolean bloqueado, LocalDateTime criadoEm) {}

    public record PedidoItem(String id, String usuarioEmail, MetodoPagamento metodo, StatusPedido status,
                             String valor, LocalDateTime criadoEm, LocalDateTime pagoEm) {}

    public record UsoIaDia(LocalDate dia, long chamadas, long falhas, long tokensEntrada, long tokensSaida, String custo) {}

    public record UsoIaConsumidor(String email, long chamadas, String custo) {}

    public record UsoIa(int dias, String custoTotal, long chamadasTotal, List<UsoIaDia> porDia, List<UsoIaConsumidor> maioresConsumidores) {}

    // ---- Métricas (visão geral) ----

    /** Ponto de série diária. Dias sem registro vêm com valor 0, para o gráfico não pular datas. */
    public record Ponto(LocalDate dia, long valor) {}

    /** Total histórico e quanto disso aconteceu dentro do período escolhido. */
    public record Contagem(long total, long periodo) {}

    public record MetricasUsuarios(long total, long novos, long verificados, long premiumAtivos,
                                   long ativos7d, long ativos30d, List<Ponto> cadastrosPorDia) {}

    /** receitaPorDia em centavos; taxaConversao em 0..1 (null quando não há pedidos no período). */
    public record MetricasVendas(String receitaPeriodo, String receitaTotal, String ticketMedio, long pedidosPagos,
                                 long reembolsos, Double taxaConversao, Map<String, Long> pagosPorMetodo,
                                 Map<String, Long> pedidosPorStatus, List<Ponto> receitaPorDia) {}

    /** revisoes só conta o período: o log é apagado pela retenção (studiy.retencao.revisoes-dias). */
    public record MetricasConteudo(Contagem resumos, Contagem flashcards, Contagem simulados, Contagem pdfs,
                                   long materias, long revisoesPeriodo, Double notaMediaSimulados,
                                   List<Ponto> resumosPorDia, List<Ponto> simuladosPorDia, List<Ponto> flashcardsPorDia) {}

    public record MetricasIa(String custo, long chamadas, long falhas, Map<String, Long> chamadasPorTipo,
                             List<Ponto> chamadasPorDia) {}

    public record Metricas(int dias, LocalDateTime geradoEm, MetricasUsuarios usuarios, MetricasVendas vendas,
                           MetricasConteudo conteudo, MetricasIa ia) {}
}
