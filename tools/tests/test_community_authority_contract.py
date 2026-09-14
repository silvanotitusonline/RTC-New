from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
COMMUNITY = ROOT / "app/src/main/java/za/org/rtc/community/feature/community"


def _read(name: str) -> str:
    return (COMMUNITY / name).read_text(encoding="utf-8")


def test_community_feed_pagination_is_cursor_only():
    repository = _read("SupabaseCommunityRepository.kt")
    load_feed_page = repository.split("override suspend fun loadFeedPage", 1)[1].split(
        "override suspend fun loadPost", 1
    )[0]

    assert "community_post_page_v3" in load_feed_page
    assert "community_post_page_v2" in load_feed_page
    assert 'from("community_post_feed")' not in load_feed_page, (
        "loadFeedPage must fail closed to cursor-aware RPCs/cache instead of restarting "
        "from the first offset page"
    )
    assert "range(0," not in load_feed_page, (
        "loadFeedPage must not discard CommunityCursor through an offset/range fallback"
    )
    assert "postsAfterCommunityCursor" in load_feed_page, (
        "cached feed fallback must apply the same composite cursor boundary before building a page"
    )
