#!/usr/bin/env python3
"""BitCask client for CentralStation.

Usage:
  ./bitcask_client.sh --view-all
  ./bitcask_client.sh --view --key=SOME_KEY
  ./bitcask_client.sh --perf --clients=100
"""

from __future__ import annotations

import argparse
import csv
import json
import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path
from typing import Any

DEFAULT_BASE_URL = os.environ.get("BITCASK_BASE_URL", "http://localhost:8085")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="BitCask client")
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument("--view-all", action="store_true", help="Export all keys/values to CSV")
    mode.add_argument("--view", action="store_true", help="Print a single value to stdout")
    mode.add_argument("--perf", action="store_true", help="Spawn N threads to fetch all keys")

    parser.add_argument("--key", help="Key to view")
    parser.add_argument("--clients", type=int, help="Number of threads for --perf")
    parser.add_argument("--base-url", default=DEFAULT_BASE_URL, help=f"CentralStation base URL (default: {DEFAULT_BASE_URL})")
    return parser.parse_args()


def http_get(base_url: str, path: str, params: dict[str, str] | None = None) -> tuple[str, str]:
    query = urllib.parse.urlencode(params or {})
    url = f"{base_url.rstrip('/')}{path}"
    if query:
        url = f"{url}?{query}"

    request = urllib.request.Request(url, method="GET")
    with urllib.request.urlopen(request, timeout=30) as response:
        body = response.read().decode("utf-8")
        content_type = response.headers.get_content_type()
        return body, content_type


def write_csv(file_path: Path, data: dict[str, Any]) -> None:
    with file_path.open("w", newline="", encoding="utf-8") as f:
        writer = csv.writer(f)
        writer.writerow(["key", "value"])
        for key in sorted(data.keys()):
            value = data[key]
            writer.writerow([key, "" if value is None else str(value)])


def fetch_view_all(base_url: str) -> dict[str, Any]:
    body, content_type = http_get(base_url, "/bitcask/view-all")
    if content_type != "application/json":
        raise RuntimeError(f"Expected JSON from /bitcask/view-all, got {content_type}")
    parsed = json.loads(body)
    if not isinstance(parsed, dict):
        raise RuntimeError("Expected JSON object from /bitcask/view-all")
    return parsed


def command_view_all(base_url: str) -> int:
    data = fetch_view_all(base_url)
    filename = f"{int(time.time())}.csv"
    write_csv(Path(filename), data)
    print(filename)
    return 0


def command_view(base_url: str, key: str | None) -> int:
    if not key:
        print("--view requires --key=SOME_KEY", file=sys.stderr)
        return 2

    try:
        body, _ = http_get(base_url, "/bitcask/view", {"key": key})
        sys.stdout.write(body)
        if not body.endswith("\n"):
            sys.stdout.write("\n")
        return 0
    except urllib.error.HTTPError as exc:
        if exc.code == 404:
            print(f"Key not found: {key}", file=sys.stderr)
            return 1
        raise


def command_perf(base_url: str, clients: int | None) -> int:
    if clients is None or clients <= 0:
        print("--perf requires --clients=N where N > 0", file=sys.stderr)
        return 2

    timestamp = int(time.time())
    start = time.perf_counter()
    errors: list[str] = []

    def worker(thread_no: int) -> str:
        data = fetch_view_all(base_url)
        filename = Path(f"{timestamp}_thread_{thread_no}.csv")
        write_csv(filename, data)
        return str(filename)

    with ThreadPoolExecutor(max_workers=clients) as executor:
        futures = {executor.submit(worker, idx): idx for idx in range(1, clients + 1)}
        for future in as_completed(futures):
            try:
                future.result()
            except Exception as exc:  # pragma: no cover - surfaced in CLI
                errors.append(str(exc))

    elapsed = time.perf_counter() - start
    if errors:
        for error in errors:
            print(error, file=sys.stderr)
        return 1

    print(f"completed {clients} clients in {elapsed:.3f}s")
    return 0


def main() -> int:
    args = parse_args()

    try:
        if args.view_all:
            return command_view_all(args.base_url)
        if args.view:
            return command_view(args.base_url, args.key)
        if args.perf:
            return command_perf(args.base_url, args.clients)
        return 2
    except urllib.error.HTTPError as exc:
        print(f"HTTP error {exc.code}: {exc.read().decode('utf-8', errors='replace')}", file=sys.stderr)
        return 1
    except urllib.error.URLError as exc:
        print(f"Connection error: {exc.reason}", file=sys.stderr)
        return 1
    except Exception as exc:
        print(f"Unexpected error: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())

