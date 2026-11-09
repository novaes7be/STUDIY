package com.studiy.backend.service;

import com.studiy.backend.model.IaChamada;
import com.studiy.backend.model.TipoGeracao;
import com.studiy.backend.model.Usuario;
import com.studiy.backend.repository.IaChamadaRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Registra tokens e custo de cada chamada de IA. Preços em US$ por milhão de tokens, configuráveis
 * (studiy.ia.preco.*) para acompanhar a tabela da Anthropic sem mudar código. Custo guardado em
 * micro-dólares (inteiro) para não acumular erro de ponto flutuante.
 */
@Service
public class IaCustoService {

    private final IaChamadaRepository repo;
    private final org.springframework.core.env.Environment env;
    private final BigDecimal precoEntrada;
    private final BigDecimal precoSaida;
    private final BigDecimal precoCacheEscrita;
    private final BigDecimal precoCacheLeitura;

    /** Usado nos testes: um preço só para qualquer modelo. */
    IaCustoService(IaChamadaRepository repo, BigDecimal precoEntrada, BigDecimal precoSaida,
                   BigDecimal precoCacheEscrita, BigDecimal precoCacheLeitura) {
        this(repo, null, precoEntrada, precoSaida, precoCacheEscrita, precoCacheLeitura);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public IaCustoService(
            IaChamadaRepository repo,
            org.springframework.core.env.Environment env,
            @Value("${studiy.ia.preco.entrada-usd-mtok}") BigDecimal precoEntrada,
            @Value("${studiy.ia.preco.saida-usd-mtok}") BigDecimal precoSaida,
            @Value("${studiy.ia.preco.cache-escrita-usd-mtok}") BigDecimal precoCacheEscrita,
            @Value("${studiy.ia.preco.cache-leitura-usd-mtok}") BigDecimal precoCacheLeitura
    ) {
        this.repo = repo;
        this.env = env;
        this.precoEntrada = precoEntrada;
        this.precoSaida = precoSaida;
        this.precoCacheEscrita = precoCacheEscrita;
        this.precoCacheLeitura = precoCacheLeitura;
    }

    /** US$/MTok × tokens = micro-US$ (1 MTok × 1 US$ = 10^6 micro-US$, então os fatores se anulam). */
    long custoMicroUsd(RespostaIa r) {
        BigDecimal total = preco(r.modelo(), "entrada", precoEntrada).multiply(BigDecimal.valueOf(r.tokensEntrada()))
                .add(preco(r.modelo(), "saida", precoSaida).multiply(BigDecimal.valueOf(r.tokensSaida())))
                .add(preco(r.modelo(), "cache-escrita", precoCacheEscrita).multiply(BigDecimal.valueOf(r.tokensCacheEscrita())))
                .add(preco(r.modelo(), "cache-leitura", precoCacheLeitura).multiply(BigDecimal.valueOf(r.tokensCacheLeitura())));
        return total.setScale(0, java.math.RoundingMode.HALF_UP).longValueExact();
    }

    /**
     * Preço do modelo QUE RESPONDEU (studiy.ia.preco.&lt;modelo&gt;.*); sem preço próprio, usa o padrão.
     * Conta também quando o servidor troca de modelo sozinho (fallback do Sonnet).
     */
    private BigDecimal preco(String modelo, String campo, BigDecimal padrao) {
        if (env == null || modelo == null) return padrao;
        String v = env.getProperty("studiy.ia.preco." + modelo + "." + campo + "-usd-mtok");
        return v == null ? padrao : new BigDecimal(v);
    }

    /** Transação própria: o registro fica mesmo que a geração falhe depois. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrar(Usuario usuario, TipoGeracao tipo, RespostaIa r) {
        repo.save(IaChamada.builder()
                .usuario(usuario).tipo(tipo).modelo(r.modelo())
                .tokensEntrada(r.tokensEntrada()).tokensSaida(r.tokensSaida())
                .tokensCacheEscrita(r.tokensCacheEscrita()).tokensCacheLeitura(r.tokensCacheLeitura())
                .custoMicroUsd(custoMicroUsd(r))
                .sucesso(true).motivoParada(r.motivoParada())
                .criadoEm(LocalDateTime.now())
                .build());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrarFalha(Usuario usuario, TipoGeracao tipo, String modelo, String motivo) {
        repo.save(IaChamada.builder()
                .usuario(usuario).tipo(tipo).modelo(modelo)
                .sucesso(false).motivoParada(motivo)
                .criadoEm(LocalDateTime.now())
                .build());
    }
}
