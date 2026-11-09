from fastapi.testclient import TestClient

from app.main import app

TOKEN = {"X-Extrator-Token": "token-de-teste-0123456789"}


def test_exige_token(pdf_com_grafico):
    with TestClient(app) as c:
        r = c.post("/converter", files={"arquivo": ("a.pdf", pdf_com_grafico, "application/pdf")})
        assert r.status_code == 401


def test_recusa_o_que_nao_e_pdf():
    with TestClient(app) as c:
        r = c.post("/converter", headers=TOKEN, files={"arquivo": ("a.pdf", b"oi", "application/pdf")})
        assert r.status_code == 400


def test_converte(pdf_com_grafico):
    with TestClient(app) as c:
        r = c.post("/converter", headers=TOKEN, files={"arquivo": ("a.pdf", pdf_com_grafico, "application/pdf")})
        assert r.status_code == 200
        corpo = r.json()
        assert corpo["paginas"] == 3
        assert corpo["imagens"] and corpo["imagens"][0]["base64"]
        assert "imagem:1" in corpo["markdown"]
