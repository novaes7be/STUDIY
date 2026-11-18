#!/bin/bash
# Backup do Postgres do studiy: um dump por execução em ~/backups, e os antigos são apagados.
#   deploy/backup.sh
# Na VPS roda pelo cron do usuário deploy (crontab -e):
#   30 3 * * * /home/deploy/STUDIY/deploy/backup.sh >> /home/deploy/backups/backup.log 2>&1

set -euo pipefail

DESTINO="${BACKUP_DIR:-$HOME/backups}"
CONTAINER="${DB_CONTAINER:-studiy-db-1}"

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
