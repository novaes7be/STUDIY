-- Refresh tokens rotativos (só o hash SHA-256 fica no banco).
-- "familia" agrupa as rotações de um mesmo login: reuso de um token já trocado revoga a família inteira.
CREATE TABLE sessoes_refresh (
    id          BIGSERIAL PRIMARY KEY,
    usuario_id  BIGINT      NOT NULL REFERENCES usuarios (id) ON DELETE CASCADE,
    token_hash  VARCHAR(64) NOT NULL UNIQUE,
    familia     VARCHAR(36) NOT NULL,
    criado_em   TIMESTAMP   NOT NULL,
    expira_em   TIMESTAMP   NOT NULL,
    usado_em    TIMESTAMP
);
CREATE INDEX idx_sessoes_refresh_usuario ON sessoes_refresh (usuario_id);
CREATE INDEX idx_sessoes_refresh_familia ON sessoes_refresh (familia);
CREATE INDEX idx_sessoes_refresh_expira ON sessoes_refresh (expira_em);
