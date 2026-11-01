package com.studiy.backend.service;

import com.studiy.backend.model.Plano;
import com.studiy.backend.model.UsoMensal;
import com.studiy.backend.model.Usuario;
import com.studiy.backend.repository.UsoMensalRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.YearMonth;

/**
 * Controla o rate limit de geração de IA / PDFs por usuário e por mês,
 * de acordo com o plano (FREE ou PREMIUM). Isso é o que mantém o custo
 * de IA sob controle: nunca chamamos o modelo sem checar aqui antes.
 */
@Service
public class UsoMensalService {

    private final UsoMensalRepository usoMensalRepository;
    private final int limiteFree;
    private final int limitePremium;

    public UsoMensalService(
            UsoMensalRepository usoMensalRepository,
            @Value("${studiy.limits.free.pdfs-por-mes:20}") int limiteFree,
            @Value("${studiy.limits.premium.pdfs-por-mes:300}") int limitePremium
    ) {
        this.usoMensalRepository = usoMensalRepository;
        this.limiteFree = limiteFree;
        this.limitePremium = limitePremium;
    }

    private String mesAtual() {
        return YearMonth.now().toString(); // yyyy-MM
    }

    public int limiteDoPlano(Plano plano) {
        return plano == Plano.PREMIUM ? limitePremium : limiteFree;
    }

    public UsoMensal buscarOuCriar(Usuario usuario) {
        return usoMensalRepository.findByUsuarioIdAndMesReferencia(usuario.getId(), mesAtual())
                .orElseGet(() -> usoMensalRepository.save(
                        UsoMensal.builder()
                                .usuario(usuario)
                                .mesReferencia(mesAtual())
                                .pdfsUsados(0)
                                .build()
                ));
    }

    /** Lança 429 se o usuário já estourou o limite do plano. */
    public void garantirDentroDoLimite(Usuario usuario) {
        UsoMensal uso = buscarOuCriar(usuario);
        int limite = limiteDoPlano(usuario.planoEfetivo());

        if (uso.getPdfsUsados() >= limite) {
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Você atingiu o limite de " + limite + " gerações com IA deste mês no plano " + (usuario.planoEfetivo() == Plano.PREMIUM ? "Pro" : "Grátis") + "."
            );
        }
    }

    public void incrementar(Usuario usuario) {
        UsoMensal uso = buscarOuCriar(usuario);
        uso.setPdfsUsados(uso.getPdfsUsados() + 1);
        usoMensalRepository.save(uso);
    }
}
