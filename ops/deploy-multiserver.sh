#!/usr/bin/env bash
set -Eeuo pipefail

cd "$HOME/metin2-market-api"

timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
backup_dir="$HOME/metin-market-db-backups"
backup_file="$backup_dir/metin_market_before_multiserver_${timestamp}.dump"
env_file="$HOME/metin2-market-api/.env"
env_backup_file="$backup_dir/metin_market_env_before_multiserver_${timestamp}.env"
rollback_tag="local/metin-market-api-before-multiserver:${timestamp}"
old_image_ref=""
migration_completed=0

rollback_on_error() {
  local status="${1:-$?}"
  trap - ERR
  echo "Deployment failed; restoring the previous API/database layout." >&2

  if [ "$migration_completed" -eq 1 ]; then
    sudo docker compose stop api || true
    if ! sudo docker compose exec -T postgres psql -U metin_market -d metin_market \
      -v ON_ERROR_STOP=1 < ops/rollback-pandora-to-public.sql; then
      echo "Automatic SQL rollback failed. Preserve the verified backup at $backup_file." >&2
    fi
  fi

  if [ -n "$old_image_ref" ]; then
    sudo docker image tag "$rollback_tag" "$old_image_ref" || true
    sudo docker compose up -d --no-build api || true
  fi

  exit "$status"
}
trap 'rollback_on_error $?' ERR

echo "Checking the production environment file and scanner credentials."
if [ ! -f "$env_file" ] || [ -L "$env_file" ]; then
  echo "Expected a regular .env file at $env_file." >&2
  exit 1
fi
mkdir -p "$backup_dir"
chmod 700 "$backup_dir"
umask 077
cp "$env_file" "$env_backup_file"
chmod 600 "$env_backup_file"

ensure_scanner_token() {
  local key="$1"
  if ! grep -qE "^${key}=[^[:space:]]+$" "$env_file"; then
    sed -i "/^${key}=/d" "$env_file"
    printf '%s=%s\n' "$key" "$(openssl rand -hex 32)" >> "$env_file"
    echo "Generated a missing private credential: $key"
  fi
}

ensure_scanner_token SCANNER_TOKEN_ELDER
ensure_scanner_token SCANNER_TOKEN_BEAVIUM
chmod 600 "$env_file"

echo "Checking Compose configuration and running services."
sudo docker compose config --quiet
api_container="$(sudo docker compose ps -q api)"
postgres_container="$(sudo docker compose ps -q postgres)"
test -n "$api_container"
test -n "$postgres_container"
test "$(sudo docker inspect --format='{{.State.Running}}' "$api_container")" = true
test "$(sudo docker inspect --format='{{.State.Running}}' "$postgres_container")" = true

old_image_ref="$(sudo docker inspect --format='{{.Config.Image}}' "$api_container")"
old_image_id="$(sudo docker inspect --format='{{.Image}}' "$api_container")"
test -n "$old_image_ref"
test -n "$old_image_id"
sudo docker image tag "$old_image_id" "$rollback_tag"

curl --fail --silent --show-error --max-time 5 \
  "http://127.0.0.1:8080/api/v1/items?page=0&size=1" > /dev/null

mkdir -p "$backup_dir"
chmod 700 "$backup_dir"
database_bytes="$(sudo docker compose exec -T postgres psql -U metin_market -d metin_market -Atqc \
  "SELECT pg_database_size(current_database())")"
available_bytes="$(df -PB1 "$backup_dir" | awk 'NR == 2 { print $4 }')"
required_bytes="$((database_bytes + 536870912))"
if [ "$available_bytes" -lt "$required_bytes" ]; then
  echo "Insufficient free space for a conservative database backup." >&2
  exit 1
fi

echo "Checking source table counts and Pandora Flyway history."
sudo docker compose exec -T postgres psql -U metin_market -d metin_market \
  -v ON_ERROR_STOP=1 -c "SELECT version, success FROM public.flyway_schema_history ORDER BY installed_rank"
sudo docker compose exec -T postgres psql -U metin_market -d metin_market \
  -v ON_ERROR_STOP=1 -c "SELECT 'synchronization_batch' AS table_name, count(*) FROM public.synchronization_batch UNION ALL SELECT 'scan_run', count(*) FROM public.scan_run UNION ALL SELECT 'shop_observation', count(*) FROM public.shop_observation UNION ALL SELECT 'shop_listing', count(*) FROM public.shop_listing UNION ALL SELECT 'shop_listing_attribute', count(*) FROM public.shop_listing_attribute UNION ALL SELECT 'shop_listing_socket', count(*) FROM public.shop_listing_socket"

echo "Creating production database backup: $backup_file"
sudo docker compose exec -T postgres sh -c \
  'PGPASSWORD="$POSTGRES_PASSWORD" pg_dump -h 127.0.0.1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc' \
  > "$backup_file"
chmod 600 "$backup_file"
test -s "$backup_file"
sudo docker compose exec -T postgres pg_restore -l < "$backup_file" > /dev/null

echo "Prebuilding the multiserver API image."
sudo docker compose build api

echo "Stopping the API briefly to migrate the existing Pandora schema."
sudo docker compose stop api
sudo docker compose exec -T postgres psql -U metin_market -d metin_market \
  -v ON_ERROR_STOP=1 < ops/migrate-public-to-pandora.sql
migration_completed=1

sudo docker compose up -d --no-build api

check_api() {
  local endpoint
  for endpoint in \
    "http://127.0.0.1:8080/api/v1/items?page=0&size=1" \
    "http://127.0.0.1:8080/api/v1/items/suggestions?query=Miecz" \
    "http://127.0.0.1:8080/api/v1/items/statistics?vnum=180" \
    "http://127.0.0.1:8080/api/v1/servers/pandora/items?page=0&size=1" \
    "http://127.0.0.1:8080/api/v1/servers/pandora/items/suggestions?query=Miecz" \
    "http://127.0.0.1:8080/api/v1/servers/pandora/items/statistics?vnum=180" \
    "http://127.0.0.1:8080/api/v1/servers/elder/items?page=0&size=1" \
    "http://127.0.0.1:8080/api/v1/servers/elder/items/suggestions?query=Miecz" \
    "http://127.0.0.1:8080/api/v1/servers/elder/items/statistics?vnum=180" \
    "http://127.0.0.1:8080/api/v1/servers/beavium/items?page=0&size=1" \
    "http://127.0.0.1:8080/api/v1/servers/beavium/items/suggestions?query=Miecz" \
    "http://127.0.0.1:8080/api/v1/servers/beavium/items/statistics?vnum=180" \
    "https://api.mazikox.pl/api/v1/servers/pandora/items?page=0&size=1" \
    "https://api.mazikox.pl/api/v1/servers/elder/items?page=0&size=1" \
    "https://api.mazikox.pl/api/v1/servers/beavium/items?page=0&size=1"; do
    curl --fail --silent --show-error --max-time 5 "$endpoint" > /dev/null || return 1
  done
}

healthy=0
for attempt in {1..12}; do
  if check_api; then
    healthy=1
    break
  fi
  sleep 5
done
if [ "$healthy" -ne 1 ]; then
  echo "API health check failed after migration." >&2
  rollback_on_error 1
fi

echo "Multiserver API deployed. Verified database backup: $backup_file"
