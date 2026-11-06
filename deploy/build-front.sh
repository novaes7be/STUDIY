#!/usr/bin/env bash
# Gera o front por ambiente em build/front-<ambiente>/:
#  - troca __API_ORIGIN__ (connect-src da CSP) e __API_BASE__ (js/config.js);
#  - em prod, só aceita API na mesma origem (vazio) ou em HTTPS, e falha se sobrar localhost/http.
#
# Uso:
#   deploy/build-front.sh dev                          # API em http://localhost:8080
#   deploy/build-front.sh prod                         # API na mesma origem (Caddy encaminha /api)
#   API_ORIGIN=https://api.exemplo.com deploy/build-front.sh prod
set -euo pipefail

AMBIENTE="${1:-}"
RAIZ="$(cd "$(dirname "$0")/.." && pwd)"
ORIGEM="$RAIZ/frontend"
DESTINO="$RAIZ/build/front-$AMBIENTE"

case "$AMBIENTE" in
  dev)  API="${API_ORIGIN-http://localhost:8080}" ;;
  prod) API="${API_ORIGIN-}"
        if [[ -n "$API" && ! "$API" =~ ^https://[A-Za-z0-9.-]+(:[0-9]+)?$ ]]; then
          echo "ERRO: em prod API_ORIGIN deve ser vazio (mesma origem) ou https://host[:porta], sem barra no fim." >&2
          exit 1
        fi ;;
  *) echo "Uso: $0 dev|prod" >&2; exit 1 ;;
esac

rm -rf "$DESTINO"
mkdir -p "$DESTINO"
cp -R "$ORIGEM"/. "$DESTINO"/

# Valores vão para atributo HTML e string JS: só caracteres de URL simples (validado acima em prod).
if [[ ! "$API" =~ ^[A-Za-z0-9:/.-]*$ ]]; then
  echo "ERRO: API_ORIGIN contém caracteres inválidos." >&2
  exit 1
fi

for f in "$DESTINO"/*.html; do
  sed -i.bak "s#__API_ORIGIN__#${API}#g" "$f" && rm -f "$f.bak"
done
sed -i.bak "s#__API_BASE__#${API}#g" "$DESTINO/js/config.js" && rm -f "$DESTINO/js/config.js.bak"

# Conferências: nenhum placeholder sobrando; em prod, nada de localhost/http nem fontes de terceiros.
if grep -rl "__API_" "$DESTINO" >/dev/null; then
  echo "ERRO: placeholder não substituído:" >&2; grep -rl "__API_" "$DESTINO" >&2; exit 1
fi
if [[ "$AMBIENTE" == "prod" ]]; then
  problemas="$( { grep -HnoE "connect-src[^;\"]*(http://|localhost|127\.0\.0\.1)[^;\"]*" "$DESTINO"/*.html || true
                  grep -HnE "var base = '(http://|[^']*localhost|[^']*127\.0\.0\.1)" "$DESTINO"/js/config.js || true
                  grep -rHnE "fonts\.googleapis|fonts\.gstatic" "$DESTINO"/*.html "$DESTINO"/css "$DESTINO"/js || true; } )"
  if [[ -n "$problemas" ]]; then
    echo "ERRO: o build de produção aponta para HTTP/localhost ou fontes externas:" >&2
    echo "$problemas" >&2
    exit 1
  fi
fi

echo "Front ($AMBIENTE) gerado em build/front-$AMBIENTE (API: ${API:-mesma origem})"
