#!/usr/bin/env python3
"""One-off, read-only SQLite to Metin Market HTTP API importer."""

from __future__ import annotations

import argparse
import json
import sqlite3
import sys
import uuid
from collections import defaultdict
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen


DEFAULT_DATABASE = Path("samples/eldersuite-history-pandora.db")
DEFAULT_API_URL = "http://localhost:8080"
DEFAULT_SOURCE_ID = "eldersuite-pandora-main"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Import market scan history from SQLite through the ingestion HTTP API."
    )
    parser.add_argument("--database", type=Path, default=DEFAULT_DATABASE)
    parser.add_argument("--api-url", default=DEFAULT_API_URL)
    parser.add_argument("--token", default="local-dev-token")
    parser.add_argument("--source-id", default=DEFAULT_SOURCE_ID)
    parser.add_argument("--batch-size", type=int, default=20)
    return parser.parse_args()


def read_database(path: Path) -> tuple[list[dict], list[dict], dict[str, int]]:
    if not path.is_file():
        raise RuntimeError(f"SQLite database does not exist: {path}")

    database_uri = path.resolve().as_uri() + "?mode=ro"
    connection = sqlite3.connect(database_uri, uri=True)
    connection.row_factory = sqlite3.Row
    try:
        runs = [
            {
                "runId": row["run_id"],
                "startedAt": row["started_at"],
                "endedAt": row["ended_at"],
                "state": row["state"],
                "mapId": row["map_id"],
                "channel": row["channel"],
                "totalTargets": row["total_targets"],
                "visitedTargets": row["visited_targets"],
                "failedTargets": row["failed_targets"],
            }
            for row in connection.execute("SELECT * FROM shop_scan_run ORDER BY started_at, run_id")
        ]

        attributes: dict[int, list[dict]] = defaultdict(list)
        for row in connection.execute(
            "SELECT listing_id, slot_index, attr_type, attr_value "
            "FROM shop_listing_attribute ORDER BY listing_id, slot_index"
        ):
            attributes[row["listing_id"]].append(
                {
                    "slotIndex": row["slot_index"],
                    "attrType": row["attr_type"],
                    "attrValue": row["attr_value"],
                }
            )

        sockets: dict[int, list[dict]] = defaultdict(list)
        for row in connection.execute(
            "SELECT listing_id, socket_index, socket_value "
            "FROM shop_listing_socket ORDER BY listing_id, socket_index"
        ):
            sockets[row["listing_id"]].append(
                {
                    "socketIndex": row["socket_index"],
                    "socketValue": row["socket_value"],
                }
            )

        listings: dict[str, list[dict]] = defaultdict(list)
        for row in connection.execute(
            "SELECT * FROM shop_listing ORDER BY observation_id, slot_index, listing_id"
        ):
            listing_id = row["listing_id"]
            listings[row["observation_id"]].append(
                {
                    "listingId": listing_id,
                    "slotIndex": row["slot_index"],
                    "vnum": row["vnum"],
                    "itemName": row["item_name"],
                    "count": row["count"],
                    "priceRaw": row["price_raw"],
                    "unitPrice": row["unit_price"],
                    "tailField": row["tail_field"],
                    "attributes": attributes[listing_id],
                    "sockets": sockets[listing_id],
                }
            )

        observations = []
        for row in connection.execute(
            "SELECT * FROM shop_observation ORDER BY observed_at, observation_id"
        ):
            observation_listings = listings[row["observation_id"]]
            if row["item_count"] != len(observation_listings):
                raise RuntimeError(
                    f"Observation {row['observation_id']} reports {row['item_count']} items "
                    f"but SQLite contains {len(observation_listings)} listings"
                )
            observations.append(
                {
                    "observationId": row["observation_id"],
                    "runId": row["run_id"],
                    "shopVid": row["shop_vid"],
                    "shopTitle": row["shop_title"],
                    "ownerName": row["owner_name"],
                    "mapId": row["map_id"],
                    "channel": row["channel"],
                    "x": row["x"],
                    "y": row["y"],
                    "z": row["z"],
                    "observedAt": row["observed_at"],
                    "contentFingerprint": row["content_fingerprint"],
                    "itemCount": row["item_count"],
                    "listings": observation_listings,
                }
            )

        counts = {
            "runs": len(runs),
            "observations": len(observations),
            "listings": sum(len(value) for value in listings.values()),
            "attributes": sum(len(value) for value in attributes.values()),
            "sockets": sum(len(value) for value in sockets.values()),
        }
        return runs, observations, counts
    finally:
        connection.close()


def post_batch(api_url: str, token: str, payload: dict) -> dict:
    endpoint = api_url.rstrip("/") + "/internal/v1/imports"
    body = json.dumps(payload, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
    request = Request(
        endpoint,
        data=body,
        method="POST",
        headers={"Content-Type": "application/json", "X-Scanner-Token": token},
    )
    try:
        with urlopen(request, timeout=60) as response:
            return json.loads(response.read().decode("utf-8"))
    except HTTPError as error:
        response_body = error.read().decode("utf-8", errors="replace")
        raise RuntimeError(
            f"HTTP batch failed with status {error.code} at {endpoint}: {response_body}"
        ) from error
    except URLError as error:
        raise RuntimeError(f"Cannot reach ingestion API at {endpoint}: {error.reason}") from error


def main() -> int:
    args = parse_args()
    if args.batch_size <= 0:
        print("ERROR: --batch-size must be greater than zero", file=sys.stderr)
        return 2

    try:
        runs, observations, counts = read_database(args.database)
        print(
            "SQLite market data: "
            + ", ".join(f"{name}={value}" for name, value in counts.items())
        )

        batch_count = max(1, (len(observations) + args.batch_size - 1) // args.batch_size)
        import_id = uuid.uuid4().hex
        accepted = {"runs": 0, "observations": 0, "listings": 0}

        for index in range(batch_count):
            start = index * args.batch_size
            batch_observations = observations[start : start + args.batch_size]
            batch_id = f"sqlite-{import_id}-{index + 1:04d}"
            payload = {
                "sourceId": args.source_id,
                "batchId": batch_id,
                "runs": runs if index == 0 else [],
                "observations": batch_observations,
            }
            result = post_batch(args.api_url, args.token, payload)
            accepted["runs"] += result["importedRuns"]
            accepted["observations"] += result["importedObservations"]
            accepted["listings"] += result["importedListings"]
            print(
                f"Batch {index + 1}/{batch_count} accepted: "
                f"observations={result['importedObservations']}, "
                f"listings={result['importedListings']}, "
                f"batchId={batch_id}"
            )

        print("Import complete.")
        print(
            "Source rows processed: "
            + ", ".join(f"{name}={value}" for name, value in counts.items())
        )
        print(
            "Rows reported affected by the API: "
            f"runs={accepted['runs']}, observations={accepted['observations']}, "
            f"listings={accepted['listings']}"
        )
        print(
            "A safe rerun reports zero new observations/listings; runs are upserted "
            "and may still be reported as affected."
        )
        return 0
    except (RuntimeError, sqlite3.Error, ValueError, KeyError) as error:
        print(f"ERROR: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
