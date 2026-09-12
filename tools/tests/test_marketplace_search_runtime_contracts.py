from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SEARCH_MIGRATION = ROOT / "supabase/migrations/20260828095000_marketplace_search_pagination.sql"
REPAIR_MIGRATION = ROOT / "supabase/migrations/20260828101700_marketplace_search_operator_schema_fix.sql"


def test_marketplace_paginated_search_schema_qualifies_pg_trgm_operator():
    sql = SEARCH_MIGRATION.read_text()
    assert "OPERATOR(extensions.%)" in sql, (
        "SECURITY DEFINER Marketplace search uses an empty search_path, so pg_trgm operators "
        "must be schema-qualified."
    )


def test_marketplace_paginated_search_has_forward_repair_for_applied_nonprod_schema():
    assert REPAIR_MIGRATION.exists()
    sql = REPAIR_MIGRATION.read_text()
    assert "create or replace function public.marketplace_search_businesses_page" in sql
    assert "OPERATOR(extensions.%)" in sql
    assert "security definer" in sql.lower()
    assert "set search_path = ''" in sql
    assert "to authenticated" in sql
