"""Código que roda DENTRO dos processos de conversão.

Fica fora de ``main.py`` de propósito: com ``max_tasks_per_child`` o Python usa ``spawn``, e cada processo novo
importa o módulo da função que vai rodar. Em ``main.py`` isso arrastaria o FastAPI inteiro; aqui só entra o
conversor (PyMuPDF + modelo de layout), que já é o custo mínimo.
"""

from __future__ import annotations

from app import conversor


def aquecer() -> int:
    """Força o processo a importar tudo agora, antes do primeiro PDF, e devolve o PID (para os testes)."""
    import os
    return os.getpid()


def converter_em_processo(dados: bytes) -> dict | tuple[str, str]:
    """Erros viram tupla (tipo, mensagem): exceções nem sempre atravessam o pickle."""
    try:
        return conversor.converter(dados).para_json()
    except conversor.PdfInvalido as e:
        return ("invalido", str(e))
