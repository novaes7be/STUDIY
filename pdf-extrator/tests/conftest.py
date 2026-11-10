import os

import pymupdf
import pytest

os.environ.setdefault("EXTRATOR_TOKEN", "token-de-teste-0123456789")


@pytest.fixture(scope="session")
def pdf_com_grafico() -> bytes:
    """3 páginas: cabeçalho repetido, título, texto, gráfico de barras VETORIAL e uma foto (raster)."""
    doc = pymupdf.open()
    foto = pymupdf.Pixmap(pymupdf.csRGB, pymupdf.IRect(0, 0, 200, 120), 0)
    for y in range(120):  # gradiente: imagem de verdade, não cor única
        for x in range(200):
            foto.set_pixel(x, y, (x, y * 2, 120))
    for n in range(1, 4):
        p = doc.new_page()
        p.insert_text((50, 40), "Apostila de Biologia", fontsize=8)
        p.insert_text((50, 90), f"Capitulo {n} Fotossintese", fontsize=20, fontname="hebo")
        p.insert_textbox(pymupdf.Rect(50, 110, 550, 200), "A fotossintese converte luz em energia quimica. " * 4, fontsize=11)
        p.insert_text((50, 225), "Grafico 1: taxa por intensidade de luz", fontsize=10)
        for i, h in enumerate([40, 80, 120, 90]):
            p.draw_rect(pymupdf.Rect(80 + i * 60, 400 - h, 120 + i * 60, 400), color=(0, 0, 0), fill=(0.2, 0.5, 0.3))
            p.insert_text((85 + i * 60, 415), f"{(i + 1) * 100} lx", fontsize=8)
        p.insert_image(pymupdf.Rect(360, 260, 560, 380), pixmap=foto)
        p.insert_text((300, 800), str(n), fontsize=8)
    dados = doc.tobytes()
    doc.close()
    return dados


def _foto() -> pymupdf.Pixmap:
    pix = pymupdf.Pixmap(pymupdf.csRGB, pymupdf.IRect(0, 0, 200, 120), 0)
    for y in range(120):
        for x in range(200):
            pix.set_pixel(x, y, (x, y * 2, 120))
    return pix


def _pagina_de_texto(doc, n: int, total: int):
    p = doc.new_page()
    p.insert_text((50, 30), "Apostila de Redes", fontsize=8)                 # cabeçalho repetido
    p.insert_text((50, 90), f"Camada {n} de transporte", fontsize=20, fontname="hebo")
    p.insert_textbox(pymupdf.Rect(50, 110, 300, 260),                          # parágrafo estreito: quebra de linha
                     f"O protocolo da camada {n} entrega os segmentos na ordem certa e confirma o recebimento. " * 2,
                     fontsize=11)
    p.insert_text((280, 820), f"Página {n} de {total}", fontsize=8)           # rodapé com número
    return p


@pytest.fixture(scope="session")
def pdf_so_texto() -> bytes:
    """6 páginas só de texto, com cabeçalho e rodapé repetidos."""
    doc = pymupdf.open()
    for n in range(1, 7):
        _pagina_de_texto(doc, n, 6)
    dados = doc.tobytes()
    doc.close()
    return dados


@pytest.fixture(scope="session")
def pdf_misto() -> bytes:
    """6 páginas; só a 2 (foto) e a 5 (gráfico vetorial) têm figura."""
    doc = pymupdf.open()
    foto = _foto()
    for n in range(1, 7):
        p = _pagina_de_texto(doc, n, 6)
        if n == 2:
            p.insert_image(pymupdf.Rect(320, 300, 520, 420), pixmap=foto)
        if n == 5:
            for i, h in enumerate([40, 80, 120, 90]):
                p.draw_rect(pymupdf.Rect(80 + i * 60, 500 - h, 120 + i * 60, 500), color=(0, 0, 0), fill=(0.2, 0.5, 0.3))
                p.insert_text((85 + i * 60, 515), f"{(i + 1) * 100} lx", fontsize=8)
    dados = doc.tobytes()
    doc.close()
    return dados


@pytest.fixture(scope="session")
def pdf_com_logo_em_toda_pagina() -> bytes:
    """6 páginas de texto com o MESMO logo grande e um fundo de página inteira: nada disso é figura."""
    doc = pymupdf.open()
    xref = 0
    for n in range(1, 7):
        p = _pagina_de_texto(doc, n, 6)
        p.draw_rect(p.rect, color=None, fill=(0.97, 0.97, 0.97), overlay=False)   # fundo
        if xref:
            p.insert_image(pymupdf.Rect(430, 20, 550, 100), xref=xref)
        else:
            xref = p.insert_image(pymupdf.Rect(430, 20, 550, 100), pixmap=_foto())
    dados = doc.tobytes()
    doc.close()
    return dados
