package com.studiy.backend.controller;

import com.studiy.backend.dto.PagamentoDtos;
import com.studiy.backend.security.UsuarioAtual;
import com.studiy.backend.service.PagamentoService;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** Apenas desenvolvimento: aprova o pedido sem cobrança. Fora do perfil dev a rota nem existe. */
@RestController
@Profile("dev")
@RequestMapping("/api/pagamentos")
public class PagamentoSimuladoController {

    private final PagamentoService pagamentoService;
    private final UsuarioAtual usuarioAtual;

    public PagamentoSimuladoController(PagamentoService pagamentoService, UsuarioAtual usuarioAtual) {
        this.pagamentoService = pagamentoService;
        this.usuarioAtual = usuarioAtual;
    }

    @PostMapping("/{id}/simular")
    public PagamentoDtos.PedidoResponse simular(Authentication auth, @PathVariable String id) {
        return pagamentoService.simular(usuarioAtual.resolver(auth), id);
    }
}
