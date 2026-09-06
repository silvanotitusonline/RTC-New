from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SEARCH_MIGRATION = ROOT / "supabase/migrations/20260828095000_marketplace_search_pagination.sql"
SECURITY_MIGRATION = ROOT / "supabase/migrations/20260828092000_marketplace_authorization_hardening.sql"
MODELS = ROOT / "app/src/main/java/za/org/rtc/community/feature/marketplace/domain/MarketplaceModels.kt"
DISCOVERY_VIEW_MODEL = ROOT / "app/src/main/java/za/org/rtc/community/feature/marketplace/presentation/viewmodel/MarketplaceDiscoveryViewModel.kt"


def _read(path: Path) -> str:
    assert path.exists(), f"Missing required integration file: {path}"
    return path.read_text(encoding="utf-8")


def test_marketplace_pagination_rpc_preserves_hardened_execution_boundary():
    sql = _read(SEARCH_MIGRATION).lower()
    assert sql.lstrip().startswith("-- additive marketplace search pagination")
    assert "begin;" in sql and "commit;" in sql
    assert "security definer" in sql
    assert "set search_path = ''" in sql
    assert "perform private.marketplace_actor();" in sql
    assert "revoke all on function public.marketplace_search_businesses_page" in sql
    assert "from public, anon" in sql
    assert "to authenticated" in sql


def test_marketplace_pagination_rpc_bounds_resource_amplifying_inputs():
    sql = _read(SEARCH_MIGRATION)
    assert "char_length(coalesce(p_query, '')) > 160" in sql
    assert "char_length(coalesce(p_locality, '')) > 120" in sql
    assert "v_offset > 5000" in sql
    assert "v_limit integer := greatest(1, least(coalesce(p_limit, 20), 50))" in sql
    assert "v_radius integer := greatest(100, least(coalesce(p_radius_metres, 50000), 50000))" in sql
    assert "v_sort not in ('RECOMMENDED', 'DISTANCE', 'TOP_RATED', 'NEWEST', 'NAME')" in sql
    assert "p_min_rating not between 0 and 5" in sql


def test_marketplace_search_state_deduplicates_cross_page_business_ids():
    source = _read(MODELS)
    assert "items = (items + page.items).distinctBy { it.id }" in source
    assert "items = page.items.distinctBy { it.id }" in source


def test_marketplace_search_generation_blocks_stale_page_results():
    source = _read(DISCOVERY_VIEW_MODEL)
    assert "private var searchGeneration = 0L" in source
    assert "val generation = searchGeneration" in source
    assert "val generation = ++searchGeneration" in source
    assert "generation == searchGeneration" in source
    assert "current.filters == state.filters" in source
    assert "current.isLoadingMore" in source


def test_marketplace_area_inheritance_refreshes_only_inherited_searches():
    source = _read(DISCOVERY_VIEW_MODEL)
    assert "private var searchUsesSelectedArea = false" in source
    assert "searchUsesSelectedArea = explicitLocality == null" in source
    assert "if (searchUsesSelectedArea)" in source
    assert "searchCriteria.value = searchCriteria.value?.copy(locality = _area.value)" in source


def test_marketplace_security_baseline_remains_present_after_feature_port():
    sql = _read(SECURITY_MIGRATION)
    assert "v_role text := upper(trim(coalesce(p_role, '')));" in sql
    assert "v_invite.role not in ('MANAGER', 'EDITOR')" in sql
    assert "lower(v_invite.email) <> v_actor_email" in sql
    assert "revoke all on function public.marketplace_invite_member" in sql
