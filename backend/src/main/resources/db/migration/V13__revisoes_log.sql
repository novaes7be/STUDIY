-- Uma linha por flashcard revisado: base do ranking "revisões dos últimos 7 dias".
-- Só guarda quem e quando (nunca o conteúdo). Apagado pela retenção após studiy.retencao.revisoes-dias.
CREATE TABLE revisoes_log (
    id          BIGSERIAL PRIMARY KEY,
    usuario_id  BIGINT    NOT NULL REFERENCES usuarios (id) ON DELETE CASCADE,
    criado_em   TIMESTAMP NOT NULL
);
CREATE INDEX idx_revisoes_log_criado_usuario ON revisoes_log (criado_em, usuario_id);
