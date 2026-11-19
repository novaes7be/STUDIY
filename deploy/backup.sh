#!/bin/bash
# Backup do Postgres do studiy: um dump por execução em ~/backups, e os antigos são apagados.
#   deploy/backup.sh
# Na VPS roda pelo cron do usuário deploy (crontab -e):
#   30 3 * * * /home/deploy/STUDIY/deploy/backup.sh >> /home/deploy/backups/backup.log 2>&1

set -euo pipefail

DESTINO="${BACKUP_DIR:-$HOME/backups}"
CONTAINER="${DB_CONTAINER:-studiy-db-1}"
B2_ENV="${BACKUP_B2_ENV:-$HOME/.backup-b2.env}"

# Healthchecks.io (HC_URL=https://hc-ping.com/... em ~/.backup-b2.env): avisa no início e manda o código
# de saída no fim (0 = ok, outro = falha). Se nenhum aviso chegar no horário, ele também alerta.
# Um problema no aviso nunca derruba o backup.
HC_URL="${HC_URL:-$(grep -sE '^HC_URL=' "$B2_ENV" | cut -d= -f2- || true)}"
avisar() { [ -z "$HC_URL" ] || curl -fsS -m 10 --retry 3 -o /dev/null "$HC_URL$1" || true; }
avisar /start
trap 'avisar "/$?"' EXIT

install -d -m 700 "$DESTINO"
arquivo="$DESTINO/studiy-$(date +%F-%H%M%S).dump"

# pg_dump de dentro do container: mesma versão do servidor, sem senha (socket local).
# Formato custom (-Fc): já vem comprimido e o pg_restore escolhe o que restaurar.
docker exec "$CONTAINER" sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc' > "$arquivo.tmp"

# Um dump que o pg_restore não consegue listar está corrompido: falha aqui em vez de guardar lixo.
docker exec -i "$CONTAINER" pg_restore --list < "$arquivo.tmp" > /dev/null
mv "$arquivo.tmp" "$arquivo"
chmod 600 "$arquivo"
echo "$(date '+%F %T') ok $(du -h "$arquivo" | cut -f1) $arquivo"

# Rotação por quantidade, não por idade: se o cron parar por dias, os últimos dumps bons continuam aqui.
# Fica com os MANTER mais recentes (padrão 14: duas semanas para perceber um dado errado).
MANTER="${BACKUP_MANTER:-14}"
ls -1t "$DESTINO"/studiy-*.dump | tail -n +"$((MANTER + 1))" | xargs -r rm -f --

# Cópia fora da VPS: restic (criptografado) no Backblaze B2, se ~/.backup-b2.env e ~/.restic-senha existem.
# Uma falha aqui não apaga nada local: sai com código 3 para o cron/log acusar, e o deploy segue.
SENHA="${BACKUP_RESTIC_SENHA:-$HOME/.restic-senha}"
if [ -r "$B2_ENV" ] && [ -r "$SENHA" ]; then
  set -a; . "$B2_ENV"; set +a
  export RESTIC_REPOSITORY="b2:$B2_BUCKET:studiy" RESTIC_PASSWORD_FILE="$SENHA"
  # Retenção no B2: 14 diários, 8 semanais e 12 mensais (um ano de histórico em poucos MB).
  if restic backup -q --host studiy-vps --tag postgres "$arquivo" \
     && restic forget -q --host studiy-vps --tag postgres --keep-daily 14 --keep-weekly 8 --keep-monthly 12 --prune; then
    echo "$(date '+%F %T') ok b2 $RESTIC_REPOSITORY"
  else
    echo "$(date '+%F %T') ERRO b2: dump local guardado, envio ao B2 falhou" >&2
    exit 3
  fi
fi
