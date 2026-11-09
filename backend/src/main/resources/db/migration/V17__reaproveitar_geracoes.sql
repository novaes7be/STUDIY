-- Reaproveitar gerações: o mesmo conteúdo (mesmo PDF, mesmo usuário) não chama a IA de novo.
-- hash_conteudo é um HMAC do Markdown (com chave do servidor), não um hash puro: não serve
-- para confirmar se alguém tem um documento conhecido.
ALTER TABLE pdfs ADD COLUMN hash_conteudo VARCHAR(64);

-- Flashcards passam a saber de qual PDF vieram (para não duplicar ao reaproveitar).
ALTER TABLE flashcards ADD COLUMN pdf_id BIGINT REFERENCES pdfs(id) ON DELETE SET NULL;
CREATE INDEX idx_flashcards_pdf ON flashcards(pdf_id);

CREATE TABLE geracoes_cache (
    id             BIGSERIAL PRIMARY KEY,
    usuario_id     BIGINT      NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    hash_conteudo  VARCHAR(64) NOT NULL,
    tipo           VARCHAR(20) NOT NULL,
    conteudo       TEXT        NOT NULL,
    criado_em      TIMESTAMP   NOT NULL DEFAULT now(),
    CONSTRAINT uq_geracao_cache UNIQUE (usuario_id, hash_conteudo, tipo)
);
