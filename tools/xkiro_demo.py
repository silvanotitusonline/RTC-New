#!/usr/bin/env python3
"""Small stdlib-only xKiro API demo.

Examples:
  python3 tools/xkiro_demo.py --smoke
  XKIRO_API_KEY=sk-xt-... python3 tools/xkiro_demo.py --live
  python3 tools/xkiro_demo.py --examples

The script never prints the API key. It uses the documented OpenAI-compatible
xKiro endpoints and keeps optional/paid features behind explicit flags.
"""
from __future__ import annotations

import argparse
import json
import os
import sys
import urllib.error
import urllib.request
from typing import Any

BASE_URL = os.getenv("XKIRO_BASE_URL", "https://api.xkiro.com/v1").rstrip("/")
KEY = os.getenv("XKIRO_API_KEY") or os.getenv("xKiro")


def request(path: str, method: str = "GET", payload: Any | None = None,
            auth: bool = False, headers: dict[str, str] | None = None) -> tuple[int, str, dict[str, str]]:
    body = None
    req_headers = {"Accept": "application/json", **(headers or {})}
    if payload is not None:
        body = json.dumps(payload).encode()
        req_headers["Content-Type"] = "application/json"
    if auth:
        if not KEY:
            raise RuntimeError("No API key found. Set XKIRO_API_KEY (or xKiro in this environment).")
        req_headers["Authorization"] = f"Bearer {KEY}"
    req = urllib.request.Request(f"{BASE_URL}{path}", data=body, headers=req_headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=95) as response:
            return response.status, response.read().decode(errors="replace"), dict(response.headers)
    except urllib.error.HTTPError as exc:
        return exc.code, exc.read().decode(errors="replace"), dict(exc.headers)
    except urllib.error.URLError as exc:
        return 0, f"network error: {exc.reason}", {}


def pretty(raw: str) -> str:
    try:
        return json.dumps(json.loads(raw), indent=2)
    except json.JSONDecodeError:
        return raw


def choose_model(raw: str) -> str | None:
    try:
        data = json.loads(raw)
        models = data.get("data", [])
        if models:
            return models[0].get("id")
    except (TypeError, json.JSONDecodeError):
        pass
    return None


def smoke() -> int:
    print(f"xKiro base URL: {BASE_URL}")
    status, raw, _ = request("/models")
    print(f"GET /models -> HTTP {status}")
    if status == 200:
        model = choose_model(raw)
        print(f"Public model catalog: OK ({model or 'no model id found'})")
        return 0
    if status == 403 and "browser_signature_banned" in raw:
        print("Edge diagnostic: Cloudflare Error 1010 blocked this client signature before the API was reached.")
    print(pretty(raw)[:2000])
    return 1


def live(model: str | None = None) -> int:
    if not KEY:
        print("LIVE SKIPPED: set XKIRO_API_KEY to a real xKiro key.")
        return 2
    status, catalog, _ = request("/models")
    selected = model or choose_model(catalog)
    print(f"GET /models -> HTTP {status}; selected model: {selected or '(none)'}")
    if not selected:
        print("Cannot run chat demo without a model ID; pass --model vendor/model when discovery is blocked.")
        return 1
    payload = {"model": selected, "messages": [{"role": "user", "content": "Reply with exactly: xKiro works."}]}
    status, raw, _ = request("/chat/completions", "POST", payload, auth=True)
    print(f"POST /chat/completions -> HTTP {status}")
    print(pretty(raw)[:4000])
    if status == 403:
        print("\nCredential diagnostic: xKiro rejected the configured key. Create/copy a key from the xKiro API Keys page.")
        return 1
    return 0 if 200 <= status < 300 else 1


def examples() -> None:
    examples = {
        "chat": {"POST": "/v1/chat/completions", "body": {"model": "vendor/model", "messages": [{"role": "user", "content": "Hello"}]}},
        "streaming": {"body_addition": {"stream": True}, "note": "Parse server-sent data: chunks until data: [DONE]."},
        "tool_calling": {"body_addition": {"tools": [{"type": "function", "function": {"name": "get_weather", "description": "Get weather", "parameters": {"type": "object", "properties": {"city": {"type": "string"}}, "required": ["city"]}}}]}},
        "vision": {"note": "Send a user content array containing text plus an image_url data/public URL."},
        "speech": {"POST": "/v1/audio/speech", "body": {"model": "vendor/speech-model", "input": "Hello from xKiro", "voice": "alloy", "format": "mp3"}},
        "image_generation": {"POST": "/v1/images/generations", "body": {"model": "vendor/image-model", "prompt": "A geometric community logo"}, "note": "Returns a job ID; poll GET /v1/images/generations/{id}."},
        "usage": {"GET": "/v1/usage", "note": "Key required; free to call and useful for quota diagnostics."},
    }
    print(json.dumps(examples, indent=2))


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument("--smoke", action="store_true", help="Call public GET /v1/models.")
    group.add_argument("--live", action="store_true", help="Run a minimal authenticated chat request.")
    group.add_argument("--examples", action="store_true", help="Print feature request templates without calling them.")
    parser.add_argument("--model", help="Explicit vendor/model ID for --live.")
    args = parser.parse_args()
    if args.smoke:
        return smoke()
    if args.live:
        return live(args.model)
    examples()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
