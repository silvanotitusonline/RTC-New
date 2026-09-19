#!/usr/bin/env python3
"""Bounded concurrent Daily Post load test using Supabase REST."""
from __future__ import annotations

import concurrent.futures
import json
import os
import statistics
import time
import urllib.error
import urllib.parse
import urllib.request
from collections import Counter


PROJECT_REF = os.environ.get("SUPABASE_TEST_PROJECT_REF", "")
BASE_URL = os.environ["SUPABASE_URL"].rstrip("/")
if BASE_URL.endswith("/rest/v1"):
    BASE_URL = BASE_URL[:-len("/rest/v1")]
API_KEY = os.environ["SUPABASE_KEY"]
POST_ID = os.environ["DAILY_POST_LOAD_POST_ID"]
MODE = os.environ.get("LOAD_MODE", "table").lower()
CLIENTS = int(os.environ.get("LOAD_CLIENTS", "32"))
REQUESTS = int(os.environ.get("LOAD_REQUESTS", "600"))
TIMEOUT = float(os.environ.get("LOAD_TIMEOUT_SECONDS", "15"))

if PROJECT_REF in {"pbzzfzfgwzwdstvnwzqu", "prod", "production"}:
    raise SystemExit("refusing REST load test against the production project")
if not POST_ID:
    raise SystemExit("DAILY_POST_LOAD_POST_ID is required")
if MODE not in {"table", "rpc"}:
    raise SystemExit("LOAD_MODE must be table or rpc")
if CLIENTS < 1 or CLIENTS > 64:
    raise SystemExit("LOAD_CLIENTS must be between 1 and 64")
if REQUESTS < 1 or REQUESTS > 5000:
    raise SystemExit("LOAD_REQUESTS must be between 1 and 5000")

if MODE == "rpc":
    URL = f"{BASE_URL}/rest/v1/rpc/daily_post_comments_page_v2"
    METHOD = "POST"
    PAYLOAD = json.dumps(
        {
            "p_post_id": POST_ID,
            "p_before_created_at": None,
            "p_before_id": None,
            "p_limit": 50,
        }
    ).encode("utf-8")
else:
    query = urllib.parse.urlencode(
        {
            "select": "id,post_id,author_id,state,created_at",
            "post_id": f"eq.{POST_ID}",
            "state": "eq.VISIBLE",
            "order": "created_at.desc,id.desc",
            "limit": "50",
        }
    )
    URL = f"{BASE_URL}/rest/v1/daily_post_comments?{query}"
    METHOD = "GET"
    PAYLOAD = None

HEADERS = {
    "apikey": API_KEY,
    "Authorization": f"Bearer {API_KEY}",
    "Content-Type": "application/json",
}


def request_once(_: int) -> tuple[float, int, int]:
    started = time.perf_counter()
    request = urllib.request.Request(URL, data=PAYLOAD, headers=HEADERS, method=METHOD)
    try:
        with urllib.request.urlopen(request, timeout=TIMEOUT) as response:
            body = response.read()
            status = response.status
            rows = 0
            if status == 200:
                decoded = json.loads(body or b"[]")
                rows = len(decoded) if isinstance(decoded, list) else 1
            return time.perf_counter() - started, status, rows
    except urllib.error.HTTPError as error:
        error.read()
        return time.perf_counter() - started, error.code, 0
    except (TimeoutError, urllib.error.URLError):
        return time.perf_counter() - started, 0, 0


def percentile(values: list[float], fraction: float) -> float:
    if not values:
        return 0.0
    ordered = sorted(values)
    index = min(len(ordered) - 1, max(0, int(round((len(ordered) - 1) * fraction))))
    return ordered[index]


def main() -> None:
    started = time.perf_counter()
    with concurrent.futures.ThreadPoolExecutor(max_workers=CLIENTS) as executor:
        results = list(executor.map(request_once, range(REQUESTS)))
    elapsed = time.perf_counter() - started

    latencies = [duration for duration, _, _ in results]
    statuses = Counter(status for _, status, _ in results)
    successes = sum(1 for _, status, _ in results if status == 200)
    returned_rows = sum(rows for _, _, rows in results)
    throughput = REQUESTS / elapsed if elapsed else 0.0

    print(json.dumps({
        "project_ref": PROJECT_REF,
        "post_id": POST_ID,
        "mode": MODE,
        "requests": REQUESTS,
        "concurrency": CLIENTS,
        "elapsed_seconds": round(elapsed, 3),
        "throughput_requests_per_second": round(throughput, 2),
        "returned_rows": returned_rows,
        "successes": successes,
        "failures": REQUESTS - successes,
        "status_counts": dict(sorted(statuses.items(), key=lambda item: str(item[0]))),
        "latency_ms": {
            "min": round(min(latencies) * 1000, 3),
            "median": round(statistics.median(latencies) * 1000, 3),
            "p95": round(percentile(latencies, 0.95) * 1000, 3),
            "p99": round(percentile(latencies, 0.99) * 1000, 3),
            "max": round(max(latencies) * 1000, 3),
        },
    }, sort_keys=True))

    if successes != REQUESTS:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
