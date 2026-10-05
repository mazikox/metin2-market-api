"""Import a server-specific 19-column TSV catalog without rescanning shop offers."""
import argparse
import os
from pathlib import Path
from urllib.request import Request, urlopen
from urllib.error import HTTPError, URLError


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--server", required=True, choices=("pandora", "elder", "beavium"))
    parser.add_argument("--file", required=True, type=Path)
    parser.add_argument("--api-url", default="http://localhost:8080")
    args = parser.parse_args()
    token_env = {"pandora": "SCANNER_TOKEN", "elder": "SCANNER_TOKEN_ELDER", "beavium": "SCANNER_TOKEN_BEAVIUM"}[args.server]
    token = os.environ.get(token_env)
    if not token:
        parser.error(f"Set {token_env} before importing")
    try:
        body = args.file.read_text(encoding="utf-8-sig").encode("utf-8")
        request = Request(f"{args.api_url.rstrip('/')}/internal/v1/servers/{args.server}/imports/item-proto",
                          data=body, headers={"Content-Type": "text/tab-separated-values; charset=utf-8", "X-Scanner-Token": token})
        with urlopen(request, timeout=120) as response:
            print(response.read().decode("utf-8"))
    except HTTPError as e:
        parser.exit(1, f"HTTP {e.code}: {e.read().decode('utf-8', errors='replace')}\n")
    except (OSError, UnicodeError, URLError) as e:
        parser.exit(1, f"Import failed: {e}\n")


if __name__ == "__main__":
    main()
