#!/bin/sh
set -eu
cd "${METIN_MARKET_API_DIR:-/home/debian/metin2-market-api}"
exec /usr/bin/docker compose exec -T postgres psql -v ON_ERROR_STOP=1 -U metin_market -d metin_market < /etc/metin2bazar/analytics-prune.sql
