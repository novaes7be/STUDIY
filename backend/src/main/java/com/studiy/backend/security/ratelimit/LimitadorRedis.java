package com.studiy.backend.security.ratelimit;

import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;

import java.time.Duration;

/** Buckets no Redis (compare-and-swap): o limite vale somado entre todas as instâncias da API. */
public class LimitadorRedis implements Limitador, AutoCloseable {

    private final RedisClient cliente;
    private final StatefulRedisConnection<String, byte[]> conexao;
    private final ProxyManager<String> proxy;

    public LimitadorRedis(String url) {
        this.cliente = RedisClient.create(url);
        this.conexao = cliente.connect(RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE));
        this.proxy = LettuceBasedProxyManager.builderFor(conexao)
                .withExpirationStrategy(ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(Duration.ofMinutes(2)))
                .build();
    }

    @Override
    public boolean consumir(String chave, int porMinuto) {
        // A configuração entra na chave: mudar o limite cria um bucket novo em vez de herdar o antigo.
        return proxy.builder()
                .build("studiy:rl:" + porMinuto + ":" + chave, () -> Limitador.configuracao(porMinuto))
                .tryConsume(1);
    }

    @Override
    public void close() {
        conexao.close();
        cliente.shutdown();
    }
}
