import re

import pymupdf

from app import conversor


def test_grafico_vetorial_e_foto_viram_figuras_referenciadas(pdf_com_grafico):
    r = conversor.converter(pdf_com_grafico)

    assert r.paginas == 3
    assert "data:image" not in r.markdown                      # nada de base64 no texto
    refs = [int(n) for n in re.findall(r"!\[Figura \d+\]\(imagem:(\d+)\)", r.markdown)]
    ids = [i.id for i in r.imagens]
    assert len(ids) >= 2                                       # gráfico + foto
    assert set(refs) == set(ids)                               # toda referência aponta para uma imagem
    assert len(refs) > len(ids)                                # mesma figura nas 3 páginas: reaproveitada
    for img in r.imagens:
        pix = pymupdf.Pixmap(img.dados)
        assert (pix.width, pix.height) == (img.largura, img.altura)
        assert min(pix.width, pix.height) >= conversor.LADO_MINIMO_PX
        assert max(pix.width, pix.height) <= conversor.LADO_MAXIMO_PX


def test_texto_titulos_e_sem_cabecalho_rodape(pdf_com_grafico):
    md = conversor.converter(pdf_com_grafico).markdown

    assert "# Capitulo 1 Fotossintese" in md and "# Capitulo 3 Fotossintese" in md
    assert "converte luz em energia quimica" in md
    assert "Apostila de Biologia" not in md                    # cabeçalho
    assert "100 lx" in md                                       # rótulos do gráfico viram texto
    assert not re.search(r"^\s*[123]\s*$", md, re.MULTILINE)   # número de página


def test_pdf_invalido():
    import pytest
    with pytest.raises(conversor.PdfInvalido):
        conversor.converter(b"%PDF-1.7 isto nao e um pdf")
