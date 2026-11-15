#!/usr/bin/env python3
"""Gerador de carga do Studiy (Python + httpx). Só contra o SEU ambiente de teste.

  carga.py registrar --n 1000        cadastra contas carga0001@carga.test ... (senha em CARGA_SENHA)
  carga.py rodar --vus 200 --duracao 120   cada usuário virtual: login, apelido, matéria, upload de PDF
                                           pequeno, gerar flashcards (IA de teste), revisar, atividade, ranking

As contas precisam de e-mail confirmado para gerar (sem SMTP, confirme em lote no banco). A senha nunca é
impressa. Alvos fora de *.sslip.io / localhost são recusados, salvo --permitir-host.
"""
from __future__ import annotations

import argparse
import asyncio
import json
import os
import random
import statistics
import sys
import textwrap
import time
from collections import defaultdict
from urllib.parse import urlparse

import httpx

SENHA = os.environ.get("CARGA_SENHA", "")
metricas: dict[str, list[tuple[int, float]]] = defaultdict(list)   # rota -> [(status, segundos)]


def email(n: int) -> str:
    return f"carga{n:04d}@carga.test"


def pdf_minimo(n: int) -> bytes:
    """PDF de 1 página, só texto, único por usuário (o hash do conteúdo difere entre contas)."""
    texto = (f"Apostila de teste de carga numero {n}. A memoria de longo prazo depende da repeticao espacada. "
             f"O intervalo entre revisoes cresce quando o aluno acerta e volta ao inicio quando erra. "
             f"Exemplo {n}: revisar hoje, depois de 1 dia, de 3 dias e de 7 dias melhora a retencao. ") * 3
    linhas = textwrap.wrap(texto, 85)[:45]
    esc = lambda s: s.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)")
    fluxo = "BT /F1 11 Tf 56 780 Td 14 TL " + " ".join(f"({esc(l)}) Tj T*" for l in linhas) + " ET"
    objs = ["<< /Type /Catalog /Pages 2 0 R >>", "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
            "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Contents 5 0 R /Resources << /Font << /F1 4 0 R >> >> >>",
            "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
            f"<< /Length {len(fluxo)} >>\nstream\n{fluxo}\nendstream"]
    saida, offsets = b"%PDF-1.4\n", []
    for i, o in enumerate(objs, 1):
        offsets.append(len(saida))
        saida += f"{i} 0 obj\n{o}\nendobj\n".encode("latin-1")
    xref = len(saida)
    saida += f"xref\n0 {len(objs) + 1}\n0000000000 65535 f \n".encode()
    saida += b"".join(f"{o:010d} 00000 n \n".encode() for o in offsets)
    return saida + f"trailer\n<< /Size {len(objs) + 1} /Root 1 0 R >>\nstartxref\n{xref}\n%%EOF\n".encode()


class Usuario:
    def __init__(self, base: str, n: int, timeout: float):
        self.n, self.base = n, base
        self.http = httpx.AsyncClient(base_url=base, timeout=timeout, follow_redirects=False,
                                      limits=httpx.Limits(max_connections=4))

    async def req(self, rota: str, metodo: str, caminho: str, **kw):
        for tentativa in (1, 2):
            cab = dict(kw.pop("headers", {}))
            if metodo != "GET":
                tok = self.http.cookies.get("XSRF-TOKEN")
                if not tok:
                    await self.http.get("/api/auth/csrf"); tok = self.http.cookies.get("XSRF-TOKEN")
                if tok: cab["X-XSRF-TOKEN"] = tok
            t = time.perf_counter()
            try:
                r = await self.http.request(metodo, caminho, headers=cab, **kw)
                status = r.status_code
            except httpx.HTTPError:
                r, status = None, 0
            metricas[rota].append((status, time.perf_counter() - t))
            if status == 401 and tentativa == 1 and rota != "login":
                await self.http.post("/api/auth/refresh", headers={"X-XSRF-TOKEN": self.http.cookies.get("XSRF-TOKEN") or ""})
                continue
            return r
        return r

    async def fechar(self):
        await self.http.aclose()


async def registrar(args):
    sem = asyncio.Semaphore(args.concorrencia)

    async def um(n: int):
        async with sem:
            u = Usuario(args.base, n, args.timeout)
            try:
                await u.http.get("/api/auth/csrf")
                for _ in range(6):
                    r = await u.req("registrar", "POST", "/api/auth/registrar",
                                    json={"nome": f"Estudante Carga {n}", "email": email(n), "senha": SENHA, "aceitoTermos": True})
                    if r is not None and r.status_code == 429:
                        await asyncio.sleep(float(r.headers.get("Retry-After", "5"))); continue
                    break
            finally:
                await u.fechar()

    await asyncio.gather(*(um(n) for n in range(args.inicio, args.inicio + args.n)))


async def vu(args, n: int, fim: float, atraso: float):
    await asyncio.sleep(atraso)
    u = Usuario(args.base, n, args.timeout)
    pensar = lambda: asyncio.sleep(random.uniform(args.pensar_min, args.pensar_max))
    try:
        await u.http.get("/api/auth/csrf")
        r = await u.req("login", "POST", "/api/auth/login", json={"email": email(n), "senha": SENHA})
        if r is None or r.status_code != 200:
            return
        await u.req("apelido", "PUT", "/api/usuarios/me/apelido", json={"apelido": f"estudante{n:04d}"})
        # Quem já tem matéria (rodada anterior) só volta a estudar; quem não tem faz a configuração inicial.
        r = await u.req("materias_listar", "GET", "/api/materias")
        existentes = r.json() if r is not None and r.status_code == 200 else []
        if existentes and not args.forcar_upload:
            materia = existentes[0]["id"]
        else:
            if existentes:
                materia = existentes[0]["id"]
            else:
                r = await u.req("materia_criar", "POST", "/api/materias", json={"nome": f"Revisao {n}", "cor": "verde"})
                if r is None or r.status_code >= 300:
                    return
                materia = r.json()["id"]
            r = await u.req("upload_pdf", "POST", f"/api/materias/{materia}/pdfs",
                            files={"arquivo": (f"apostila-{n}.pdf", pdf_minimo(n + (random.randint(1, 10**6) if args.forcar_upload else 0)), "application/pdf")})
            pdf = r.json()["id"] if r is not None and r.status_code == 200 else None
            if pdf:
                await u.req("gerar_flashcards", "POST", f"/api/materias/{materia}/gerar", json={"pdfId": pdf, "tipo": "FLASHCARD"})

        while time.monotonic() < fim:
            acao = random.choices(["painel", "revisar", "atividade", "ranking"], weights=[4, 5, 2, 3])[0]
            if acao == "painel":
                await u.req("me", "GET", "/api/usuarios/me")
                await u.req("materias_listar", "GET", "/api/materias")
            elif acao == "revisar":
                r = await u.req("revisao_listar", "GET", f"/api/materias/{materia}/flashcards/revisao")
                cartoes = (r.json().get("cartoes") or []) if r is not None and r.status_code == 200 else []
                for c in cartoes[: random.randint(1, 4)]:
                    await u.req("revisar", "POST", f"/api/materias/{materia}/flashcards/{c['id']}/revisar",
                                json={"resposta": random.choice(["ERREI", "DIFICIL", "BOM", "FACIL"])})
                    await asyncio.sleep(random.uniform(0.2, 0.8))
            elif acao == "atividade":
                await u.req("atividade", "POST", f"/api/materias/{materia}/atividades", json={"titulo": f"Exercicio {random.randint(1, 999)}"})
            else:
                await u.req("ranking", "GET", f"/api/ranking?tipo={random.choice(['SEMANA', 'XP', 'SEQUENCIA'])}")
            await pensar()
    finally:
        await u.fechar()


async def rodar(args):
    fim = time.monotonic() + args.rampa + args.duracao
    resultados = await asyncio.gather(*(vu(args, args.inicio + i, fim, args.rampa * i / max(1, args.vus))
                                        for i in range(args.vus)), return_exceptions=True)
    quebrados = [r for r in resultados if isinstance(r, Exception)]
    if quebrados:  # um erro do gerador não pode passar por 'servidor saudável'
        print(f"ATENCAO: {len(quebrados)} usuarios virtuais terminaram com erro do script, ex.: {quebrados[0]!r}")


def resumo(args, dt: float):
    linhas, total, erros = [], 0, 0
    for rota, v in sorted(metricas.items()):
        lat = sorted(x for _, x in v); st = [s for s, _ in v]
        ruins = sum(1 for s in st if s == 0 or s >= 500)
        limit = sum(1 for s in st if s == 429)
        cli = sum(1 for s in st if 400 <= s < 500 and s != 429)
        total += len(v); erros += ruins
        p = lambda q: lat[min(len(lat) - 1, int(q * len(lat)))] * 1000
        linhas.append((rota, len(v), p(0.5), p(0.95), p(0.99), ruins, limit, cli))
    print(f"\n{'rota':<18}{'reqs':>7}{'p50 ms':>9}{'p95 ms':>9}{'p99 ms':>9}{'5xx/fal':>9}{'429':>6}{'4xx':>6}")
    for l in linhas:
        print(f"{l[0]:<18}{l[1]:>7}{l[2]:>9.0f}{l[3]:>9.0f}{l[4]:>9.0f}{l[5]:>9}{l[6]:>6}{l[7]:>6}")
    print(f"\nTOTAL {total} reqs em {dt:.0f}s = {total / max(dt, 1):.1f} req/s | falhas (5xx/rede): {erros} ({100 * erros / max(total, 1):.2f}%)")
    if args.saida:
        json.dump({r: [(s, round(t, 4)) for s, t in v] for r, v in metricas.items()}, open(args.saida, "w"))


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("modo", choices=["registrar", "rodar"])
    ap.add_argument("--base", default="https://161-97-97-133.sslip.io")
    ap.add_argument("--n", type=int, default=10, help="registrar: quantas contas")
    ap.add_argument("--inicio", type=int, default=1, help="número da primeira conta")
    ap.add_argument("--concorrencia", type=int, default=20, help="registrar: cadastros em paralelo")
    ap.add_argument("--vus", type=int, default=10, help="rodar: usuários virtuais simultâneos")
    ap.add_argument("--duracao", type=int, default=60, help="rodar: segundos após a rampa")
    ap.add_argument("--rampa", type=int, default=10, help="rodar: segundos para subir todos os usuários")
    ap.add_argument("--forcar-upload", action="store_true", help="rodar: sobe um PDF novo mesmo quem já tem matéria")
    ap.add_argument("--pensar-min", type=float, default=0.5)
    ap.add_argument("--pensar-max", type=float, default=3.0)
    ap.add_argument("--timeout", type=float, default=60)
    ap.add_argument("--saida", help="grava as medições brutas em JSON")
    ap.add_argument("--permitir-host", action="store_true", help="aceita alvo fora de *.sslip.io e localhost")
    args = ap.parse_args()

    host = urlparse(args.base).hostname or ""
    if not (host.endswith(".sslip.io") or host in ("localhost", "127.0.0.1")) and not args.permitir_host:
        sys.exit(f"Recusado: {host} não é *.sslip.io nem localhost. Só gere carga no SEU ambiente (--permitir-host).")
    if not SENHA or len(SENHA) < 8:
        sys.exit("Defina CARGA_SENHA (8+ caracteres) no ambiente. Ela não é impressa.")

    t = time.perf_counter()
    asyncio.run(registrar(args) if args.modo == "registrar" else rodar(args))
    resumo(args, time.perf_counter() - t)


if __name__ == "__main__":
    main()
