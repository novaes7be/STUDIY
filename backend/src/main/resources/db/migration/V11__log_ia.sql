-- Uma linha por chamada de IA: tokens e custo estimado (micro-dólares) para controle de gasto.
-- Excluir a conta mantém o custo agregado, mas desvincula o usuário (LGPD).
CREATE TABLE ia_chamadas (
    id                 BIGSERIAL PRIMARY KEY,
    usuario_id         BIGINT REFERENCES usuarios (id) ON DELETE SET NULL,
    tipo               VARCHAR(20)  NOT NULL,
    modelo             VARCHAR(80)  NOT NULL,
    tokens_entrada     INTEGER      NOT NULL DEFAULT 0,
    tokens_saida       INTEGER      NOT NULL DEFAULT 0,
    tokens_cache_escrita INTEGER    NOT NULL DEFAULT 0,
    tokens_cache_leitura INTEGER    NOT NULL DEFAULT 0,
    custo_micro_usd    BIGINT       NOT NULL DEFAULT 0,
    sucesso            BOOLEAN      NOT NULL,
    motivo_parada      VARCHAR(40),
    criado_em          TIMESTAMP    NOT NULL
);
CREATE INDEX idx_ia_chamadas_criado ON ia_chamadas (criado_em);
CREATE INDEX idx_ia_chamadas_usuario ON ia_chamadas (usuario_id);
