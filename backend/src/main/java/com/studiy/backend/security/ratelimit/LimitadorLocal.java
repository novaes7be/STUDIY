package com.studiy.backend.security.ratelimit;

import io.github.bucket4j.Bucket;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Buckets em memória: certo para UMA instância da API. Com várias instâncias, configure
 * STUDIY_REDIS_URL para usar o {@link LimitadorRedis} (limite compartilhado entre elas).
 */
public class LimitadorLocal implements Limitador {

    private static final int MAX_CHAVES = 50_000;
    private static final long OCIOSO_MS = 5 * 60_000L;

    private record Entrada(Bucket bucket, int porMinuto, long[] ultimoUso) {}

    private final ConcurrentHashMap<String, Entrada> buckets = new ConcurrentHashMap<>();

    @Override
    public boolean consumir(String chave, int porMinuto) {
        long agora = System.currentTimeMillis();
        if (buckets.size() > MAX_CHAVES) {
            buckets.entrySet().removeIf(e -> agora - e.getValue().ultimoUso()[0] > OCIOSO_MS);
        }
        Entrada e = buckets.compute(chave, (k, atual) -> atual != null && atual.porMinuto() == porMinuto
                ? atual
                : new Entrada(Bucket.builder()
                        .addLimit(l -> l.capacity(porMinuto).refillGreedy(porMinuto, java.time.Duration.ofMinutes(1)))
                        .build(), porMinuto, new long[1]));
        e.ultimoUso()[0] = agora;
        return e.bucket().tryConsume(1);
    }
}
