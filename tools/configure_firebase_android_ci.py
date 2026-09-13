#!/usr/bin/env python3
"""Restore the Android client config to the existing GitHub Actions secret.

Run after `gh auth login`, using a repository administrator account:
  python3 tools/configure_firebase_android_ci.py --file /path/to/google-services.json

This sends the original file, base64 encoded, through stdin to `gh secret set`.
It never prints client credentials or accepts a Firebase Admin service account.
GitHub CLI encrypts the value before submission:
https://cli.github.com/manual/gh_secret_set
"""

import argparse
import base64
import json
from pathlib import Path
import shutil
import subprocess
import sys


def client_config_bytes(path: Path) -> bytes:
    raw = path.read_bytes()
    if len(raw) > 32_000:
        raise ValueError("Android client configuration exceeds the supported size.")
    config = json.loads(raw)
    if not isinstance(config, dict) or any(
        key in config for key in ("private_key", "private_key_id", "client_email")
    ):
        raise ValueError("Expected Android google-services.json, not an Admin service account.")
    clients = config.get("client", [])
    if not isinstance(clients, list) or not any(
        isinstance(client, dict)
        and client.get("client_info", {}).get("android_client_info", {}).get("package_name")
        == "za.org.rtc.community"
        for client in clients
    ):
        raise ValueError("Configuration has no Android client for za.org.rtc.community.")
    return raw


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--file", required=True, type=Path)
    parser.add_argument("--repo", default="silvanotitusonline/RTC-New")
    parser.add_argument("--check-only", action="store_true")
    args = parser.parse_args()
    try:
        raw = client_config_bytes(args.file)
    except (OSError, ValueError, TypeError, AttributeError):
        print("Invalid Android client file; supply google-services.json containing za.org.rtc.community.", file=sys.stderr)
        return 1
    if args.check_only:
        print("Android client configuration is valid for za.org.rtc.community; no remote changes made.")
        return 0
    if not shutil.which("gh"):
        print("Install GitHub CLI and run gh auth login with repository administration access.", file=sys.stderr)
        return 1
    try:
        subprocess.run(
            ["gh", "secret", "set", "GOOGLE_SERVICES_JSON_BASE64", "--repo", args.repo],
            input=base64.b64encode(raw).decode("ascii"),
            text=True,
            check=True,
        )
    except subprocess.CalledProcessError:
        print("GitHub did not confirm the secret update; check account and repository permissions.", file=sys.stderr)
        return 1
    print("Updated GOOGLE_SERVICES_JSON_BASE64. Existing signing and Supabase release secrets are still required.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
