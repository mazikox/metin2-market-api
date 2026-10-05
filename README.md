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

The server market overview is available at `/api/v1/servers/{server}/items/overview?limit=8`
(and `/api/v1/items/overview` for Pandora). It returns up to 24 item cards, ranked by
unique shop count, with minimum unit price, total quantity, scan ID/time and the
number of observed shops. Only the latest completed publishable scan is used;
repeat observations of one shop count once. Different item names sharing a VNUM
remain separate variants. The endpoint uses one aggregate query and no per-item
statistics requests. Overview views preserve catalog analytics without counting
as user searches.

Every result contains price and quantity, indexed attributes and sockets, observation time, and the observed shop's VID/title/owner, map, channel, and coordinates. Results use the latest published scan and default to unit price ascending; the API deliberately does not infer whether a listing is still active.

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

Overview supports `sort=shops` (default, unique shops) and `sort=quantity` (total units). Both rankings are calculated across the latest scan before applying the limit; quantity ties use shop count, then item ID and name.

Offer search accepts `sort=priceAsc` (default), `sort=priceDesc`, or `sort=quantity`. Sorting happens across all matching aggregated offers before pagination. Quantity uses the total units in each aggregated offer, with cheaper unit prices first on ties. Observation time and listing ID make ties stable.


## Item metadata catalog

Import a server-specific ElderSuite `item_proto` TSV (the exact 19-column header is required):

```powershell
$env:SCANNER_TOKEN_BEAVIUM = "replace-with-the-beavium-token"
python tools/import_item_proto.py --server beavium --file "C:\path\to\item_proto_beavium.tsv"
```

The protected endpoint `/internal/v1/servers/{server}/imports/item-proto` accepts
`text/tab-separated-values; charset=utf-8` with the matching `X-Scanner-Token`.
Validation completes before writing, and the import commits atomically. Reimporting
updates definitions and replaces their base bonuses; definitions omitted from a
file are retained. Invalid or duplicate VNUMs in the file,
invalid numeric values and malformed columns return HTTP 400. Unusual inverted
attack ranges are preserved and returned in `unusualAttackRangeVnums`, to support
special item subtypes such as Beavium arrows.

Migration V4 creates separate catalog tables in each game-server schema, indexed
for future category/level and base-bonus filters. Offer search returns nullable
`metadata` with type/subtype, required level, base stats, socket capacity and
`builtInBonuses` (raw apply type/value). Metadata is fetched once per result page
by VNUM, without modifying historical offers or requiring a new scan. Servers
without an imported catalog return `metadata: null`. Scanned offer `attributes`
and occupied `sockets` retain their existing meaning. Filtering and displaying
this metadata in the web UI is supported in the item detail drawer and category/level filters.

Production deployment imports the versioned `catalogs/item_proto_beavium.tsv`
after the API health check via `ops/import-beavium-catalog.sh`. The script reads
the existing Beavium token from the API container without logging it. Each deploy
upserts this catalog, including its base bonuses, independently of shop scans.
Update the versioned TSV when deploying future Beavium catalog changes.


## Extra offer bonus filters

Offer search accepts up to 7 repeated `bonus` parameters, e.g.
`/api/v1/servers/beavium/items?bonus=72:40&bonus=16:10`.
`bonus=72` requires the bonus to be present with any value, including zero or
negative; `bonus=71:-25` requires a value greater than or equal to -25.
All conditions must hold on the same scanned offer. They combine with name/VNUM
filters, run before grouping, sorting and pagination, and are also applied to
out-of-range page counts. Built-in catalog applies, base stats and socket bonuses
are deliberately excluded. Duplicate bonus types, malformed integers and more
than 7 conditions return HTTP 400.

`/api/v1/servers/{server}/items/bonus-options` (or the Pandora legacy
`/api/v1/items/bonus-options`) returns bonus types present in canonical shop
observations of the latest completed publishable scan, with server-specific
names and units. Unknown types remain available with their numeric type.
Migration V5 adds an index on attribute type/value/listing ID. Filters use
parameterized `EXISTS` predicates and do not multiply offer rows or totals.
Price statistics still describe all bonuses of the selected item, independently
of the offer filters; the web UI states this when bonus filters are active.


## Category and required level filters

Search supports `category=necklaces`, `minLevel=30`, `maxLevel=75` and their
combination with extra bonuses, names and VNUMs. Both level bounds are inclusive;
zero is a real bound, omitted bounds are unrestricted. Inverted or negative
ranges and unknown category slugs return HTTP 400. Categories use engine
item_proto type/subtype values (the weapon category excludes arrows and quivers),
not name matching. Available category slugs and Polish labels come from
`/api/v1/servers/{server}/items/category-options`, which also returns `available`
for catalog availability. When any category/level condition is present, offers
without a catalog definition are excluded. Without those conditions, existing
search behavior includes offers with unknown metadata. The web UI disables
category/level filtering when no catalog exists for that server. The existing
V4 catalog indexes support these parameterized EXISTS predicates; no migration
or scan rerun is required.


## Markets per map and private scan management

V6/V7 keep one active completed, publishable, fully imported scan per map in
**each server schema**. Default mode follows the newest eligible scan by end
time, start time and ID. Importing another map retains the other maps' scans.
Joan's `metin2_map_a1_summer` shares the `metin2_map_a1` market. Shop VID
deduplication is scoped to map and channel. Offers, price statistics, bonuses,
suggestions and overview all use `market_canonical_observation`.

`GET /api/v1/admin/servers/{server}/scans` returns map configuration and up to
100 newest scans per map, plus any pinned older scan. `PUT` on the same URL
accepts `{"maps":[{"mapId":"metin2_map_a1","enabled":false,"selectedScanId":null}]}`.
Only listed maps change, atomically. Null selection follows the latest scan;
a numeric ID pins an eligible scan from that server and map. Disabled maps stay
hidden after new imports. Invalid, incomplete, unpublished or other-map scan
selections fail without changing any map. Settings are persisted in
`market_map_selection`, independently of imports. No scan data is deleted.

The `/admin/scans` page reuses `/admin/stats`' Caddy Basic Auth credentials,
not scanner credentials. Caddy's existing `/admin/*` and `/api/v1/admin/*` rules
protect it. The backend also verifies the trusted proxy token and authenticated
admin header; writes require `X-Admin-Action: scan-selection`, JSON, and reject
cross-site browser requests. CORS permits PUT only on the private admin routes.

The updated SQLite importer accepts repeated `--map` with `--latest-completed`:

```powershell
python tools/import_sqlite.py --server beavium --database history.db --source-id eldersuite-beavium-main --latest-completed --publishable --map metin2_map_a1 --map metin2_map_b1 --map metin2_map_c1
```

Pass just one or two `--map` flags for the maps requested by the operator.
For separate SQLite files, run once per file with that file's map. Keep a stable
source ID and use the matching scanner token environment variable. All requested
maps are validated and read before the first network request. `--dry-run` sends
nothing and SQLite is always opened read-only. Existing `--run-id` and unfiltered
`--latest-completed` remain single-run modes.

New imports send `expectedObservations` in run metadata. A transactionally
maintained count exposes the run only once all observations have arrived.
A failed/midway import keeps the previous eligible scan visible; retrying resumes
without duplicate observations. The `sqlite-v2-` batch prefix avoids conflicts
with earlier manifest-free imports. Historical imports without a manifest remain
compatible. Agents/direct clients should always send the expected observation
count for new batched imports. An empty complete scan (expected count zero) is
valid and replaces that map's old offers.

Public offer and statistics endpoints accept repeated `map` parameters, e.g.
`items?map=metin2_map_a1&map=metin2_map_c1&bonus=11:20`. Filtering is applied before
grouping, sorting, pagination and total counts. No parameter means all active
maps. `/items/map-options` lists only active maps. The web groups map, category,
level and extra bonus conditions in one expandable panel. Price statistics
respect selected maps while still covering all extra bonuses of an item.
