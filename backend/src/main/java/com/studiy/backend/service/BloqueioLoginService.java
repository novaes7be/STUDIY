package com.studiy.backend.service;

import com.studiy.backend.model.Usuario;
import com.studiy.backend.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Bloqueio progressivo POR CONTA após falhas de login (complementa o rate limit por IP, que não
 * segura ataque distribuído). A partir de {@code tentativasLivres} falhas seguidas, a conta fica
 * bloqueada por base × 2^(excedentes), até o teto. Um login certo zera o contador.
 */
@Service
public class BloqueioLoginService {

    private final UsuarioRepository usuarioRepository;
    private final int tentativasLivres;
    private final Duration base;
    private final Duration teto;

    public BloqueioLoginService(
            UsuarioRepository usuarioRepository,
            @Value("${studiy.auth.bloqueio.tentativas-livres:5}") int tentativasLivres,
            @Value("${studiy.auth.bloqueio.base-segundos:60}") long baseSegundos,
            @Value("${studiy.auth.bloqueio.teto-minutos:60}") long tetoMinutos
    ) {
        this.usuarioRepository = usuarioRepository;
        this.tentativasLivres = tentativasLivres;
        this.base = Duration.ofSeconds(baseSegundos);
        this.teto = Duration.ofMinutes(tetoMinutos);
    }

    public boolean bloqueado(Usuario u) {
        return u.getBloqueadoAte() != null && u.getBloqueadoAte().isAfter(LocalDateTime.now());
    }

    /** Incremento atômico no banco (seguro com tentativas simultâneas); bloqueia se passou do limite. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrarFalha(Long usuarioId) {
        usuarioRepository.incrementarFalhasLogin(usuarioId);
        Usuario u = usuarioRepository.findById(usuarioId).orElseThrow();
        Duration espera = espera(u.getFalhasLogin());
        if (!espera.isZero()) {
            u.setBloqueadoAte(LocalDateTime.now().plus(espera));
            usuarioRepository.save(u);
        }
    }

    /** Login certo: zera o contador (quem salva a entidade é o chamador). */
    public void limpar(Usuario u) {
        u.setFalhasLogin(0);
        u.setBloqueadoAte(null);
    }

    /** 0 até o limite; depois base, 2×base, 4×base... até o teto. */
    Duration espera(int falhas) {
        int excedentes = falhas - tentativasLivres;
        if (excedentes < 0) return Duration.ZERO;
        long fator = 1L << Math.min(excedentes, 20);
        Duration d = base.multipliedBy(fator);
        return d.compareTo(teto) > 0 ? teto : d;
    }
}
