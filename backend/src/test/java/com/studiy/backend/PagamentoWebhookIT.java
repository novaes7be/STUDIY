package com.studiy.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.studiy.backend.model.Plano;
import com.studiy.backend.model.Usuario;
import com.studiy.backend.service.AssinaturaMercadoPago;
import com.studiy.backend.service.MercadoPagoClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Fluxo do pagamento depois do checkout: o Mercado Pago avisa pelo webhook (assinado) e o plano é liberado.
 * O cliente do Mercado Pago é um mock; o resto (assinatura, controller, transação, banco) é o de verdade.
 */
@TestPropertySource(properties = "studiy.pagamento.mp.webhook-secret=" + PagamentoWebhookIT.SEGREDO)
class PagamentoWebhookIT extends IntegracaoBase {

    static final String SEGREDO = "segredo-de-webhook-de-teste-0123456789";
    private static final AtomicLong PROXIMO_PAGAMENTO = new AtomicLong(9_000_000_000L);

    @MockBean MercadoPagoClient mp;

    @BeforeEach
    void mercadoPagoConfigurado() {
        when(mp.configurado()).thenReturn(true);
        when(mp.criarPreferencia(any())).thenReturn(json.valueToTree(
                Map.of("id", "pref-teste", "init_point", "https://www.mercadopago.com.br/checkout/teste")));
    }

    @Test
    void pagamentoAprovadoLiberaOPlanoUmaVezSo() throws Exception {
        Sessao s = novaSessao();
        String pedido = checkout(s);
        String pagamento = pagamentoNoMercadoPago(pedido, "approved", 20.00, "BRL");

        webhook(pagamento, assinaturaValida(pagamento)).andExpect(status().isOk());

        assertThat(statusDoPedido(s, pedido)).isEqualTo("PAGO");
        Usuario u = usuarios.findByEmail(s.email()).orElseThrow();
        assertThat(u.getPlano()).isEqualTo(Plano.PREMIUM);
        LocalDateTime expira = u.getPlanoExpiraEm();
        assertThat(expira).isBetween(LocalDateTime.now().plusDays(29), LocalDateTime.now().plusDays(31));

        // O Mercado Pago reenvia avisos: o mesmo pagamento não pode somar mais 30 dias
        webhook(pagamento, assinaturaValida(pagamento)).andExpect(status().isOk());
        assertThat(usuarios.findByEmail(s.email()).orElseThrow().getPlanoExpiraEm()).isEqualTo(expira);
    }

    @Test
    void assinaturaInvalidaNaoMudaNada() throws Exception {
        Sessao s = novaSessao();
        String pedido = checkout(s);
        String pagamento = pagamentoNoMercadoPago(pedido, "approved", 20.00, "BRL");

        webhook(pagamento, "ts=1700000000,v1=" + "0".repeat(64)).andExpect(status().isUnauthorized());

        assertThat(statusDoPedido(s, pedido)).isEqualTo("PENDENTE");
        assertThat(usuarios.findByEmail(s.email()).orElseThrow().getPlano()).isEqualTo(Plano.FREE);
    }

    @Test
    void valorDiferenteDoPrecoRejeitaOPedido() throws Exception {
        Sessao s = novaSessao();
        String pedido = checkout(s);
        String pagamento = pagamentoNoMercadoPago(pedido, "approved", 0.50, "BRL");

        webhook(pagamento, assinaturaValida(pagamento)).andExpect(status().isOk());

        assertThat(statusDoPedido(s, pedido)).isEqualTo("REJEITADO");
        assertThat(usuarios.findByEmail(s.email()).orElseThrow().getPlano()).isEqualTo(Plano.FREE);
    }

    @Test
    void pagamentoRecusadoDeixaOPlanoComoEstava() throws Exception {
        Sessao s = novaSessao();
        String pedido = checkout(s);
        String pagamento = pagamentoNoMercadoPago(pedido, "rejected", 20.00, "BRL");

        webhook(pagamento, assinaturaValida(pagamento)).andExpect(status().isOk());

        assertThat(statusDoPedido(s, pedido)).isEqualTo("REJEITADO");
        assertThat(usuarios.findByEmail(s.email()).orElseThrow().getPlano()).isEqualTo(Plano.FREE);
    }

    /* ---------------- auxiliares ---------------- */

    private String checkout(Sessao s) throws Exception {
        JsonNode r = ler(mvc.perform(s.em(post("/api/pagamentos/checkout"))
                        .contentType(MediaType.APPLICATION_JSON).content(corpo(Map.of("metodo", "CARTAO"))))
                .andExpect(status().isOk()).andReturn());
        assertThat(r.path("simulacao").asBoolean()).isFalse();
        return r.path("pedidoId").asText();
    }

    /** O que a API do Mercado Pago devolveria em GET /v1/payments/{id}. */
    private String pagamentoNoMercadoPago(String pedido, String statusMp, double valor, String moeda) {
        String id = String.valueOf(PROXIMO_PAGAMENTO.incrementAndGet());
        when(mp.buscarPagamento(id)).thenReturn(json.valueToTree(Map.of(
                "id", Long.parseLong(id), "external_reference", pedido, "status", statusMp,
                "transaction_amount", valor, "currency_id", moeda)));
        return id;
    }

    private static String assinaturaValida(String dataId) {
        String ts = String.valueOf(System.currentTimeMillis() / 1000);
        String manifesto = "id:" + dataId + ";request-id:req-" + dataId + ";ts:" + ts + ";";
        return "ts=" + ts + ",v1=" + AssinaturaMercadoPago.assinar(SEGREDO, manifesto);
    }

    private org.springframework.test.web.servlet.ResultActions webhook(String dataId, String assinatura) throws Exception {
        return mvc.perform(post("/api/pagamentos/webhook").param("data.id", dataId).param("type", "payment")
                .header("x-signature", assinatura).header("x-request-id", "req-" + dataId)
                .contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"payment\",\"data\":{\"id\":\"" + dataId + "\"}}"));
    }

    private String statusDoPedido(Sessao s, String pedido) throws Exception {
        return ler(mvc.perform(s.em(get("/api/pagamentos/" + pedido))).andExpect(status().isOk()).andReturn())
                .path("status").asText();
    }
}
