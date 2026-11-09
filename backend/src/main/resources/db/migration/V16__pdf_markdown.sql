-- O texto do PDF passa a ser guardado como Markdown (títulos, listas, parágrafos).
-- PDFs antigos ficam como TEXTO até o conversor da inicialização reescrevê-los.
ALTER TABLE pdfs ADD COLUMN formato VARCHAR(10) NOT NULL DEFAULT 'TEXTO';
CREATE INDEX idx_pdfs_formato ON pdfs(formato) WHERE formato <> 'MARKDOWN';
