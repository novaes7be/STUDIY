# Extrator de PDF (PyMuPDF)

Serviço interno que converte PDF em Markdown com `pymupdf4llm` (modo layout), **mantendo figuras e
gráficos**: gráficos vetoriais são renderizados como imagem e ficam no texto como
`![Figura 3](imagem:3)`, na posição em que aparecem. Só a API Java chama (rede interna do Docker,
cabeçalho `X-Extrator-Token`). Se ele estiver fora do ar, a API usa o conversor PDFBox.

- `POST /converter` (multipart `arquivo`) → `{markdown, paginas, imagens[{id, mime, largura, altura, base64}], imagensOmitidas, motor}`
- `GET /saude`

Limites: 10 MB, 300 páginas, 120 s por PDF (processo isolado, trocado a cada PDF), até 80 figuras
de no máximo 1024 px.

## Modo híbrido (padrão)

O modelo de layout é o que enxerga as figuras, mas é lento: ~0,7 s por página medido numa VPS de
4 vCPU (um PDF de 120 páginas levou 81 s). O modo rápido leva ~0,04 s por página, mas **não devolve
figuras**. Por isso o extrator olha cada página: foto ou gráfico vetorial vai para o modelo de layout,
as de texto puro vão pelo modo rápido, e o resultado é montado na ordem original. No mesmo PDF de
120 páginas (18 com figura): 14 a 21 s (duas rodadas) contra 81 s, com as mesmas 14 figuras e ~99,8% das palavras.

Não contam como figura: imagem menor que 40 pt, imagem que se repete em mais de 30% das páginas
(logo, fundo) e traço que cobre a página inteira. Nas páginas do modo rápido, cabeçalho/rodapé
repetidos e o número de página são removidos e as linhas do parágrafo são juntadas.

| Variável | Padrão | Efeito |
|---|---|---|
| `EXTRATOR_MODO` | `hibrido` | `layout` roda o modelo em todas as páginas (mais lento) |
| `EXTRATOR_LIMITE_PAGINAS_LAYOUT` | `120` | teto de páginas com layout; as demais com figura perdem a figura (o `motor` mostra `híbrido K/N`) |
| `EXTRATOR_TEMPO_MAXIMO_S` | `120` | tempo máximo por PDF |

Pior caso: se toda página tiver figura, o tempo é o do layout completo. O `pymupdf_layout` precisa
estar instalado: sem ele, o modo "layout" cai em silêncio no rápido (a sua máquina pode não ter).

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
