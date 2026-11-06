package com.studiy.backend.dto;

import com.studiy.backend.model.MetodoPagamento;
import com.studiy.backend.model.StatusPedido;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public final class PagamentoDtos {

    private PagamentoDtos() {}

    /** O cliente só escolhe o método. Preço, plano e URL de retorno (studiy.front.url) são do servidor. */
    public record CheckoutRequest(@NotNull MetodoPagamento metodo) {}

    public record CheckoutResponse(String pedidoId, String url, boolean simulacao) {}

    public record PedidoResponse(String id, MetodoPagamento metodo, StatusPedido status, int valorCentavos, LocalDateTime planoExpiraEm) {}

    public record ConfigResponse(int precoCentavos, String precoFormatado, int dias, boolean simulacao) {}
}
