# Extrator de PDF (PyMuPDF)

Serviço interno que converte PDF em Markdown com `pymupdf4llm` (modo layout), **mantendo figuras e
gráficos**: gráficos vetoriais são renderizados como imagem e ficam no texto como
`![Figura 3](imagem:3)`, na posição em que aparecem. Só a API Java chama (rede interna do Docker,
cabeçalho `X-Extrator-Token`). Se ele estiver fora do ar, a API usa o conversor PDFBox.

- `POST /converter` (multipart `arquivo`) → `{markdown, paginas, imagens[{id, mime, largura, altura, base64}], imagensOmitidas, motor}`
- `GET /saude`

Limites: 10 MB, 300 páginas, 120 s por PDF (processo isolado, trocado a cada PDF), até 80 figuras
de no máximo 1024 px.

```bash
python3 -m venv .venv && .venv/bin/pip install -r requirements-dev.txt
.venv/bin/python -m pytest -q
```

## Licença

PyMuPDF é distribuído sob **AGPL-3.0** (ou licença comercial da Artifex). Por isso este diretório
é licenciado sob a AGPL-3.0 (ver `LICENSE`), separado do restante do Studiy (MIT). O código-fonte
fica público neste repositório, como a AGPL exige para serviços acessados pela rede.
O pacote `pymupdf-layout` (dependência do PyMuPDF 2.x) não declara licença nos metadados: confirme
os termos com a Artifex antes de uso comercial.
