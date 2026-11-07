-- "Foto" do ranking, recalculada por um job a cada poucos minutos. A API só lê daqui:
-- nunca calcula na hora, então a resposta é rápida e sempre completa (a troca é atômica).
-- usuario_id serve só para achar a posição de quem pergunta; nunca sai na API.
CREATE TABLE ranking_snapshot (
    id          BIGSERIAL PRIMARY KEY,
    tipo        VARCHAR(10) NOT NULL,   -- SEMANA | SEQUENCIA | XP
    posicao     INTEGER     NOT NULL,
    usuario_id  BIGINT      NOT NULL REFERENCES usuarios (id) ON DELETE CASCADE,
    apelido     VARCHAR(20) NOT NULL,
    valor       INTEGER     NOT NULL,
    gerado_em   TIMESTAMP   NOT NULL
);
CREATE INDEX idx_ranking_tipo_posicao ON ranking_snapshot (tipo, posicao);
CREATE INDEX idx_ranking_tipo_usuario ON ranking_snapshot (tipo, usuario_id);
