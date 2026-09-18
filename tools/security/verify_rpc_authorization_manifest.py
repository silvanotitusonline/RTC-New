#!/usr/bin/env python3
"""Validate the checked-in Supabase RPC authorization boundary.

This is intentionally source-only. It does not connect to a database and never
attempts to apply migrations. A live reconciliation may compare the manifest's
expected rows with pg_proc, information_schema.role_routine_grants, and
pg_policies in the approved non-production project.
"""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
MANIFEST = ROOT / "supabase" / "security" / "rpc_authorization_manifest.json"
MIGRATION = ROOT / "supabase" / "migrations" / "20260918001000_close_advisor_rpc_only_table_access.sql"


def fail(message: str) -> None:
    raise AssertionError(message)


def main() -> int:
    manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))
    migration = MIGRATION.read_text(encoding="utf-8").lower()

    if manifest.get("manifest_version") != 1:
        fail("unsupported manifest version")

    entries = manifest.get("anonymous_security_definer_allowlist", [])
    if not entries:
        fail("anonymous SECURITY DEFINER allowlist must not be empty")

    identities = [(entry["name"], entry["identity_arguments"]) for entry in entries]
    if len(identities) != len(set(identities)):
        fail("anonymous SECURITY DEFINER allowlist contains duplicate identities")

    allowed_classifications = {"public-read"}
    for entry in entries:
        if entry.get("classification") not in allowed_classifications:
            fail(f"unsupported anonymous classification: {entry}")
        if not entry.get("reason"):
            fail(f"missing justification: {entry}")
        if not re.fullmatch(r"[a-z0-9_]+", entry["name"]):
            fail(f"unsafe function name: {entry['name']}")
        if "insert" in entry["reason"].lower() or "update" in entry["reason"].lower():
            fail(f"anonymous allowlist entry is not read-only by description: {entry['name']}")

    tables = manifest.get("rpc_only_tables", [])
    if len(tables) != 25 or len(tables) != len(set(tables)):
        fail("RPC-only table list must contain 25 unique tables")
    for table in tables:
        if not re.fullmatch(r"[a-z0-9_]+", table):
            fail(f"unsafe table name: {table}")
        if f"public.{table}" not in migration:
            fail(f"RPC-only table is missing from the hardening migration: {table}")
    if "revoke all on table" not in migration:
        fail("hardening migration must revoke direct table privileges")
    if "from public, anon, authenticated" not in migration:
        fail("hardening migration must cover public, anon, and authenticated roles")
    if not migration.strip().startswith("begin;") or not migration.strip().endswith("commit;"):
        fail("hardening migration must be transactional")

    boundary = manifest.get("required_rpc_only_table_boundary", {})
    if boundary.get("rls_enabled") is not True:
        fail("manifest must require RLS for RPC-only tables")
    if boundary.get("direct_grants") != []:
        fail("manifest must require no direct grants for RPC-only tables")

    print(
        f"Validated {len(entries)} anonymous allowlisted RPCs and "
        f"{len(tables)} RPC-only tables against {MIGRATION.name}."
    )
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (AssertionError, KeyError, json.JSONDecodeError) as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        raise SystemExit(1)
