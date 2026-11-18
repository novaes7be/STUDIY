#!/bin/bash
# Publica a main em produção.
#   À mão, na VPS:        ~/STUDIY/deploy/deploy.sh
#   Pelo GitHub Actions:  ssh deploy@VPS <sha>. A chave do Actions só consegue rodar este script
#                         (restrict,command="/home/deploy/STUDIY/deploy/deploy.sh" no authorized_keys).
# Com <sha>, só publica se ele ainda for a ponta da main: se chegou um commit mais novo,
# o CI dele dispara outro deploy, e nada sobe sem ter passado no CI.

set -euo pipefail
cd "$(dirname "$0")/.."

alvo="${1:-${SSH_ORIGINAL_COMMAND:-}}"
if [ -n "$alvo" ] && ! [[ "$alvo" =~ ^[0-9a-f]{40}$ ]]; then
  echo "uso: deploy.sh [sha completo da main]" >&2
  exit 2
fi

if [ "${DEPLOY_ATUALIZADO:-}" != 1 ]; then
  git fetch -q origin main
  git checkout -q main
  git merge -q --ff-only origin/main
  DEPLOY_ATUALIZADO=1 exec "$0" "$alvo"   # segue com a versão recém-baixada deste script
fi

ponta=$(git rev-parse HEAD)
if [ -n "$alvo" ] && [ "$alvo" != "$ponta" ]; then
  echo "A main já está em ${ponta:0:7} (pedido: ${alvo:0:7}). O CI do commit mais novo publica; nada a fazer."
  exit 0
fi

echo "== publicando ${ponta:0:7}: $(git log -1 --format=%s)"
deploy/backup.sh
docker compose build -q
docker compose up -d --remove-orphans

# Sem login, a rota do admin responde 401: é o sinal de que a API subiu e o Caddy chega nela.
url=$(grep -E '^FRONT_URL=' .env | cut -d= -f2-)
for _ in $(seq 1 60); do
  if [ "$(curl -s -o /dev/null -w '%{http_code}' "$url/api/admin/metricas" || true)" = 401 ]; then
    echo "== no ar: $url"
    docker compose ps --format '{{.Name}} {{.Status}}'
    exit 0
  fi
  sleep 3
done
echo "ERRO: a API não respondeu em 3 minutos" >&2
docker compose logs --tail 40 api >&2
exit 1
