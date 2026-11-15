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


def test_pool_reaproveita_os_processos_em_vez_de_abrir_um_por_pdf():
    """Antes: max_tasks_per_child=1, cada PDF abria um interpretador e reimportava tudo (~2 s de CPU)."""
    from app import main, trabalho

    pool = main._novo_pool()
    try:
        pids = [pool.submit(trabalho.aquecer).result(timeout=120) for _ in range(8)]
    finally:
        pool.shutdown(wait=True, cancel_futures=True)

    assert main.PDFS_POR_PROCESSO > 1
    assert len(set(pids)) <= main.PROCESSOS, f"processos usados: {len(set(pids))}"


def test_worker_nao_importa_o_fastapi():
    """O módulo que o processo filho importa deve ser leve: nada de FastAPI/uvicorn dentro dos workers."""
    import subprocess
    import sys

    codigo = "import sys; import app.trabalho; print('fastapi' in sys.modules)"
    saida = subprocess.run([sys.executable, "-c", codigo], capture_output=True, text=True, timeout=120,
                           env={**__import__('os').environ, "EXTRATOR_TOKEN": "token-de-teste-0123456789"})
    assert saida.stdout.strip().endswith("False"), saida.stderr[-300:]
