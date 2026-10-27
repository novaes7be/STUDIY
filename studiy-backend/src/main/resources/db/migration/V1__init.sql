-- Baseline: estrutura atual do Studiy (usuários, matérias, PDFs, flashcards, simulados, uso mensal)

CREATE TABLE usuarios (
    id          BIGSERIAL PRIMARY KEY,
    nome        VARCHAR(255) NOT NULL,
    email       VARCHAR(255) NOT NULL UNIQUE,
    senha       VARCHAR(255) NOT NULL,
    plano       VARCHAR(20)  NOT NULL DEFAULT 'FREE',
    criado_em   TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE TABLE materias (
    id               BIGSERIAL PRIMARY KEY,
    nome             VARCHAR(255) NOT NULL,
    cor              VARCHAR(20)  NOT NULL DEFAULT '#5b6eff',
    progresso_atual  INTEGER      NOT NULL DEFAULT 0,
    progresso_total  INTEGER      NOT NULL DEFAULT 10,
    usuario_id       BIGINT       NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE
);

CREATE TABLE pdfs (
    id              BIGSERIAL PRIMARY KEY,
    nome_arquivo    VARCHAR(255) NOT NULL,
    texto_extraido  TEXT,
    materia_id      BIGINT       NOT NULL REFERENCES materias(id) ON DELETE CASCADE,
    criado_em       TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE TABLE flashcards (
    id          BIGSERIAL PRIMARY KEY,
    pergunta    TEXT      NOT NULL,
    resposta    TEXT      NOT NULL,
    revisado    BOOLEAN   NOT NULL DEFAULT FALSE,
    materia_id  BIGINT    NOT NULL REFERENCES materias(id) ON DELETE CASCADE,
    criado_em   TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE simulados (
    id               BIGSERIAL PRIMARY KEY,
    nota             INTEGER NOT NULL,
    materia_id       BIGINT  NOT NULL REFERENCES materias(id) ON DELETE CASCADE,
    data_realizacao  DATE    NOT NULL DEFAULT CURRENT_DATE
);

CREATE TABLE uso_mensal (
    id              BIGSERIAL PRIMARY KEY,
    usuario_id      BIGINT       NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    mes_referencia  VARCHAR(7)   NOT NULL,
    pdfs_usados     INTEGER      NOT NULL DEFAULT 0,
    UNIQUE (usuario_id, mes_referencia)
);

-- Novas tabelas deste passo: resumos gerados por IA e atividades (to-do por matéria)

CREATE TABLE resumos (
    id          BIGSERIAL PRIMARY KEY,
    conteudo    TEXT      NOT NULL,
    materia_id  BIGINT    NOT NULL REFERENCES materias(id) ON DELETE CASCADE,
    pdf_id      BIGINT    REFERENCES pdfs(id) ON DELETE SET NULL,
    criado_em   TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE atividades (
    id              BIGSERIAL PRIMARY KEY,
    titulo          VARCHAR(255) NOT NULL,
    data_atividade  DATE,
    tag             VARCHAR(50),
    tag_tipo        VARCHAR(20)  NOT NULL DEFAULT 'info',
    concluida       BOOLEAN      NOT NULL DEFAULT FALSE,
    materia_id      BIGINT       NOT NULL REFERENCES materias(id) ON DELETE CASCADE
);

CREATE INDEX idx_materias_usuario ON materias(usuario_id);
CREATE INDEX idx_pdfs_materia ON pdfs(materia_id);
CREATE INDEX idx_flashcards_materia ON flashcards(materia_id);
CREATE INDEX idx_simulados_materia ON simulados(materia_id);
CREATE INDEX idx_resumos_materia ON resumos(materia_id);
CREATE INDEX idx_atividades_materia ON atividades(materia_id);
