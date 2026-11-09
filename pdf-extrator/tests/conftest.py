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
