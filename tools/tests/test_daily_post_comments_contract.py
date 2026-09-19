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
    repository = REPOSITORY.read_text()
    migration = (ROOT / "supabase/migrations/20260919090000_daily_post_comments_realtime_and_visible_count.sql").read_text()
    assert "observeComments(articleId)" in nav
    assert "stopObservingComments()" in nav
    assert "observeCommentChanges" in repository
    assert "daily_post_comments" in repository
    assert "daily_post_comment_count_sync" in migration
    assert "UPDATE" in migration
    assert "state = 'VISIBLE'" in migration
    assert "onLoadOlderComments" in nav
    assert "RTC_DATABASE_MIGRATION_10_11" in database
    assert "commentCount" in database


def test_daily_post_comment_reporting_is_authenticated_rate_limited_and_rpc_only():
    repository = REPOSITORY.read_text()
    comments = COMMENTS.read_text()
    detail = (ROOT / "app/src/main/java/za/org/rtc/community/feature/dailypost/ui/DailyPostDetailScreen.kt").read_text()
    migration = (ROOT / "supabase/migrations/20260919095000_daily_post_comment_reporting.sql").read_text()
    manifest = (ROOT / "supabase/security/rpc_authorization_manifest.json").read_text()
    assert 'daily_post_comment_report_v1' in repository
    assert 'onReportComment' in detail
    assert 'Report comment' in comments
    assert "auth.uid() is null" in migration
    assert "recent_count >= 10" in migration
    assert "daily_post_comment_reports_rpc_only" in migration
    assert '"daily_post_comment_reports"' in manifest


def test_daily_post_comment_reads_match_visible_count_contract():
    migration = (ROOT / "supabase/migrations/20260919096000_daily_post_comment_visible_read_boundary.sql").read_text()
    assert "c.state = 'VISIBLE'" in migration
    assert "daily_post_comments_page_v1" in migration
    assert "daily_post_comments_page_v2" in migration
    assert "p_after_created_at" in migration
    assert "p_before_created_at" in migration


def test_daily_post_realtime_subscription_is_post_scoped_and_route_owned():
    repository = REPOSITORY.read_text()
    view_model = (ROOT / "app/src/main/java/za/org/rtc/community/feature/dailypost/ui/DailyPostViewModel.kt").read_text()
    nav = NAV.read_text()
    assert 'channel("daily-post-comments-$articleId")' in repository
    assert 'postId == articleId' in repository
    assert 'action.record["post_id"]' in repository
    assert 'postgresChangeFlow<PostgresAction>' in repository
    assert 'debounce(250)' in view_model
    assert 'stopObservingComments()' in view_model
    assert 'awaitCancellation()' in nav
    assert 'finally' in nav


def test_daily_post_moderation_and_cursor_contracts_are_bounded():
    repository = REPOSITORY.read_text()
    view_model = (ROOT / "app/src/main/java/za/org/rtc/community/feature/dailypost/ui/DailyPostViewModel.kt").read_text()
    assert 'daily_post_comment_moderate_v1' in repository
    assert 'daily_post_comment_delete_v1' in repository
    assert 'p_after_created_at' in repository
    assert 'p_after_id' in repository
    assert 'limit.coerceIn(1, 200)' in repository
    assert 'commentsHasMore' in view_model or '_commentsHasMore' in view_model
    assert 'distinctBy { it.id }' in view_model


def test_daily_post_operational_health_and_release_gate_are_source_controlled():
    repository = REPOSITORY.read_text()
    view_model = VIEW_MODEL.read_text()
    detail = DETAIL.read_text()
    migration = (ROOT / "supabase/migrations/20260919098000_daily_post_operational_health.sql").read_text()
    release_gate = (ROOT / "tools/tests/run_release_gate.sh").read_text()
    load_test = (ROOT / "tools/tests/run_daily_post_load_test.sh").read_text()
    assert 'withRpcMetrics("comments_page")' in repository
    for rpc in ("comment_create", "comment_delete", "comment_moderate", "comment_report"):
        assert f'withRpcMetrics("{rpc}")' in repository
    assert 'AUTHORIZATION_FAILURE' in repository
    assert 'LiveUpdateStatus.UNAVAILABLE' in view_model
    assert 'liveUpdatesAvailable' in detail
    for function_name in (
        'daily_post_rpc_metric_record_v1', 'daily_post_rpc_health_v1',
        'daily_post_report_queue_v1', 'daily_post_report_transition_v1',
        'daily_post_report_summary_v1', 'daily_post_count_drift_check_v1',
    ):
        assert function_name in migration
    assert 'daily_post_comment_reports_reporter_idx' in migration
    assert 'daily_post_comment_reports_reviewed_by_idx' in migration
    assert 'daily_post_audit_events_actor_idx' in migration
    assert 'run_contract_tests.py' in release_gate
    assert 'refusing production load test' in load_test


def test_daily_post_load_runner_requires_staging_objects_and_discovers_fixture():
    load_test = (ROOT / "tools/tests/run_daily_post_load_test.sh").read_text()
    assert "SUPABASE_TEST_DB_URL" in load_test
    assert "SUPABASE_TEST_PROJECT_REF" in load_test
    assert "PGSSLMODE=\"${PGSSLMODE:-require}\"" in load_test
    assert "to_regclass('public.daily_posts')" in load_test
    assert "to_regprocedure('public.daily_post_count_drift_check_v1()')" in load_test
    assert "where state = 'PUBLISHED'" in load_test
    assert "No published Daily Post fixture" in load_test
    assert "select c.id, c.post_id, c.state, c.created_at" in load_test


def test_daily_post_rest_load_runner_is_bounded_and_non_production_only():
    load_test = (ROOT / "tools/tests/run_daily_post_rest_load_test.py").read_text()
    assert "LOAD_CLIENTS" in load_test
    assert "LOAD_REQUESTS" in load_test
    assert "refusing REST load test against the production project" in load_test
    assert "daily_post_comments_page_v2" in load_test
    assert "daily_post_comments" in load_test
    assert "BASE_URL.endswith(\"/rest/v1\")" in load_test


def test_daily_post_drift_deployment_is_idempotent_and_service_role_only():
    migration = (ROOT / "supabase/migrations/20260919111500_daily_post_count_drift_check_deploy.sql").read_text()
    assert "create table if not exists public.daily_post_count_drift_events" in migration
    assert "create unique index if not exists daily_post_count_drift_open_idx" in migration
    assert "create or replace function public.daily_post_count_drift_check_v1()" in migration
    assert "revoke all on function public.daily_post_count_drift_check_v1()" in migration
    assert "grant execute on function public.daily_post_count_drift_check_v1()" in migration
    assert "to service_role" in migration
    assert "cron.schedule" in migration
