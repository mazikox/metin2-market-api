# Metin Market API

Spring Boot 4.1.1 / Java 26 backend for synchronizing Metin2 scanner observations into PostgreSQL and searching the historical listings.

## Run locally

The simplest route starts PostgreSQL and the API together (including a Java 26 build):

```powershell
$env:POSTGRES_PASSWORD = "replace-with-a-private-password"
$env:SCANNER_TOKEN = "pandora-private-token"
$env:SCANNER_TOKEN_ELDER = "elder-private-token"
$env:SCANNER_TOKEN_BEAVIUM = "beavium-private-token"
$env:ANALYTICS_SECRET = "replace-with-at-least-32-random-characters"
$env:ANALYTICS_PROXY_TOKEN = "replace-with-another-32-random-characters"
docker compose up --build
```

The API listens on `http://localhost:8080`. On startup Flyway applies the shared migrations separately in the `pandora`, `elder`, and `beavium` schemas. PostgreSQL data is retained in the `metin-market-postgres-v18` Docker volume. The database is also available on localhost port `15432` by default; set `POSTGRES_PORT` before starting Compose to change it.

To run the API from an installed Java 26 JDK instead:

```powershell
$env:POSTGRES_PASSWORD = "replace-with-a-private-password"
$env:SCANNER_TOKEN = "pandora-private-token"
$env:SCANNER_TOKEN_ELDER = "elder-private-token"
$env:SCANNER_TOKEN_BEAVIUM = "beavium-private-token"
docker compose up -d postgres
$env:DATABASE_URL = "jdbc:postgresql://localhost:15432/metin_market"
$env:DATABASE_PASSWORD = $env:POSTGRES_PASSWORD
./mvnw.cmd spring-boot:run
```

## API

Import a batch (the sample is taken from `samples/eldersuite-history-pandora.db`):

```powershell
curl.exe -X POST http://localhost:8080/internal/v1/imports `
  -H "Content-Type: application/json" `
  -H "X-Scanner-Token: $env:SCANNER_TOKEN" `
  --data-binary "@samples/import-example.json"
```

The legacy routes above are permanent aliases for Pandora. Server-specific routes are:

| Server | Search | Suggestions | Statistics | Import |
| --- | --- | --- | --- | --- |
| Pandora | `/api/v1/servers/pandora/items` | `/api/v1/servers/pandora/items/suggestions` | `/api/v1/servers/pandora/items/statistics` | `/internal/v1/servers/pandora/imports` |
| Elder | `/api/v1/servers/elder/items` | `/api/v1/servers/elder/items/suggestions` | `/api/v1/servers/elder/items/statistics` | `/internal/v1/servers/elder/imports` |
| Beavium | `/api/v1/servers/beavium/items` | `/api/v1/servers/beavium/items/suggestions` | `/api/v1/servers/beavium/items/statistics` | `/internal/v1/servers/beavium/imports` |

Each import route accepts the same JSON and requires the matching `X-Scanner-Token`: `SCANNER_TOKEN` for Pandora, `SCANNER_TOKEN_ELDER` for Elder, or `SCANNER_TOKEN_BEAVIUM` for Beavium. The database schema is selected by the route and is not taken from the request body.

Retrying the same `sourceId` + `batchId` and payload returns `alreadyProcessed: true`. Reusing that batch identity with different data returns HTTP 409. Observations are also deduplicated by `sourceId` + `observationId`; a changed fingerprint or observation payload is rejected.

Search by case-insensitive item-name fragment, optionally with an exact vnum:

```powershell
curl.exe "http://localhost:8080/api/v1/items?query=Zatruty&page=0&size=20"
curl.exe "http://localhost:8080/api/v1/items?vnum=180&query="
```

Every result contains price and quantity, indexed attributes and sockets, observation time, and the observed shop's VID/title/owner, map, channel, and coordinates. Results are historical and newest-first; the API deliberately does not infer whether a listing is still active.

## Test

With Java 26 and Docker running:

```powershell
./mvnw.cmd test
```

The integration test starts PostgreSQL 18 with Testcontainers, applies Flyway, imports a real-shaped observation twice, and verifies search output and child data.

## Import the supplied SQLite history

The one-off development utility reads only the market tables from SQLite and sends them through the protected HTTP API in batches:

```powershell
python tools/import_sqlite.py
```

Defaults target `samples/eldersuite-history-pandora.db`, `http://localhost:8080`, source `eldersuite-pandora-main`, and the development token `local-dev-token`. Run `python tools/import_sqlite.py --help` to override them. Each invocation uses new synchronization batch IDs; stable source observation IDs let the API safely deduplicate reruns.

Pandora keeps those defaults. For Elder and Beavium, pass `--server`, `--database`, and `--source-id`, and set `SCANNER_TOKEN_ELDER` or `SCANNER_TOKEN_BEAVIUM`. Example:

```powershell
$env:SCANNER_TOKEN_ELDER = "replace-with-a-private-token"
python update_market.py --server elder --database "C:\path\to\elder-history.db" --source-id "eldersuite-elder-main" --dry-run
```

The production schema move is a controlled operation. Rehearse it on a restored database copy first. The manual `Deploy backend` workflow on `main`, with `confirm_multiserver_migration` enabled, checks the VPS Compose setup, verifies a database backup, migrates Pandora, deploys the API, and checks all server routes. It retains the backup under `~/metin-market-db-backups` and attempts to restore the previous schema and API image if startup checks fail. The workflow requires all three scanner tokens in the VPS `.env`. See [docs/multi-server-migration-plan.md](docs/multi-server-migration-plan.md).

## Source identity model

`sourceId` identifies one scanner database/installation. It namespaces SQLite's run, observation, and integer listing identities so independent scanners cannot collide. A scan run is separate from an HTTP synchronization batch. Shop VID is stored on each immutable observation, never treated as a permanent shop identity.

## Private catalog statistics

Daily catalog analytics and the Caddy-protected `/admin/stats` panel replace Umami
and the log-based usage report. Deployment, secrets, schema and retention:
[docs/statystyki-katalogu.md](docs/statystyki-katalogu.md).
Analytics defaults to disabled outside Compose. No cookies or persistent client IDs.
