#!/usr/bin/env bash
set -euo pipefail

# Run from the production repository after the API health check.
# Keep the token out of logs and curl's command-line arguments.
scanner_token="$(sudo docker compose exec -T api printenv SCANNER_TOKEN_BEAVIUM)"
if [ -z "$scanner_token" ]; then
  echo "Missing Beavium scanner token" >&2
  exit 1
fi
printf 'X-Scanner-Token: %s\n' "$scanner_token" |
  curl --fail-with-body --silent --show-error \
    --connect-timeout 10 --max-time 120 \
    --header @- \
    --header 'Content-Type: text/tab-separated-values; charset=utf-8' \
    --data-binary @catalogs/item_proto_beavium.tsv \
    http://127.0.0.1:8080/internal/v1/servers/beavium/imports/item-proto
printf '\n'
