from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
REGISTRY = ROOT / "supabase" / "tests" / "security_definer_classification.sql"
MIGRATIONS = ROOT / "supabase" / "migrations"


def test_security_definer_registry_is_exhaustive_and_classified():
    assert REGISTRY.exists(), "Foundation must classify executable SECURITY DEFINER functions."
    text = REGISTRY.read_text(encoding="utf-8").lower()
    assert "p.prosecdef" in text
    assert "has_function_privilege('authenticated'" in text
    assert "fixed_search_path" in text
    assert "contains_caller_or_authority_guard" in text
    for disposition in ("intentional", "harden", "revoke", "replace"):
        assert disposition in text


def test_security_definer_registry_fails_closed_on_unreviewed_set_changes():
    text = REGISTRY.read_text(encoding="utf-8").lower()
    assert "select plan(" in text
    assert "exposed_fingerprint" in text
    assert "md5(string_agg(signature" in text
    assert "66cda900664e76083670f8186087aece" in text
    assert "182" in text
    assert "missing_fixed_search_path" in text
    assert "select * from finish()" in text
    assert "rollback;" in text


def test_edge_rate_limit_actor_foreign_key_has_forward_covering_index():
    candidates = list(MIGRATIONS.glob("*_edge_rate_limit_actor_fk_reconciliation.sql"))
    assert len(candidates) == 1, "Expected one forward reconciliation migration generated for the actor FK index."
    text = candidates[0].read_text(encoding="utf-8").lower()
    assert "create index if not exists edge_function_rate_limits_actor_id_idx" in text
    assert "on public.edge_function_rate_limits(actor_id)" in text
