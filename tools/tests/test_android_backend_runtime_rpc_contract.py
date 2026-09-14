from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
MIGRATION = ROOT / "supabase" / "migrations" / "20260914103000_restore_android_runtime_rpc_contracts.sql"
COMMUNITY_REPO = ROOT / "app" / "src" / "main" / "java" / "za" / "org" / "rtc" / "community" / "feature" / "community" / "AuthoritativeCommunityRepository.kt"
SUPABASE_REPO = ROOT / "app" / "src" / "main" / "java" / "za" / "org" / "rtc" / "community" / "feature" / "community" / "SupabaseCommunityRepository.kt"


def test_android_runtime_rpcs_are_source_controlled_and_shape_compatible():
    migration = MIGRATION.read_text(encoding="utf-8")
    authoritative = COMMUNITY_REPO.read_text(encoding="utf-8")
    supabase = SUPABASE_REPO.read_text(encoding="utf-8")

    required_rpcs = (
        "community_post_page_v3",
        "toggle_community_post_like",
        "toggle_community_post_reaction",
        "repost_community_post",
        "bookmark_community_post",
        "unbookmark_community_post",
        "ops_workspace_summary_v1",
        "ops_list_eligible_assignees_v1",
    )
    for rpc in required_rpcs:
        assert rpc in migration, f"{rpc} must be restored by a forward migration"

    for rpc in (
        "toggle_community_post_like",
        "toggle_community_post_reaction",
        "repost_community_post",
        "bookmark_community_post",
        "unbookmark_community_post",
    ):
        assert rpc in authoritative, f"Android authoritative repository must call {rpc}"

    assert "community_post_page_v3" in supabase
    assert "community_post_page_v2" in supabase
    assert "returns table(liked boolean, like_count integer)" in migration.lower()
    assert "returns table(reposted boolean, repost_count integer)" in migration.lower()
    assert "returns table(bookmarked boolean, bookmark_count integer)" in migration.lower()
    assert "(f.created_at, f.id) < (p_before_created_at, p_before_id)" in migration
    assert "security invoker" in migration.lower()
    assert "revoke all on function public.community_post_page_v3" in migration.lower()
