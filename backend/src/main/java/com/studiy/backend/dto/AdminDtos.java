package com.studiy.backend.dto;

import com.studiy.backend.model.MetodoPagamento;
import com.studiy.backend.model.Papel;
import com.studiy.backend.model.Plano;
import com.studiy.backend.model.StatusPedido;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

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
}
