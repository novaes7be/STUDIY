# Teste de carga

`carga.py` gera usuários virtuais (Python + `httpx`, sem instalar nada além do que o extrator já usa) contra o
**seu** ambiente de teste. Recusa alvos que não sejam `*.sslip.io` ou `localhost` (use `--permitir-host` por sua conta).

```bash
export CARGA_SENHA="$(openssl rand -base64 18 | tr -d '/+=\n')A9"     # senha das contas fictícias, não é impressa
pdf-extrator/.venv/bin/python loadtest/carga.py registrar --n 1000 --concorrencia 40
pdf-extrator/.venv/bin/python loadtest/carga.py rodar --vus 200 --duracao 120 --rampa 30
```

Cada usuário virtual: login, apelido (entra no ranking), matéria, upload de um PDF pequeno, gerar flashcards
(sem a chave da Anthropic a IA devolve resposta de teste, custo zero) e depois, em laço: painel, revisar
flashcards (+XP), atividade e ranking, com pausa de 0,5 a 3 s entre ações.

## Antes de rodar
- **Limite de requisições:** 10/min por IP em cadastro/login/upload. De um só computador isso mascara a
  capacidade. Para medir, suba `STUDIY_RATELIMIT_AUTH_POR_MINUTO`, `..._IA_...` e `..._UPLOAD_...` no `.env`
  **só durante o teste** e restaure depois.
- **E-mail confirmado:** sem SMTP as contas nascem não confirmadas e não geram com IA. Confirme em lote:
  `update usuarios set email_verificado = true where id > <último id antes do cadastro>`.
- `ulimit -n 8192` antes de subir 1.000 usuários simultâneos (o padrão do macOS é 256).
- A tabela mostra 5xx/falhas de rede, 429 (limite) e 4xx (erro do cliente) separados: um 4xx não é "servidor saudável".

## Limpeza
As contas ficam no ranking. Para remover: `delete from usuarios where id > <último id antes do cadastro>`.
