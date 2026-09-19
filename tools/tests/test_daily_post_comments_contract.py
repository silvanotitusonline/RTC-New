from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
REPOSITORY = ROOT / "app/src/main/java/za/org/rtc/community/feature/dailypost/data/DailyPostRepository.kt"
VIEW_MODEL = ROOT / "app/src/main/java/za/org/rtc/community/feature/dailypost/ui/DailyPostViewModel.kt"
DETAIL = ROOT / "app/src/main/java/za/org/rtc/community/feature/dailypost/ui/DailyPostDetailScreen.kt"
COMMENTS = ROOT / "app/src/main/java/za/org/rtc/community/feature/dailypost/ui/DailyPostCommentsSection.kt"
NAV = ROOT / "app/src/main/java/za/org/rtc/community/ui/navigation/RtcCommunityNavGraph.kt"
DATABASE = ROOT / "app/src/main/java/za/org/rtc/community/data/local/RtcDatabase.kt"


def test_daily_post_comments_use_genuine_production_rpc_surface():
    source = REPOSITORY.read_text()
    for rpc in (
        '"daily_post_comments_page_v1"',
        '"daily_post_comment_create_v1"',
        '"daily_post_comment_update_v1"',
        '"daily_post_comment_delete_v1"',
        '"daily_post_comment_moderate_v1"',
    ):
        assert rpc in source
    assert "p_limit" in source
    assert "p_parent_id" in source
    assert "p_after_created_at" in source
    assert "p_after_id" in source


def test_daily_post_comment_viewmodel_reloads_authoritative_state_after_mutations():
    source = VIEW_MODEL.read_text()
    assert "loadCommentsInternal" in source
    assert "submitComment" in source
    assert "updateComment" in source
    assert "deleteComment" in source
    assert "moderateComment" in source
    assert "loadOlderComments" in source
    assert "commentsHasMore" in source
    assert "_commentPendingId.value = null" in source


def test_daily_post_detail_has_composer_and_owner_moderator_actions():
    detail = DETAIL.read_text()
    comments = COMMENTS.read_text()
    for label in ("Post comment", "Save changes", "Remove comment?", "Hide comment"):
        assert label in comments
    assert "DailyPostCommentsSection" in detail
    assert "onCreateComment" in detail
    assert "onModerateComment" in detail
    assert "comment.authorId == currentUserId" in comments


def test_daily_post_comments_refresh_while_article_is_open_and_cache_migrates():
    nav = NAV.read_text()
    database = DATABASE.read_text()
    assert "delay(15_000)" in nav
    assert "refreshComments(articleId)" in nav
    assert "onLoadOlderComments" in nav
    assert "RTC_DATABASE_MIGRATION_10_11" in database
    assert "commentCount" in database
