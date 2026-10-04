#!/usr/bin/env python3
"""User-friendly local CLI script for updating the production Metin Market from ElderSuite SQLite database."""

from __future__ import annotations

import argparse
import os
import sqlite3
import sys
from pathlib import Path

# Add project root to sys.path so tools module can be imported
PROJECT_ROOT = Path(__file__).resolve().parent
if str(PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(PROJECT_ROOT))

# Ensure UTF-8 console output on Windows
if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
if hasattr(sys.stderr, "reconfigure"):
    sys.stderr.reconfigure(encoding="utf-8", errors="replace")

from tools.import_sqlite import (
    DEFAULT_SOURCE_ID,
    SERVERS,
    STATE_COMPLETED,
    TOKEN_ENVIRONMENT,
    find_newest_run,
    load_run_payload_and_observations,
    post_batch,
)

DEFAULT_ELDERSUITE_DBS = {
    "pandora": Path(os.environ.get("LOCALAPPDATA", "")) / "ElderSuite" / "eldersuite-history-pandora.db",
    "elder": Path(os.environ.get("LOCALAPPDATA", "")) / "ElderSuite" / "eldersuite-history-elder.db",
    "beavium": Path(os.environ.get("LOCALAPPDATA", "")) / "ElderSuite" / "eldersuite-history-beavium.db",
}
DEFAULT_SOURCE_IDS = {
    "pandora": DEFAULT_SOURCE_ID,
    "elder": "eldersuite-elder-main",
    "beavium": "eldersuite-beavium-main",
}
DEFAULT_PROD_API_URL = "https://api.mazikox.pl"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Update one Metin Market game server with the latest scan from an ElderSuite SQLite database."
    )
    parser.add_argument("--server", choices=SERVERS, default="pandora", help="Game server receiving this import")
    parser.add_argument(
        "--database",
        type=Path,
        help="Path to the server's ElderSuite SQLite database (defaults to the selected server DB in %LOCALAPPDATA%\\ElderSuite)",
    )
    parser.add_argument(
        "--api-url",
        default=DEFAULT_PROD_API_URL,
        help=f"Production Metin Market API URL (default: {DEFAULT_PROD_API_URL})",
    )
    parser.add_argument(
        "--batch-size",
        type=int,
        default=20,
        help="Number of observations per HTTP batch (default: 20)",
    )
    parser.add_argument(
        "--source-id",
        help="Source identifier (defaults to eldersuite-<server>-main)",
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        default=False,
        help="Show full summary and exit without asking for confirmation or sending requests",
    )
    return parser.parse_args()


def describe_state(state: int) -> str:
    states = {
        1: "1 (Zainicjalizowany)",
        2: "2 (W toku / Niezamknięty)",
        3: "3 (Zakończony / COMPLETED)",
        4: "4 (Przerwany / Błąd)",
    }
    return states.get(state, f"{state} (Nieznany)")


def main() -> int:
    args = parse_args()

    database = args.database or DEFAULT_ELDERSUITE_DBS.get(args.server)
    source_id = args.source_id or DEFAULT_SOURCE_IDS.get(args.server)

    if not database or not source_id:
        print(f"BŁĄD: Brak konfiguracji domyślnej dla serwera: {args.server}", file=sys.stderr)
        return 2

    if not database.is_file():
        print(f"BŁĄD: Plik bazy danych SQLite nie istnieje:\n  {database}", file=sys.stderr)
        print("Upewnij się, że bot ElderSuite został uruchomiony lub podaj ścieżkę parametrem --database.", file=sys.stderr)
        return 1

    # Open SQLite in read-only mode to prevent any modification to the scanner database
    database_uri = database.resolve().as_uri() + "?mode=ro"
    connection = sqlite3.connect(database_uri, uri=True)
    connection.row_factory = sqlite3.Row

    try:
        newest_run = find_newest_run(connection)
        if not newest_run:
            print(f"BŁĄD: Nie znaleziono żadnych skanów w bazie SQLite: {database}", file=sys.stderr)
            return 1

        run_id = newest_run["run_id"]
        original_state = newest_run["state"]
        ended_at = newest_run["ended_at"]
        force_completed = False

        # Handle unfinished/unclosed scans from the bot
        if original_state != STATE_COMPLETED or ended_at is None:
            max_obs_row = connection.execute(
                "SELECT max(observed_at) AS latest_obs FROM shop_observation WHERE run_id = ?",
                (run_id,),
            ).fetchone()
            inferred_ended_at = (
                max_obs_row["latest_obs"]
                if max_obs_row and max_obs_row["latest_obs"]
                else newest_run["started_at"]
            )

            print("======================================================================")
            print("UWAGA: Najnowszy skan nie został formalnie zamknięty przez bota!")
            print(f"  - Run ID:                  {run_id}")
            print(f"  - Stan w bazie SQLite:     {describe_state(original_state)}")
            print(f"  - Data zakończenia w bazie: BRAK (NULL)")
            print(f"  - Oszacowany koniec skanu: {inferred_ended_at}")
            print("======================================================================")

            if args.dry_run:
                print("[DRY-RUN] Skan wymaga wymuszenia statusu zakończonego (--force-completed).")
                force_completed = True
            else:
                prompt = input("Czy potraktować ten skan jako zakończony? [TAK/NIE]: ").strip()
                if prompt != "TAK":
                    print("Przerwano na życzenie użytkownika. Żadne dane nie zostały wysłane.")
                    return 0
                force_completed = True

        run_payload, observations, counts, is_forced = load_run_payload_and_observations(
            connection,
            newest_run,
            publishable=True,
            force_completed=force_completed,
        )

        batch_count = max(1, (len(observations) + args.batch_size - 1) // args.batch_size)

        # Display summary before sending anything
        print()
        print("======================================================================")
        print("                     PODSUMOWANIE SKANU")
        print("======================================================================")
        print(f"  Serwer gry:                 {args.server}")
        print(f"  Run ID:                    {run_id}")
        print(f"  Data rozpoczęcia:          {run_payload['startedAt']}")
        print(f"  Data zakończenia:          {run_payload['endedAt']}")
        print(f"  Stan początkowy w SQLite:  {describe_state(original_state)}")
        print(f"  Wymuszone zakończenie:     {'TAK' if is_forced else 'NIE'}")
        print(f"  Oznaczenie jako publiczny: TAK (publishable=True)")
        print(f"  Liczba sklepów:            {counts['observations']}")
        print(f"  Liczba ofert (przedmiotów): {counts['listings']}")
        print(f"  Liczba atrybutów (bonów):  {counts['attributes']}")
        print(f"  Liczba socketów (kamieni): {counts['sockets']}")
        print(f"  Liczba paczek (batch):     {batch_count} (po {args.batch_size} sklepów)")
        print("======================================================================")

        if args.dry_run:
            print("\n[DRY-RUN] Zakończono podgląd. Żadne zapytania HTTP nie zostały wysłane.")
            return 0

        # Production warning and explicit confirmation
        print("\nTen skan zostanie wysłany na PRODUKCJĘ i stanie się aktualnym publicznym marketem.")
        print(f"  - Liczba sklepów: {counts['observations']}")
        print(f"  - Liczba ofert:   {counts['listings']}")
        print(f"  - Run ID:         {run_id}\n")

        confirm = input("Wpisz TAK, aby wysłać skan na produkcję: ").strip()
        if confirm != "TAK":
            print("Anulowano. Żadne dane nie zostały wysłane na serwer.")
            return 0

        # Read security token from environment variable
        token_name = TOKEN_ENVIRONMENT[args.server]
        token = os.environ.get(token_name, "").strip()
        if not token:
            print(f"\nBŁĄD: Zmienna środowiskowa {token_name} nie jest ustawiona.", file=sys.stderr)
            print(f"Ustaw zmienną {token_name} przed uruchomieniem skryptu, np.:", file=sys.stderr)
            print(f'  PowerShell:  $env:{token_name} = "twoj_tajny_token"', file=sys.stderr)
            print(f"  CMD:         set {token_name}=twoj_tajny_token", file=sys.stderr)
            return 1

        print(f"\nRozpoczynanie wysyłki serwera {args.server} na {args.api_url}...")
        accepted_observations = 0
        accepted_listings = 0

        for index in range(batch_count):
            start = index * args.batch_size
            batch_observations = observations[start : start + args.batch_size]
            batch_id = f"sqlite-{run_id}-{index + 1:04d}"
            payload = {
                "sourceId": source_id,
                "batchId": batch_id,
                "runs": [run_payload] if index == 0 else [],
                "observations": batch_observations,
            }

            print(f"Wysyłanie {index + 1}/{batch_count}...")
            result = post_batch(args.api_url, token, payload, args.server)
            accepted_observations += result.get("importedObservations", 0)
            accepted_listings += result.get("importedListings", 0)

        print("\n======================================================================")
        print("Market zaktualizowany pomyślnie.")
        print("Szczegóły:")
        print(f"  - Run ID:            {run_id}")
        print(f"  - Wysłane sklepy:    {counts['observations']}")
        print(f"  - Wysłane oferty:    {counts['listings']}")
        print("======================================================================")
        return 0

    except (RuntimeError, sqlite3.Error, ValueError, KeyError) as error:
        print(f"\nBŁĄD podczas aktualizacji marketu: {error}", file=sys.stderr)
        print("Wysyłanie zostało przerwane. Ponowne uruchomienie skryptu po rozwiązaniu problemu jest w pełni bezpieczne (idempotentność).", file=sys.stderr)
        return 1
    finally:
        connection.close()


if __name__ == "__main__":
    raise SystemExit(main())
