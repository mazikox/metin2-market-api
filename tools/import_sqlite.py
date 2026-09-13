#!/usr/bin/env python3
"""One-off, read-only SQLite to Metin Market HTTP API importer for single completed scan runs."""

from __future__ import annotations

import argparse
import json
import sqlite3
import sys
from collections import defaultdict
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen


DEFAULT_DATABASE = Path("samples/eldersuite-history-pandora.db")
DEFAULT_API_URL = "http://localhost:8080"
DEFAULT_SOURCE_ID = "eldersuite-pandora-main"
STATE_COMPLETED = 3


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Import a single completed market scan run from SQLite through the ingestion HTTP API."
    )
    parser.add_argument("--database", type=Path, default=DEFAULT_DATABASE, help="Path to SQLite database file")
    parser.add_argument("--api-url", default=DEFAULT_API_URL, help="Base URL of Metin Market API")
    parser.add_argument("--token", default="local-dev-token", help="Scanner ingestion security token")
    parser.add_argument("--source-id", default=DEFAULT_SOURCE_ID, help="Source identifier")
    parser.add_argument("--batch-size", type=int, default=20, help="Number of observations per batch")

    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument("--latest-completed", action="store_true", help="Import only the newest completed scan run")
    group.add_argument("--run-id", type=str, help="Import a specific scan run ID")

    parser.add_argument(
        "--publishable",
        action="store_true",
        default=False,
        help="Mark the imported run as publishable/full for the public market (default: False)",
    )
    parser.add_argument(
        "--force-completed",
        action="store_true",
        default=False,
        help="Force run state to completed (state=3) and set ended_at from latest observation if NULL (requires explicit --run-id)",
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        default=False,
        help="Inspect and print run summary without sending HTTP requests",
    )
    return parser.parse_args()


def select_target_run(
    connection: sqlite3.Connection, run_id: str | None, latest_completed: bool, force_completed: bool
) -> tuple[dict, bool]:
    if force_completed and not run_id:
        raise RuntimeError("--force-completed requires explicit --run-id <id>")

    if latest_completed:
        row = connection.execute(
            "SELECT * FROM shop_scan_run "
            "WHERE state = ? "
            "ORDER BY datetime(COALESCE(ended_at, started_at)) DESC, started_at DESC, run_id DESC "
            "LIMIT 1",
            (STATE_COMPLETED,),
        ).fetchone()
        if not row:
            raise RuntimeError("No completed scan runs found in SQLite database (state=3)")
        return dict(row), False

    if run_id:
        row = connection.execute(
            "SELECT * FROM shop_scan_run WHERE run_id = ?",
            (run_id,),
        ).fetchone()
        if not row:
            raise RuntimeError(f"Scan run '{run_id}' does not exist in SQLite database")
        
        is_forced = False
        if row["state"] != STATE_COMPLETED:
            if not force_completed:
                raise RuntimeError(
                    f"Scan run '{run_id}' is not completed (state={row['state']}, ended_at={row['ended_at']}). "
                    f"Expected state={STATE_COMPLETED} (COMPLETED). Use --force-completed with --run-id to override."
                )
            is_forced = True

        return dict(row), is_forced

    raise RuntimeError("Either --latest-completed or --run-id <id> must be specified")


def read_run_data(
    path: Path, run_id: str | None, latest_completed: bool, publishable: bool, force_completed: bool
) -> tuple[dict, list[dict], dict[str, int], bool]:
    if not path.is_file():
        raise RuntimeError(f"SQLite database does not exist: {path}")

    database_uri = path.resolve().as_uri() + "?mode=ro"
    connection = sqlite3.connect(database_uri, uri=True)
    connection.row_factory = sqlite3.Row
    try:
        run_row, is_forced = select_target_run(connection, run_id, latest_completed, force_completed)
        target_run_id = run_row["run_id"]

        ended_at = run_row["ended_at"]
        state = STATE_COMPLETED if is_forced else run_row["state"]

        if is_forced and not ended_at:
            max_obs_row = connection.execute(
                "SELECT max(observed_at) AS latest_obs FROM shop_observation WHERE run_id = ?",
                (target_run_id,),
            ).fetchone()
            ended_at = max_obs_row["latest_obs"] if max_obs_row and max_obs_row["latest_obs"] else run_row["started_at"]

        run_payload = {
            "runId": target_run_id,
            "startedAt": run_row["started_at"],
            "endedAt": ended_at,
            "state": state,
            "mapId": run_row["map_id"],
            "channel": run_row["channel"],
            "totalTargets": run_row["total_targets"],
            "visitedTargets": run_row["visited_targets"],
            "failedTargets": run_row["failed_targets"],
            "publishable": publishable,
        }

        attributes: dict[int, list[dict]] = defaultdict(list)
        for row in connection.execute(
            "SELECT a.listing_id, a.slot_index, a.attr_type, a.attr_value "
            "FROM shop_listing_attribute a "
            "JOIN shop_listing l ON l.listing_id = a.listing_id "
            "JOIN shop_observation o ON o.observation_id = l.observation_id "
            "WHERE o.run_id = ? "
            "ORDER BY a.listing_id, a.slot_index",
            (target_run_id,),
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
            "SELECT s.listing_id, s.socket_index, s.socket_value "
            "FROM shop_listing_socket s "
            "JOIN shop_listing l ON l.listing_id = s.listing_id "
            "JOIN shop_observation o ON o.observation_id = l.observation_id "
            "WHERE o.run_id = ? "
            "ORDER BY s.listing_id, s.socket_index",
            (target_run_id,),
        ):
            sockets[row["listing_id"]].append(
                {
                    "socketIndex": row["socket_index"],
                    "socketValue": row["socket_value"],
                }
            )

        listings: dict[str, list[dict]] = defaultdict(list)
        for row in connection.execute(
            "SELECT l.* "
            "FROM shop_listing l "
            "JOIN shop_observation o ON o.observation_id = l.observation_id "
            "WHERE o.run_id = ? "
            "ORDER BY l.observation_id, l.slot_index, l.listing_id",
            (target_run_id,),
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
            "SELECT * FROM shop_observation WHERE run_id = ? ORDER BY observed_at, observation_id",
            (target_run_id,),
        ):
            obs_id = row["observation_id"]
            observation_listings = listings[obs_id]
            if row["item_count"] != len(observation_listings):
                raise RuntimeError(
                    f"Observation {obs_id} reports {row['item_count']} items "
                    f"but SQLite contains {len(observation_listings)} listings"
                )
            observations.append(
                {
                    "observationId": obs_id,
                    "runId": target_run_id,
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
            "observations": len(observations),
            "listings": sum(len(value) for value in listings.values()),
            "attributes": sum(len(value) for value in attributes.values()),
            "sockets": sum(len(value) for value in sockets.values()),
        }
        return run_payload, observations, counts, is_forced, run_row["state"]
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

    if args.force_completed and not args.run_id:
        print("ERROR: --force-completed requires explicit --run-id <id>", file=sys.stderr)
        return 2

    try:
        run_payload, observations, counts, is_forced, orig_state = read_run_data(
            args.database, args.run_id, args.latest_completed, args.publishable, args.force_completed
        )

        batch_count = max(1, (len(observations) + args.batch_size - 1) // args.batch_size)
        run_id = run_payload["runId"]

        print("=== Selected Scanner Run Summary ===")
        print(f"Run ID:            {run_id}")
        print(f"Started at:        {run_payload['startedAt']}")
        print(f"Ended at:          {run_payload['endedAt']}")
        if is_forced:
            print(f"State:             {run_payload['state']} (FORCED COMPLETED, original SQLite state={orig_state})")
            print(f"Forced completion: True (ended_at resolved to '{run_payload['endedAt']}')")
        else:
            print(f"State:             {run_payload['state']} (COMPLETED)")
            print(f"Forced completion: False")
        print(f"Map ID:            {run_payload['mapId']}")
        print(f"Channel:           {run_payload['channel']}")
        print(f"Targets:           total={run_payload['totalTargets']}, visited={run_payload['visitedTargets']}, failed={run_payload['failedTargets']}")
        print(f"Publishable:       {run_payload['publishable']}")
        print(f"Observations:      {counts['observations']}")
        print(f"Listings:          {counts['listings']}")
        print(f"Attributes:        {counts['attributes']}")
        print(f"Sockets:           {counts['sockets']}")
        print(f"Total batches:     {batch_count} (batch_size={args.batch_size})")
        print("====================================")

        if args.dry_run:
            print("DRY RUN: No requests sent to the backend.")
            return 0

        accepted = {"runs": 0, "observations": 0, "listings": 0}

        for index in range(batch_count):
            start = index * args.batch_size
            batch_observations = observations[start : start + args.batch_size]
            batch_id = f"sqlite-{run_id}-{index + 1:04d}"
            payload = {
                "sourceId": args.source_id,
                "batchId": batch_id,
                "runs": [run_payload] if index == 0 else [],
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
            f"Run {run_id} processed: "
            f"observations={counts['observations']}, listings={counts['listings']}"
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

