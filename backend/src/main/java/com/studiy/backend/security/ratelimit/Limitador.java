package com.studiy.backend.security.ratelimit;

import io.github.bucket4j.BucketConfiguration;

import java.time.Duration;

/** Token bucket por chave. Implementações: em memória (uma instância) ou Redis (várias instâncias). */
public interface Limitador {

    /** true se a chamada pode seguir; false se a chave estourou o limite por minuto. */
    boolean consumir(String chave, int porMinuto);

    static BucketConfiguration configuracao(int porMinuto) {
        return BucketConfiguration.builder()
                .addLimit(l -> l.capacity(porMinuto).refillGreedy(porMinuto, Duration.ofMinutes(1)))
                .build();
    }
}
