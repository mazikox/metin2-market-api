#!/bin/sh
set -eu
# Archive the live journal first. Keep 89 days of archives plus at most
# one daily interval: requests are kept for no more than about 90 days.
/usr/bin/journalctl --rotate
/usr/bin/journalctl --vacuum-time=89days
/usr/bin/docker exec -i umami-db-1 sh -c \
  'exec psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"' \
  < /etc/metin2bazar/umami-prune.sql
