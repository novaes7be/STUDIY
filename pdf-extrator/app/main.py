"""API interna do extrator: só a API Java do Studiy chama, pela rede interna do Docker.

POST /converter (multipart, campo "arquivo", cabeçalho X-Extrator-Token) -> Markdown + figuras.
A conversão roda num processo separado, com tempo máximo: um PDF malicioso ou pesado demais
derruba só o processo de trabalho, não o serviço.
"""

from __future__ import annotations

import asyncio
import hmac
import logging
import os
from concurrent.futures import ProcessPoolExecutor
from concurrent.futures.process import BrokenProcessPool
from contextlib import asynccontextmanager

from fastapi import FastAPI, File, Header, HTTPException, UploadFile

from app import conversor

log = logging.getLogger("extrator")

TAMANHO_MAXIMO = 10 * 1024 * 1024
TEMPO_MAXIMO_S = float(os.environ.get("EXTRATOR_TEMPO_MAXIMO_S", "120"))
PROCESSOS = int(os.environ.get("EXTRATOR_PROCESSOS", "2"))


def _token() -> str:
    token = os.environ.get("EXTRATOR_TOKEN", "")
    if len(token) < 16:
        raise RuntimeError("Defina EXTRATOR_TOKEN (16+ caracteres): o extrator não roda sem autenticação.")
    return token


def _novo_pool() -> ProcessPoolExecutor:
    # Cada processo atende um PDF e é trocado: memória de um PDF grande não se acumula.
    return ProcessPoolExecutor(max_workers=PROCESSOS, max_tasks_per_child=1)


@asynccontextmanager
async def ciclo_de_vida(app: FastAPI):
    app.state.token = _token()
    app.state.pool = _novo_pool()
    yield
    app.state.pool.shutdown(wait=True, cancel_futures=True)


app = FastAPI(title="Studiy extrator de PDF", lifespan=ciclo_de_vida, docs_url=None, redoc_url=None, openapi_url=None)


def _converter_em_processo(dados: bytes) -> dict | tuple[str, str]:
    """Roda no processo filho. Erros viram tupla (tipo, mensagem): exceções nem sempre atravessam o pickle."""
    try:
        return conversor.converter(dados).para_json()
    except conversor.PdfInvalido as e:
        return ("invalido", str(e))


@app.get("/saude")
def saude() -> dict:
    return {"ok": True}


@app.post("/converter")
async def converter(arquivo: UploadFile = File(...), x_extrator_token: str = Header(default="")) -> dict:
    if not hmac.compare_digest(x_extrator_token.encode(), app.state.token.encode()):
        raise HTTPException(status_code=401, detail="token inválido")

    dados = await arquivo.read(TAMANHO_MAXIMO + 1)
    if len(dados) > TAMANHO_MAXIMO:
        raise HTTPException(status_code=413, detail="PDF muito grande (máximo 10MB)")
    if not dados.startswith(b"%PDF-"):
        raise HTTPException(status_code=400, detail="Só são aceitos arquivos PDF")

    loop = asyncio.get_running_loop()
    try:
        resultado = await asyncio.wait_for(
            loop.run_in_executor(app.state.pool, _converter_em_processo, dados), TEMPO_MAXIMO_S)
    except asyncio.TimeoutError:
        _reiniciar_pool()
        raise HTTPException(status_code=504, detail="Conversão demorou demais")
    except BrokenProcessPool:
        _reiniciar_pool()
        log.warning("processo de conversão caiu (PDF problemático?)")
        raise HTTPException(status_code=422, detail="Não foi possível ler o PDF")

    if isinstance(resultado, tuple):
        raise HTTPException(status_code=422, detail=resultado[1])
    return resultado


def _reiniciar_pool() -> None:
    """Mata os processos presos e começa outro pool; o serviço continua atendendo."""
    antigo = app.state.pool
    app.state.pool = _novo_pool()
    for p in list(getattr(antigo, "_processes", {}).values()):
        p.kill()
    antigo.shutdown(wait=False, cancel_futures=True)
