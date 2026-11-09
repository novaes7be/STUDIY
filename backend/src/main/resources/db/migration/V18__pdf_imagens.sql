-- Figuras dos PDFs (fotos e gráficos renderizados pelo extrator PyMuPDF), referenciadas no
-- Markdown como imagem:N. Conteúdo cifrado em repouso, como o texto. Somem com o PDF.
CREATE TABLE pdf_imagens (
    id       BIGSERIAL PRIMARY KEY,
    pdf_id   BIGINT      NOT NULL REFERENCES pdfs(id) ON DELETE CASCADE,
    numero   INTEGER     NOT NULL,
    mime     VARCHAR(30) NOT NULL,
    largura  INTEGER     NOT NULL,
    altura   INTEGER     NOT NULL,
    dados    TEXT        NOT NULL,
    CONSTRAINT uq_pdf_imagem UNIQUE (pdf_id, numero)
);

-- Qual conversor gerou o Markdown: pymupdf4llm (com figuras) ou pdfbox (só texto, reserva).
ALTER TABLE pdfs ADD COLUMN motor VARCHAR(40);
