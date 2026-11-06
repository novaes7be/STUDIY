package com.studiy.backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.studiy.backend.dto.PagamentoDtos;
import com.studiy.backend.security.UsuarioAtual;
import com.studiy.backend.service.PagamentoService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/pagamentos")
public class PagamentoController {

    private final PagamentoService pagamentoService;
    private final UsuarioAtual usuarioAtual;

    public PagamentoController(PagamentoService pagamentoService, UsuarioAtual usuarioAtual) {
        this.pagamentoService = pagamentoService;
        this.usuarioAtual = usuarioAtual;
    }

    @GetMapping("/config")
    public PagamentoDtos.ConfigResponse config() {
        return pagamentoService.config();
    }

    @PostMapping("/checkout")
    public PagamentoDtos.CheckoutResponse checkout(Authentication auth, @Valid @RequestBody PagamentoDtos.CheckoutRequest request) {
        return pagamentoService.criarCheckout(usuarioAtual.resolver(auth), request.metodo(), request.retornoUrl());
    }

    @GetMapping("/{id}")
    public PagamentoDtos.PedidoResponse consultar(Authentication auth, @PathVariable String id) {
        return pagamentoService.consultar(usuarioAtual.resolver(auth), id);
    }

    /** Público, mas só aceita chamadas com x-signature válido do Mercado Pago. */
    @PostMapping("/webhook")
    public ResponseEntity<Void> webhook(
            @RequestParam(name = "data.id", required = false) String dataId,
            @RequestParam(name = "type", required = false) String type,
            @RequestHeader(name = "x-signature", required = false) String assinatura,
            @RequestHeader(name = "x-request-id", required = false) String requestId,
            @RequestBody(required = false) JsonNode corpo
    ) {
        pagamentoService.processarWebhook(dataId, type, assinatura, requestId, corpo);
        return ResponseEntity.ok().build();
    }
}
