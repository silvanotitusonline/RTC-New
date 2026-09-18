import json
import subprocess
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
MANIFEST = ROOT / "supabase" / "security" / "rpc_authorization_manifest.json"
CHECKER = ROOT / "tools" / "security" / "verify_rpc_authorization_manifest.py"


def test_rpc_authorization_manifest_is_validated_by_the_checked_in_checker():
    result = subprocess.run(
        [sys.executable, str(CHECKER)],
        cwd=ROOT,
        check=False,
        capture_output=True,
        text=True,
    )
    assert result.returncode == 0, result.stderr or result.stdout
    assert "25 RPC-only tables" in result.stdout


def test_manifest_has_only_explicit_public_read_anonymous_entries():
    manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))
    entries = manifest["anonymous_security_definer_allowlist"]
    assert len(entries) == 10
    assert {entry["classification"] for entry in entries} == {"public-read"}
    assert all(entry["reason"] for entry in entries)


def test_manifest_requires_zero_direct_grants_for_rpc_only_tables():
    manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))
    assert manifest["deployment_target"] == {
        "project_name": "RTC Community Production",
        "project_ref": "pbzzfzfgwzwdstvnwzqu",
    }
    boundary = manifest["required_rpc_only_table_boundary"]
    assert boundary["rls_enabled"] is True
    assert boundary["direct_grants"] == []
    assert boundary["required_migration"].endswith(
        "close_advisor_rpc_only_table_access.sql"
    )
