#!/bin/bash
# Limpeza mensal dos backups no Backblaze B2. Roda NO MAC, nunca na VPS:
# a chave da VPS não pode apagar (é o que protege os backups de quem invadir o servidor).
#
#   deploy/limpar-b2.sh
#
# Precisa, no Mac (fora do git, permissão 600):
#   ~/.studiy-b2-limpeza.env  com B2_ACCOUNT_ID e B2_ACCOUNT_KEY da chave studiy-mac-limpeza
#                             (essa tem deleteFiles) e B2_BUCKET=studiy-backups-novaes
#   ~/.studiy-restic-senha    a mesma senha do restic da VPS (~/.restic-senha)
# e o restic instalado (brew install restic).

set -euo pipefail

ENV="${LIMPEZA_ENV:-$HOME/.studiy-b2-limpeza.env}"
SENHA="${LIMPEZA_SENHA:-$HOME/.studiy-restic-senha}"
for f in "$ENV" "$SENHA"; do
  [ -r "$f" ] || { echo "Falta $f (veja o cabeçalho deste script)." >&2; exit 1; }
done

set -a; . "$ENV"; set +a
export AWS_ACCESS_KEY_ID="$B2_ACCOUNT_ID" AWS_SECRET_ACCESS_KEY="$B2_ACCOUNT_KEY" RESTIC_PASSWORD_FILE="$SENHA"
export RESTIC_REPOSITORY="s3:${B2_S3_ENDPOINT:-https://s3.us-east-005.backblazeb2.com}/$B2_BUCKET/studiy"

# Retenção: 14 diários, 8 semanais e 12 mensais (um ano de histórico em poucos MB).
# Pelo S3, o B2 "esconde" o que o restic apaga; a regra do bucket (daysFromHidingToDeleting: 30)
# remove de vez depois de 30 dias. Até lá, um backup escondido por engano ainda pode ser recuperado.
restic forget --host studiy-vps --tag postgres --keep-daily 14 --keep-weekly 8 --keep-monthly 12 --prune
# Confere a integridade do repositório depois de apagar.
restic check
restic snapshots --compact | tail -5
