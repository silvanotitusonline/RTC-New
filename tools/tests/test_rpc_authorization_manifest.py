import json
import subprocess
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
MANIFEST = ROOT / "supabase" / "security" / "rpc_authorization_manifest.json"
CHECKER = ROOT / "tools" / "security" / "verify_rpc_authorization_manifest.py"
ANON_MIGRATION = ROOT / "supabase" / "migrations" / "20260918040000_fail_closed_public_security_definer_execution.sql"
SQL_CHECK = ROOT / "supabase" / "security" / "production_boundary_check.sql"
INDEX_REVIEW = ROOT / "supabase" / "security" / "performance_index_review.sql"


def test_rpc_authorization_manifest_is_validated_by_the_checked_in_checker():
    result = subprocess.run(
        [sys.executable, str(CHECKER)],
        cwd=ROOT,
        check=False,
        capture_output=True,
        text=True,
    )
    assert result.returncode == 0, result.stderr or result.stdout
    assert "26 RPC-only tables" in result.stdout


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


def test_anonymous_security_definer_migration_is_fail_closed_and_preserves_signed_in_grants():
    text = ANON_MIGRATION.read_text(encoding="utf-8").lower()
    assert text.startswith("begin;")
    assert text.rstrip().endswith("commit;")
    assert "p.prosecdef = true" in text
    assert "from public, anon" in text
    assert "from public, anon, authenticated" not in text
    assert text.count("grant execute on function public.") == 10


def test_production_sql_boundary_check_is_read_only_and_covers_both_boundaries():
    text = SQL_CHECK.read_text(encoding="utf-8").lower()
    assert "pbzzfzfgwzwdstvnwzqu" in text
    assert "has_function_privilege" in text
    assert "has_table_privilege" in text
    assert "p.prosecdef" in text
    assert "rpc_only_table_boundary" in text
    assert "grant " not in text
    assert "revoke " not in text
    assert "insert " not in text
    assert "update " not in text
    assert "delete " not in text
    assert "drop " not in text
    assert "alter " not in text


def test_production_index_review_is_read_only_and_workload_ranked():
    text = INDEX_REVIEW.read_text(encoding="utf-8").lower()
    assert "pbzzfzfgwzwdstvnwzqu" in text
    assert "pg_constraint" in text
    assert "pg_index" in text
    assert "n_live_tup" in text
    assert "n_mod_since_analyze" in text
    assert "high-priority-review" in text
    assert "defer-until-workload" in text
    assert "create index" not in text
    assert "drop index" not in text
    assert "insert " not in text
    assert "update " not in text
    assert "delete " not in text
    assert "alter " not in text
