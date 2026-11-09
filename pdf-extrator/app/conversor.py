"""Converte PDF em Markdown com PyMuPDF (pymupdf4llm, modo layout), mantendo figuras e gráficos.

As imagens (fotos e também gráficos vetoriais, que o layout renderiza como imagem) saem do
Markdown e viram referências curtas, ``![Figura 3](imagem:3)``, na posição em que aparecem.
O conteúdo de cada uma vai à parte, em ``imagens``, para quem chama guardar e mandar à IA.
"""

from __future__ import annotations

import base64
import hashlib
import re
from dataclasses import dataclass, field

import pymupdf
import pymupdf4llm

pymupdf4llm.use_layout(True)

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

    def para_json(self) -> dict:
        return {"markdown": self.markdown, "paginas": self.paginas,
                "imagens": [i.para_json() for i in self.imagens],
                "imagensOmitidas": self.imagens_omitidas,
                "motor": f"pymupdf4llm {pymupdf4llm.__version__}"}


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
        bruto = pymupdf4llm.to_markdown(
            doc, embed_images=True, dpi=110, header=False, footer=False,
            use_ocr=False, show_progress=False)
        paginas = doc.page_count
    return _pos_processar(bruto, paginas)


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
