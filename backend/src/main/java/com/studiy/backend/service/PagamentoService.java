package com.studiy.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.studiy.backend.dto.PagamentoDtos.CheckoutResponse;
import com.studiy.backend.dto.PagamentoDtos.ConfigResponse;
import com.studiy.backend.dto.PagamentoDtos.PedidoResponse;
import com.studiy.backend.model.MetodoPagamento;
import com.studiy.backend.model.Pedido;
import com.studiy.backend.model.Plano;
import com.studiy.backend.model.StatusPedido;
import com.studiy.backend.model.Usuario;
import com.studiy.backend.repository.PedidoRepository;
import com.studiy.backend.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Plano Pro por período pago (cartão ou Pix) via Mercado Pago (Checkout Pro).
 *
 * Regras de segurança:
 *  - preço, plano e duração são decididos AQUI; o front só escolhe o método;
 *  - dados do cartão nunca passam pelo Studiy (o pagamento acontece no ambiente do Mercado Pago);
 *  - o acesso Pro só é liberado depois de um webhook com assinatura válida E de consultar o pagamento
 *    direto na API do Mercado Pago (o corpo do webhook nunca é usado para decidir nada);
 *  - eventos repetidos/simultâneos são idempotentes (trava na linha do pedido + id de pagamento único).
 */
@Service
public class PagamentoService {

    private static final Logger log = LoggerFactory.getLogger(PagamentoService.class);

    private static final List<String> EXCLUIR_NO_PIX =
            List.of("credit_card", "debit_card", "ticket", "atm", "prepaid_card", "account_money");
    private static final List<String> EXCLUIR_NO_CARTAO =
            List.of("ticket", "atm", "bank_transfer", "account_money");

    private final PedidoRepository pedidoRepository;
    private final UsuarioRepository usuarioRepository;
    private final MercadoPagoClient mp;
    private final TransactionTemplate tx;
    private final int precoCentavos;
    private final int dias;
    private final boolean simulacao;
    private final String segredoWebhook;
    private final String apiPublica;
    private final CorsConfiguration origensPermitidas = new CorsConfiguration();

    public PagamentoService(
            PedidoRepository pedidoRepository,
            UsuarioRepository usuarioRepository,
            MercadoPagoClient mp,
            PlatformTransactionManager transactionManager,
            @Value("${studiy.pagamento.preco-centavos:2000}") int precoCentavos,
            @Value("${studiy.pagamento.dias-pro:30}") int dias,
            @Value("${studiy.pagamento.simulacao:false}") boolean simulacao,
            @Value("${studiy.pagamento.mp.webhook-secret:}") String segredoWebhook,
            @Value("${studiy.pagamento.api-publica:}") String apiPublica,
            @Value("${studiy.cors.origins}") String origens
    ) {
        this.pedidoRepository = pedidoRepository;
        this.usuarioRepository = usuarioRepository;
        this.mp = mp;
        this.tx = new TransactionTemplate(transactionManager);
        this.precoCentavos = precoCentavos;
        this.dias = dias;
        this.simulacao = simulacao;
        this.segredoWebhook = segredoWebhook == null ? "" : segredoWebhook.trim();
        this.apiPublica = apiPublica == null ? "" : apiPublica.trim();
        this.origensPermitidas.setAllowedOriginPatterns(
                Arrays.stream(origens.split(",")).map(String::trim).filter(s -> !s.isBlank()).toList());
    }

    public ConfigResponse config() {
        return new ConfigResponse(precoCentavos, dias, simulacao && !mp.configurado());
    }

    /** Cria o pedido e devolve para onde enviar o usuário (checkout do Mercado Pago ou tela de simulação). */
    public CheckoutResponse criarCheckout(Usuario usuario, MetodoPagamento metodo, String retornoUrl) {
        AuthService.exigirEmailVerificado(usuario);
        boolean gateway = mp.configurado();
        if (!gateway && !simulacao) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Pagamento indisponível no momento.");
        }
        String base = validarRetorno(retornoUrl);

        Pedido pedido = pedidoRepository.save(Pedido.builder()
                .id(UUID.randomUUID().toString())
                .usuario(usuario)
                .metodo(metodo)
                .valorCentavos(precoCentavos)
                .status(StatusPedido.PENDENTE)
                .build());
        String voltar = base + "/pagamento.html?pedido=" + pedido.getId();

        if (!gateway) {
            return new CheckoutResponse(pedido.getId(), voltar + "&simulado=1", true);
        }

        JsonNode resposta = mp.criarPreferencia(preferencia(pedido, usuario, voltar));
        String url = resposta.path(mp.sandbox() ? "sandbox_init_point" : "init_point").asText("");
        if (url.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Não foi possível iniciar o pagamento agora. Tente novamente.");
        }
        pedido.setMpPreferenceId(resposta.path("id").asText(null));
        pedidoRepository.save(pedido);
        return new CheckoutResponse(pedido.getId(), url, false);
    }

    public PedidoResponse consultar(Usuario usuario, String pedidoId) {
        Pedido pedido = pedidoDoUsuario(usuario, pedidoId);
        return resposta(pedido, usuarioRepository.findById(usuario.getId()).orElse(usuario));
    }

    /** Só existe em desenvolvimento (sem credenciais do Mercado Pago e com simulacao=true). */
    public PedidoResponse simular(Usuario usuario, String pedidoId) {
        if (!simulacao || mp.configurado()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Não encontrado");
        }
        pedidoDoUsuario(usuario, pedidoId);
        tx.executeWithoutResult(status -> {
            Pedido p = pedidoRepository.buscarComTrava(pedidoId).orElseThrow();
            if (p.getStatus() == StatusPedido.PENDENTE || p.getStatus() == StatusPedido.REJEITADO) {
                ativar(p, "SIMULADO-" + p.getId());
            }
        });
        return consultar(usuario, pedidoId);
    }

    /* ---------------- Webhook ---------------- */

    public void processarWebhook(String dataId, String tipo, String xSignature, String xRequestId, JsonNode corpo) {
        if (!AssinaturaMercadoPago.valida(segredoWebhook, dataId, xSignature, xRequestId)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Assinatura inválida");
        }
        String tipoEvento = tipo != null ? tipo : (corpo != null ? corpo.path("type").asText("") : "");
        if (!"payment".equals(tipoEvento) || !dataId.matches("\\d{1,20}")) {
            return; // evento que não nos interessa
        }
        // Nunca confiamos no corpo do webhook: consultamos o pagamento direto no Mercado Pago.
        JsonNode pagamento = mp.buscarPagamento(dataId);
        processarPagamento(
                pagamento.path("external_reference").asText(""),
                dataId,
                pagamento.path("status").asText(""),
                pagamento.path("transaction_amount").decimalValue(),
                pagamento.path("currency_id").asText(""));
    }

    void processarPagamento(String referencia, String paymentId, String status, BigDecimal valor, String moeda) {
        tx.executeWithoutResult(t -> {
            Pedido p = pedidoRepository.buscarComTrava(referencia).orElse(null);
            if (p == null) {
                log.warn("Webhook do pagamento {} sem pedido correspondente", paymentId);
                return;
            }
            switch (status) {
                case "approved" -> {
                    if (p.getStatus() == StatusPedido.PAGO || p.getStatus() == StatusPedido.REEMBOLSADO) {
                        if (!paymentId.equals(p.getMpPaymentId())) {
                            log.warn("Pagamento extra {} aprovado para o pedido {} já pago: revisar/reembolsar", paymentId, p.getId());
                        }
                        return; // evento repetido: nada a fazer
                    }
                    BigDecimal esperado = BigDecimal.valueOf(p.getValorCentavos()).movePointLeft(2);
                    if (p.getUsuario() == null || valor.compareTo(esperado) != 0 || !"BRL".equals(moeda)) {
                        log.warn("Pagamento {} do pedido {} com valor/moeda inesperados ({} {})", paymentId, p.getId(), valor, moeda);
                        p.setStatus(StatusPedido.REJEITADO);
                        pedidoRepository.save(p);
                        return;
                    }
                    ativar(p, paymentId);
                }
                case "rejected", "cancelled" -> {
                    if (p.getStatus() == StatusPedido.PENDENTE) {
                        p.setStatus(StatusPedido.REJEITADO);
                        pedidoRepository.save(p);
                    }
                }
                case "refunded", "charged_back" -> {
                    if (p.getStatus() == StatusPedido.PAGO) {
                        p.setStatus(StatusPedido.REEMBOLSADO);
                        pedidoRepository.save(p);
                        revogar(p);
                    }
                }
                default -> { /* pending / in_process: continua aguardando */ }
            }
        });
    }

    /* ---------------- Regras do plano ---------------- */

    /** Soma os dias ao fim do plano atual (se ainda ativo) ou a partir de agora. Chamar dentro de transação. */
    private void ativar(Pedido p, String paymentId) {
        Usuario u = usuarioRepository.findById(p.getUsuario().getId()).orElseThrow();
        LocalDateTime agora = LocalDateTime.now();
        LocalDateTime base = u.getPlanoExpiraEm() != null && u.getPlanoExpiraEm().isAfter(agora) ? u.getPlanoExpiraEm() : agora;
        u.setPlano(Plano.PREMIUM);
        u.setPlanoExpiraEm(base.plusDays(dias));
        usuarioRepository.save(u);

        p.setStatus(StatusPedido.PAGO);
        p.setMpPaymentId(paymentId);
        p.setPagoEm(agora);
        pedidoRepository.save(p);
    }

    private void revogar(Pedido p) {
        if (p.getUsuario() == null) return;
        usuarioRepository.findById(p.getUsuario().getId()).ifPresent(u -> {
            if (u.getPlanoExpiraEm() == null) return;
            LocalDateTime novo = u.getPlanoExpiraEm().minusDays(dias);
            if (novo.isAfter(LocalDateTime.now())) {
                u.setPlanoExpiraEm(novo);
            } else {
                u.setPlanoExpiraEm(null);
                u.setPlano(Plano.FREE);
            }
            usuarioRepository.save(u);
        });
    }

    /* ---------------- Auxiliares ---------------- */

    private Pedido pedidoDoUsuario(Usuario usuario, String pedidoId) {
        try {
            UUID.fromString(pedidoId);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pedido não encontrado");
        }
        return pedidoRepository.findByIdAndUsuarioId(pedidoId, usuario.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pedido não encontrado"));
    }

    private PedidoResponse resposta(Pedido p, Usuario u) {
        boolean pro = u.planoEfetivo() == Plano.PREMIUM;
        return new PedidoResponse(p.getId(), p.getMetodo(), p.getStatus(), p.getValorCentavos(), pro ? u.getPlanoExpiraEm() : null);
    }

    /** O front informa a pasta onde está; só aceitamos origens da lista de CORS (evita redirecionamento para sites de terceiros). */
    private String validarRetorno(String retorno) {
        if (retorno == null || retorno.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Endereço de retorno ausente");
        }
        try {
            URI uri = URI.create(retorno.trim());
            String esquema = uri.getScheme();
            boolean ok = ("http".equals(esquema) || "https".equals(esquema))
                    && uri.getRawAuthority() != null && uri.getUserInfo() == null
                    && uri.getRawQuery() == null && uri.getRawFragment() == null;
            String origem = ok ? esquema + "://" + uri.getRawAuthority() : null;
            if (origem == null || origensPermitidas.checkOrigin(origem) == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Endereço de retorno não permitido");
            }
            String caminho = uri.getRawPath() == null ? "" : uri.getRawPath().replaceAll("/+$", "");
            return origem + caminho;
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Endereço de retorno inválido");
        }
    }

    private Map<String, Object> preferencia(Pedido p, Usuario u, String voltar) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", "studiy-pro");
        item.put("title", "Studiy Pro - " + dias + " dias");
        item.put("quantity", 1);
        item.put("currency_id", "BRL");
        item.put("unit_price", BigDecimal.valueOf(p.getValorCentavos()).movePointLeft(2));

        List<Map<String, String>> excluidos = (p.getMetodo() == MetodoPagamento.PIX ? EXCLUIR_NO_PIX : EXCLUIR_NO_CARTAO)
                .stream().map(id -> Map.of("id", id)).toList();
        Map<String, Object> metodos = new LinkedHashMap<>();
        metodos.put("excluded_payment_types", excluidos);
        metodos.put("installments", 1);

        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("items", List.of(item));
        corpo.put("payer", Map.of("email", u.getEmail()));
        corpo.put("external_reference", p.getId());
        corpo.put("statement_descriptor", "STUDIY");
        corpo.put("payment_methods", metodos);
        corpo.put("back_urls", Map.of("success", voltar, "failure", voltar, "pending", voltar));
        if (voltar.startsWith("https://")) {
            corpo.put("auto_return", "approved");
        }
        if (apiPublica.startsWith("https://")) {
            corpo.put("notification_url", apiPublica.replaceAll("/+$", "") + "/api/pagamentos/webhook");
        }
        return corpo;
    }
}
