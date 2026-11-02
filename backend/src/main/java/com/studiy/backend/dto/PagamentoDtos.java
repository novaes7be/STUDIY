package com.studiy.backend.dto;

import com.studiy.backend.model.MetodoPagamento;
import com.studiy.backend.model.StatusPedido;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public final class PagamentoDtos {

    private PagamentoDtos() {}

    /** retornoUrl = pasta do front (ex.: http://127.0.0.1:5500/STUDIY/src); o servidor valida contra a lista de origens permitidas. */
    public record CheckoutRequest(@NotNull MetodoPagamento metodo, @Size(max = 300) String retornoUrl) {}

    public record CheckoutResponse(String pedidoId, String url, boolean simulacao) {}

    public record PedidoResponse(String id, MetodoPagamento metodo, StatusPedido status, int valorCentavos, LocalDateTime planoExpiraEm) {}

    public record ConfigResponse(int precoCentavos, int dias, boolean simulacao) {}
}
