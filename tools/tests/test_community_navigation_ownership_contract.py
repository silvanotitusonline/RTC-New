from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[2]
NAV = (ROOT / "app/src/main/java/za/org/rtc/community/ui/navigation/RtcCommunityNavGraph.kt").read_text()
FEED = (ROOT / "app/src/main/java/za/org/rtc/community/feature/community/CommunityFeedScreen.kt").read_text()
DETAIL = (ROOT / "app/src/main/java/za/org/rtc/community/feature/community/CommunityPostDetailScreen.kt").read_text()


def _signature(source: str, function_name: str) -> str:
    match = re.search(rf"internal fun {function_name}\((.*?)\n\) \{{", source, re.S)
    assert match, f"Could not find {function_name} signature"
    return match.group(1)


def test_community_detail_navigation_has_single_scoped_owner():
    assert "viewModel.loadCommunityPostDetail" not in NAV
    assert "viewModel.communityPostDetail.collectAsStateWithLifecycle" not in NAV
    assert "viewModel.communityComments.collectAsStateWithLifecycle" not in NAV

    detail_signature = _signature(DETAIL, "CommunityPostDetailScreen")
    assert "postId: String" in detail_signature
    for obsolete in (
        "post: CommunityPost?",
        "comments: List<CommunityComment>",
        "isLoading: Boolean",
        "onCreateComment:",
        "onUpdateComment:",
        "onDeleteComment:",
        "onToggleLike:",
        "onRefreshMediaUrl:",
    ):
        assert obsolete not in detail_signature, obsolete


def test_community_feed_navigation_does_not_accept_legacy_feed_state_or_reaction_callbacks():
    feed_signature = _signature(FEED, "CommunityScreen")
    for obsolete in (
        "posts: List<CommunityPost>",
        "isRefreshing: Boolean",
        "onRefresh:",
        "onToggleLike:",
    ):
        assert obsolete not in feed_signature, obsolete

    community_route = re.search(
        r"composable\(RtcRoute\.COMMUNITY_FEED\) \{(.*?)\n        \}\n        composable\(RtcRoute\.MARKETPLACE_HOME\)",
        NAV,
        re.S,
    )
    assert community_route, "Could not isolate Community feed navigation route"
    route_body = community_route.group(1)
    assert "posts = posts" not in route_body
    assert "isRefreshing = isLiveContentLoading" not in route_body
    assert "onRefresh = viewModel::refreshLiveContent" not in route_body
    assert "onToggleLike = viewModel::toggleCommunityPostLike" not in route_body
