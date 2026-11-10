"""Converte PDF em Markdown com PyMuPDF (pymupdf4llm, modo layout), mantendo figuras e gráficos.

As imagens (fotos e também gráficos vetoriais, que o layout renderiza como imagem) saem do
Markdown e viram referências curtas, ``![Figura 3](imagem:3)``, na posição em que aparecem.
O conteúdo de cada uma vai à parte, em ``imagens``, para quem chama guardar e mandar à IA.
"""

from __future__ import annotations

import base64
import hashlib
import os
import re
from collections import Counter
from dataclasses import dataclass, field

import pymupdf
import pymupdf4llm

pymupdf4llm.use_layout(True)

# "hibrido" (padrão): o modelo de layout, lento (~0,7 s/página), só nas páginas com figura;
# as de texto puro vão pelo modo rápido (~0,04 s/página). "layout": o modelo em todas.
MODO = os.environ.get("EXTRATOR_MODO", "hibrido")
LIMITE_PAGINAS_LAYOUT = int(os.environ.get("EXTRATOR_LIMITE_PAGINAS_LAYOUT", "120"))
LADO_MINIMO_FIGURA_PT = 40     # imagem menor que isso (em pontos) é ícone, não figura
DESENHOS_MINIMOS = 4           # traços vetoriais para a página contar como gráfico
FRACAO_REPETIDA = 0.3          # imagem em mais de 30% das páginas é logo/fundo, não figura

PAGINAS_MAXIMAS = 300
LADO_MAXIMO_PX = 1024          # ~1000 tokens por figura na API; legível para gráficos
LADO_MINIMO_PX = 24            # faixas finas (linhas, rótulos soltos) não são figuras
IMAGENS_MAXIMAS = 80
BYTES_MAXIMOS_IMAGENS = 12 * 1024 * 1024

_IMAGEM_EMBUTIDA = re.compile(r"!\[[^\]]*\]\(data:image/(\w+);base64,([A-Za-z0-9+/=]+)\)")
_TEXTO_DE_FIGURA = re.compile(
    r"<!--\s*Start of picture text\s*-->(.*?)<!--\s*End of picture text\s*-->", re.DOTALL)
_TITULO_EM_NEGRITO = re.compile(r"^(#{1,6})\s+\*\*(.+?)\*\*\s*$", re.MULTILINE)


class PdfInvalido(ValueError):
    """PDF que não dá para converter (protegido, páginas demais, corrompido)."""


@dataclass
class Imagem:
    id: int
    mime: str
    largura: int
    altura: int
    dados: bytes

    def para_json(self) -> dict:
        return {"id": self.id, "mime": self.mime, "largura": self.largura, "altura": self.altura,
                "base64": base64.b64encode(self.dados).decode("ascii")}


@dataclass
class Resultado:
    markdown: str
    paginas: int
    imagens: list[Imagem] = field(default_factory=list)
    imagens_omitidas: int = 0
    paginas_layout: int | None = None   # None = layout em todas as páginas

    def motor(self) -> str:
        base = f"pymupdf4llm {pymupdf4llm.__version__}"
        if self.paginas_layout is None:
            return base
        return f"{base} híbrido {self.paginas_layout}/{self.paginas}"  # cabe nos 40 caracteres da coluna

    def para_json(self) -> dict:
        return {"markdown": self.markdown, "paginas": self.paginas,
                "imagens": [i.para_json() for i in self.imagens],
                "imagensOmitidas": self.imagens_omitidas,
                "paginasLayout": self.paginas_layout,
                "motor": self.motor()}


def converter(pdf: bytes) -> Resultado:
    try:
        doc = pymupdf.open(stream=pdf, filetype="pdf")
    except Exception as e:  # noqa: BLE001 - qualquer falha de leitura é PDF inválido
        raise PdfInvalido("Não foi possível ler o PDF") from e
    with doc:
        if doc.needs_pass:
            raise PdfInvalido("PDF protegido por senha")
        if doc.page_count > PAGINAS_MAXIMAS:
            raise PdfInvalido(f"PDF com páginas demais (máximo {PAGINAS_MAXIMAS})")
        paginas = doc.page_count
        if MODO == "layout":
            bruto, com_layout = _markdown_com_layout(doc, None), None
        else:
            bruto, com_layout = _markdown_hibrido(doc)
    resultado = _pos_processar(bruto, paginas)
    resultado.paginas_layout = com_layout
    return resultado


def _markdown_com_layout(doc: pymupdf.Document, paginas: list[int] | None, *, em_pedacos: bool = False):
    pymupdf4llm.use_layout(True)
    r = pymupdf4llm.to_markdown(
        doc, pages=paginas, page_chunks=em_pedacos, embed_images=True, dpi=110, header=False,
        footer=False, use_ocr=False, show_progress=False)
    return [c["text"] for c in r] if em_pedacos else r


def _markdown_rapido(doc: pymupdf.Document, paginas: list[int]) -> list[str]:
    """Modo sem o modelo de layout. Sem imagens: Base64 aqui só poluiria o texto."""
    pymupdf4llm.use_layout(False)
    r = pymupdf4llm.to_markdown(doc, pages=paginas, page_chunks=True, embed_images=False, show_progress=False)
    return _limpar_cabecalho_rodape([_juntar_linhas(c["text"]) for c in r])


def _markdown_hibrido(doc: pymupdf.Document) -> tuple[str, int]:
    """Layout só onde há figura; o resto no modo rápido. Devolve (markdown bruto, páginas com layout)."""
    n = doc.page_count
    com_figura = paginas_com_grafico(doc)[:LIMITE_PAGINAS_LAYOUT]  # teto: tempo máximo do extrator
    marcadas = set(com_figura)
    so_texto = [i for i in range(n) if i not in marcadas]
    pedacos: dict[int, str] = {}
    if com_figura:
        pedacos.update(zip(com_figura, _markdown_com_layout(doc, com_figura, em_pedacos=True)))
    if so_texto:
        pedacos.update(zip(so_texto, _markdown_rapido(doc, so_texto)))
    return "\n\n".join(pedacos[i] for i in range(n)), len(com_figura)


def paginas_com_grafico(doc: pymupdf.Document) -> list[int]:
    """Índices (0-based) das páginas com figura: foto ou gráfico vetorial. Logo, ícone e fundo não contam."""
    n = doc.page_count
    imagens_por_pagina = [pg.get_image_info(xrefs=True) for pg in doc]
    contagem: Counter[int] = Counter()
    for infos in imagens_por_pagina:
        contagem.update({i["xref"] for i in infos if i["xref"]})
    repetidas = {x for x, c in contagem.items() if c > max(2, n * FRACAO_REPETIDA)}

    achadas = []
    for idx, pg in enumerate(doc):
        if any(min(i["bbox"][2] - i["bbox"][0], i["bbox"][3] - i["bbox"][1]) >= LADO_MINIMO_FIGURA_PT
               and i["xref"] not in repetidas for i in imagens_por_pagina[idx]):
            achadas.append(idx)
            continue
        area = pg.rect.width * pg.rect.height
        tracos = sum(1 for d in pg.get_drawings() if d["rect"].width * d["rect"].height < 0.9 * area)  # sem fundo
        if tracos >= DESENHOS_MINIMOS:
            achadas.append(idx)
    return achadas


_ESTRUTURA = re.compile(r"^(#{1,6}\s|\||>|[-*•]\s|\d+[.)]\s|```|\s{4})")


def _juntar_linhas(md: str) -> str:
    """O modo rápido quebra o parágrafo onde o PDF quebrou a linha: junta de volta, só em texto corrido."""
    paragrafos = []
    for p in re.split(r"\n{2,}", md):
        linhas = p.split("\n")
        corrido = not any(_ESTRUTURA.match(l) for l in linhas)
        paragrafos.append(" ".join(l.strip() for l in linhas) if corrido else p)
    return "\n\n".join(paragrafos)


def _limpar_cabecalho_rodape(pedacos: list[str]) -> list[str]:
    """O modo rápido ignora header/footer: tira a primeira/última linha que se repete (dígitos contam
    como iguais: 'Página 3 de 10') e o número de página solto."""
    def chave(linha: str) -> str:
        return re.sub(r"\d+", "#", linha.strip())

    linhas = [[l for l in p.split("\n") if l.strip()] for p in pedacos]
    borda: Counter[str] = Counter()
    for ls in linhas:
        borda.update({chave(l) for l in (ls[:1] + ls[-1:])})
    limite = max(2, len(pedacos) // 2)
    repetidas = {k for k, c in borda.items() if c > limite and len(pedacos) >= 4}

    limpos = []
    for p, ls in zip(pedacos, linhas):
        out = p.split("\n")
        for pos in (0, -1):  # a primeira e a última linha com texto
            idx = next((i for i in (range(len(out)) if pos == 0 else range(len(out) - 1, -1, -1)) if out[i].strip()), None)
            if idx is not None and (chave(out[idx]) in repetidas or re.fullmatch(r"\s*\d{1,4}\s*", out[idx])):
                out[idx] = ""
        limpos.append("\n".join(out).strip())
    return limpos


def _pos_processar(bruto: str, paginas: int) -> Resultado:
    resultado = Resultado(markdown="", paginas=paginas)
    por_hash: dict[str, int] = {}
    total_bytes = 0

    def trocar(m: re.Match) -> str:
        nonlocal total_bytes
        try:
            dados = base64.b64decode(m.group(2))
            pix = pymupdf.Pixmap(dados)
        except Exception:  # noqa: BLE001 - imagem ilegível: ignora
            return ""
        if min(pix.width, pix.height) < LADO_MINIMO_PX or _cor_unica(pix):
            return ""
        pix, dados = _reduzir(pix, dados)
        chave = hashlib.sha256(dados).hexdigest()
        if chave in por_hash:  # mesma figura repetida (logo, gráfico reaproveitado)
            return f"![Figura {por_hash[chave]}](imagem:{por_hash[chave]})"
        if len(resultado.imagens) >= IMAGENS_MAXIMAS or total_bytes + len(dados) > BYTES_MAXIMOS_IMAGENS:
            resultado.imagens_omitidas += 1
            return "*[figura omitida: limite de imagens por PDF]*"
        numero = len(resultado.imagens) + 1
        por_hash[chave] = numero
        total_bytes += len(dados)
        resultado.imagens.append(Imagem(numero, "image/png", pix.width, pix.height, dados))
        return f"![Figura {numero}](imagem:{numero})"

    md = _IMAGEM_EMBUTIDA.sub(trocar, bruto)
    md = _TEXTO_DE_FIGURA.sub(lambda m: "*Texto da figura:* " + _limpar_texto_figura(m.group(1)), md)
    md = _TITULO_EM_NEGRITO.sub(r"\1 \2", md)
    md = "\n".join(linha.rstrip() for linha in md.splitlines())
    md = re.sub(r"\n{3,}", "\n\n", md).strip()
    resultado.markdown = md + "\n" if md else ""
    return resultado


def _cor_unica(pix: pymupdf.Pixmap) -> bool:
    """Retângulo liso não informa nada (fundo, faixa decorativa)."""
    try:
        return pix.color_count() <= 1
    except Exception:  # noqa: BLE001
        return False


def _reduzir(pix: pymupdf.Pixmap, dados: bytes) -> tuple[pymupdf.Pixmap, bytes]:
    maior = max(pix.width, pix.height)
    if maior <= LADO_MAXIMO_PX:
        return pix, dados
    escala = LADO_MAXIMO_PX / maior
    menor = pymupdf.Pixmap(pix, max(1, int(pix.width * escala)), max(1, int(pix.height * escala)), None)
    return menor, menor.tobytes("png")


def _limpar_texto_figura(texto: str) -> str:
    return re.sub(r"\s+", " ", texto.replace("<br>", " ")).strip()
