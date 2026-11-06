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
    private final BigDecimal precoEntrada;
    private final BigDecimal precoSaida;
    private final BigDecimal precoCacheEscrita;
    private final BigDecimal precoCacheLeitura;

    public IaCustoService(
            IaChamadaRepository repo,
            @Value("${studiy.ia.preco.entrada-usd-mtok}") BigDecimal precoEntrada,
            @Value("${studiy.ia.preco.saida-usd-mtok}") BigDecimal precoSaida,
            @Value("${studiy.ia.preco.cache-escrita-usd-mtok}") BigDecimal precoCacheEscrita,
            @Value("${studiy.ia.preco.cache-leitura-usd-mtok}") BigDecimal precoCacheLeitura
    ) {
        this.repo = repo;
        this.precoEntrada = precoEntrada;
        this.precoSaida = precoSaida;
        this.precoCacheEscrita = precoCacheEscrita;
        this.precoCacheLeitura = precoCacheLeitura;
    }

    /** US$/MTok × tokens = micro-US$ (1 MTok × 1 US$ = 10^6 micro-US$, então os fatores se anulam). */
    long custoMicroUsd(RespostaIa r) {
        BigDecimal total = precoEntrada.multiply(BigDecimal.valueOf(r.tokensEntrada()))
                .add(precoSaida.multiply(BigDecimal.valueOf(r.tokensSaida())))
                .add(precoCacheEscrita.multiply(BigDecimal.valueOf(r.tokensCacheEscrita())))
                .add(precoCacheLeitura.multiply(BigDecimal.valueOf(r.tokensCacheLeitura())));
        return total.setScale(0, java.math.RoundingMode.HALF_UP).longValueExact();
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
