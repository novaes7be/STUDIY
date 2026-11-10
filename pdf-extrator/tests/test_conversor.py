import re

import pymupdf
import pytest

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


# ---- modo híbrido ----

def _abrir(pdf: bytes):
    return pymupdf.open(stream=pdf, filetype="pdf")


def test_deteccao_acha_so_as_paginas_com_foto_ou_grafico(pdf_misto):
    with _abrir(pdf_misto) as doc:
        assert conversor.paginas_com_grafico(doc) == [1, 4]          # páginas 2 e 5


def test_deteccao_ignora_texto_puro_logo_repetido_e_fundo(pdf_so_texto, pdf_com_logo_em_toda_pagina):
    with _abrir(pdf_so_texto) as doc:
        assert conversor.paginas_com_grafico(doc) == []
    with _abrir(pdf_com_logo_em_toda_pagina) as doc:
        assert conversor.paginas_com_grafico(doc) == []


def test_pdf_so_de_texto_nao_usa_o_modelo_de_layout(pdf_so_texto):
    r = conversor.converter(pdf_so_texto)

    assert r.paginas_layout == 0 and r.motor().endswith("híbrido 0/6")
    assert r.imagens == [] and "data:image" not in r.markdown
    assert "# Camada 1 de transporte" in r.markdown and "# Camada 6 de transporte" in r.markdown
    assert "Apostila de Redes" not in r.markdown                      # cabeçalho repetido
    assert not re.search(r"Página \d+ de 6", r.markdown)              # rodapé
    assert "entrega os segmentos na ordem certa e confirma o recebimento" in r.markdown  # parágrafo sem quebra no meio


def test_hibrido_mantem_o_texto_das_paginas_sem_figura_e_chama_layout_so_nas_com_figura(pdf_misto, monkeypatch):
    chamadas = []
    original = conversor._markdown_com_layout
    monkeypatch.setattr(conversor, "_markdown_com_layout",
                        lambda doc, paginas, **kw: chamadas.append(paginas) or original(doc, paginas, **kw))

    r = conversor.converter(pdf_misto)

    assert chamadas == [[1, 4]]
    assert r.paginas_layout == 2 and r.motor().endswith("híbrido 2/6")
    for n in range(1, 7):
        assert f"# Camada {n} de transporte" in r.markdown          # nenhuma página some
    assert r.markdown.index("Camada 1 de") < r.markdown.index("Camada 4 de") < r.markdown.index("Camada 6 de")


def test_teto_de_paginas_com_layout_nao_vaza_base64(pdf_misto, monkeypatch):
    monkeypatch.setattr(conversor, "LIMITE_PAGINAS_LAYOUT", 1)

    r = conversor.converter(pdf_misto)

    assert r.paginas_layout == 1 and r.motor().endswith("híbrido 1/6")
    assert "data:image" not in r.markdown
    for n in range(1, 7):
        assert f"# Camada {n} de transporte" in r.markdown


def test_modo_layout_completo_continua_disponivel(pdf_misto, monkeypatch):
    monkeypatch.setattr(conversor, "MODO", "layout")

    r = conversor.converter(pdf_misto)

    assert r.paginas_layout is None and "híbrido" not in r.motor()
    assert r.para_json()["paginasLayout"] is None


def test_hibrido_com_o_modelo_real_devolve_as_figuras(pdf_misto):
    pytest.importorskip("pymupdf_layout")        # só onde o modelo está instalado (CI e imagem Docker)
    r = conversor.converter(pdf_misto)

    assert len(r.imagens) >= 1
    assert {int(n) for n in re.findall(r"imagem:(\d+)", r.markdown)} == {i.id for i in r.imagens}


def test_juntar_linhas_une_texto_corrido_mas_preserva_marcadores_de_slide():
    texto = "O diagrama mostra\nas classes do sistema\ne seus atributos.\n\n❑ Nome da classe\n❑ atributos\n❑ métodos"

    juntado = conversor._juntar_linhas(texto)

    assert "O diagrama mostra as classes do sistema e seus atributos." in juntado
    assert "❑ Nome da classe\n❑ atributos\n❑ métodos" in juntado      # lista não vira uma linha só


def test_juntar_linhas_preserva_titulo_tabela_e_numeracao():
    for bloco in ["# Titulo\nTexto", "| a | b |\n| - | - |", "1. primeiro\n2. segundo", "> citação\nlinha"]:
        assert conversor._juntar_linhas(bloco) == bloco
