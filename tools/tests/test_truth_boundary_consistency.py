from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def read(path: str) -> str:
    return (ROOT / path).read_text()


def test_public_reports_are_bound_to_server_authoritative_repository():
    module = read("app/src/main/java/za/org/rtc/community/di/AppModule.kt")
    authoritative = read(
        "app/src/main/java/za/org/rtc/community/feature/publicreports/data/AuthoritativePublicReportRepository.kt"
    )
    assert "providePublicReportRepository" in module
    assert "AuthoritativePublicReportRepository" in module
    assert "PublicReportMockData" not in authoritative
    assert "The authoritative Public Reports dashboard returned no row." in authoritative
    for rpc in [
        "PublicReportRpcContract.CREATE",
        "PublicReportRpcContract.ADD_COMMENT",
        "PublicReportRpcContract.SET_VOTE",
        "PublicReportRpcContract.VERIFIED_DASHBOARD",
    ]:
        assert rpc in authoritative


def test_community_mutations_cross_server_boundary_before_final_local_state():
    module = read("app/src/main/java/za/org/rtc/community/di/AppModule.kt")
    authoritative = read(
        "app/src/main/java/za/org/rtc/community/feature/community/AuthoritativeCommunityRepository.kt"
    )
    assert "AuthoritativeCommunityRepository" in module
    assert 'function = "create_community_comment"' in authoritative
    assert 'put("p_parent_comment_id", it)' in authoritative
    assert 'function = "edit_community_comment"' in authoritative
    assert 'function = "delete_community_comment"' in authoritative
    assert 'function = "delete_community_post"' in authoritative
    assert 'function = "moderate_community_comment_v1"' in authoritative
    assert "runAuthoritativeOptimisticMutation" in authoritative


def test_community_mock_fixtures_cannot_cross_release_read_boundary():
    authoritative = read(
        "app/src/main/java/za/org/rtc/community/feature/community/AuthoritativeCommunityRepository.kt"
    )
    assert "BuildConfig.DEBUG" in authoritative
    assert 'const val MOCK_POST_PREFIX = "mock_post_"' in authoritative
    assert "rejectSyntheticPosts(page.items)" in authoritative
    assert "post?.isSyntheticCommunityFixture()" in authoritative
    assert "comments.any { it.postId.startsWith(MOCK_POST_PREFIX) }" in authoritative
    assert "rejectSyntheticPosts(posts)" in authoritative


def test_authentication_requires_real_supabase_session_and_server_admin_guard():
    auth = read("app/src/main/java/za/org/rtc/community/app/RtcAuthenticationCoordinator.kt")
    admin_guard = read(
        "app/src/main/java/za/org/rtc/community/feature/administration/security/AdminGuard.kt"
    )
    assert "supabase.auth.currentUserOrNull()" in auth
    assert "requireVerifiedSession()" in auth
    assert "supabase.auth.signUpWith(Email" in auth
    assert "repository.signUpWithEmail(" not in auth
    assert 'supabase.postgrest.rpc("admin_access_guard")' in admin_guard
    for forbidden in ["cachedIsAdmin", "isAdminEmail", "userMetadata", "appMetadata"]:
        assert forbidden not in admin_guard


def test_notification_dispatcher_excludes_resident_role():
    dispatcher = read("supabase/functions/realtime-notification-dispatcher/index.ts")
    assert 'const NOTIFICATION_DISPATCH_ROLES = ["SYSTEM_ADMIN", "CONTENT_EDITOR"] as const;' in dispatcher
    assert "APP_ROLES" not in dispatcher
    assert "REALTIME_DISPATCH_FAILED" in dispatcher


def test_frontend_distinguishes_local_pending_and_server_confirmed_work():
    home = read("app/src/main/java/za/org/rtc/community/feature/home/HomeComponents.kt")
    composer = read(
        "app/src/main/java/za/org/rtc/community/feature/publicreports/presentation/PublicReportComposerViewModel.kt"
    )
    screen = read(
        "app/src/main/java/za/org/rtc/community/feature/publicreports/presentation/PublicReportComposerScreen.kt"
    )
    assert "This has not been submitted or confirmed by the server." in home
    assert "serverConfirmedReportId" in composer
    assert "hasPartialSubmission" in composer
    assert "current.serverConfirmedReportId ?: repository.create(draft)" in composer
    assert "Retry evidence upload" in screen
    assert "The report itself is confirmed on RTC. Evidence is not fully attached yet." in screen
