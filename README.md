# Metin Market API

Spring Boot 4.1.1 / Java 26 backend for synchronizing Metin2 scanner observations into PostgreSQL and searching the historical listings.

## Run locally

The simplest route starts PostgreSQL and the API together (including a Java 26 build):

```powershell
$env:SCANNER_TOKEN = "replace-with-a-private-token"
docker compose up --build
```

The API listens on `http://localhost:8080`. Flyway creates and upgrades the database schema on startup. PostgreSQL data is retained in the `metin-market-postgres-v18` Docker volume. The database is also available on localhost port `15432` by default; set `POSTGRES_PORT` before starting Compose to change it.

To run the API from an installed Java 26 JDK instead:

```powershell
docker compose up -d postgres
$env:SCANNER_TOKEN = "replace-with-a-private-token"
$env:DATABASE_URL = "jdbc:postgresql://localhost:15432/metin_market"
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

## Source identity model

`sourceId` identifies one scanner database/installation. It namespaces SQLite's run, observation, and integer listing identities so independent scanners cannot collide. A scan run is separate from an HTTP synchronization batch. Shop VID is stored on each immutable observation, never treated as a permanent shop identity.
