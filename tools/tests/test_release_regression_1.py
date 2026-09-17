from release_contract_context import *
from release_contract_context import _function_body

def test_resident_navigation_uses_policy_driven_five_destination_chrome():
    assert 'ResidentNavigationPolicy.primaryDestinations(navigationV2Enabled)' in NAV_METADATA
    assert 'MainDestination.ACCOUNT' in NAV_METADATA
    assert 'RtcRoute.SERVICES' in NAV_METADATA
    assert 'activeNavItems = residentNavItems(navigationV2Enabled)' in MAIN
    assert 'items = activeNavItems.map' in MAIN
    assert 'RtcResidentBottomNavigation' in MAIN


def test_media_upload_path_is_bounded_and_durable():
    media = (ROOT / 'app/src/main/java/za/org/rtc/community/data/local/MediaPreparation.kt').read_text()
    worker = (ROOT / 'app/src/main/java/za/org/rtc/community/data/local/CommunityUploadWorker.kt').read_text()
    entity = (ROOT / 'app/src/main/java/za/org/rtc/community/data/local/RtcDatabase.kt').read_text()
    assert 'readBytes()' not in media
    assert 'WorkManager' in VM or 'WorkManager' in REPO or 'WorkManager' in worker
    assert 'UploadOutboxEntity' in entity
    assert 'finalize_community_post' in PROD
    assert 'PickMultipleVisualMedia' in COMMUNITY_COMPOSER


def test_no_privileged_server_credentials_are_embedded_in_android_source():
    manifest = (ROOT / 'app/src/main/AndroidManifest.xml').read_text()
    assert 'android:allowBackup="false"' in manifest
    assert 'android:usesCleartextTraffic="false"' in manifest
    source = '\n'.join(p.read_text(errors='ignore') for p in (ROOT / 'app/src').rglob('*') if p.is_file())
    forbidden = ['SUPABASE_SERVICE_ROLE_KEY', 'SUPABASE_SECRET_KEY', 'GOOGLE_SERVICE_ACCOUNT_JSON', 'private_key_id', 'BEGIN PRIVATE KEY']
    for token in forbidden:
        assert token not in source, token


def test_appetize_resident_navigation_runner_is_autonomous_and_nonproduction_only():
    config = (ROOT / 'qa/appetize/playwright.config.cjs').read_text()
    suite = (ROOT / 'qa/appetize/tests/resident-menu.spec.cjs').read_text()
    assert "RTC_APPETIZE_BUILD_ID is required" in config
    assert "device: 'pixel7'" in config
    assert 'grantPermissions: false' in config
    assert 'userInteractionDisabled: true' in config
    assert 'adbShellCommand' in suite
    assert "const ANDROID_PACKAGE = 'za.org.rtc.community'" in suite
    assert 'am force-stop ${ANDROID_PACKAGE}' in suite
    assert 'za.org.rtc.community.DEBUG_SESSION_ROLE' in suite
    assert 'RESIDENT_A' in suite
    assert 'Open account or work queue' in suite
    for forbidden in ['password', 'signIn', 'signUp', 'grantPermissions: true', 'SUPABASE_SERVICE_ROLE']:
        assert forbidden not in suite, forbidden


def test_github_android_build_pipeline_is_source_controlled_and_truthful():
    workflow = ROOT / ".github/workflows/android-ci.yml"
    assert workflow.exists()
    text = workflow.read_text()
    assert "android-actions/setup-android" in text
    assert "gradle/actions/setup-gradle" in text
    assert "gradle-version: '8.13'" in text or 'gradle-version: "8.13"' in text
    assert "platforms;android-36" in text
    assert "build-tools;35.0.0" in text
    assert "testDebugUnitTest" in text
    assert "lintDebug" in text
    assert "assembleDebug" in text
    assert "GOOGLE_SERVICES_JSON_BASE64" in text
    assert "assembleRelease" in text or "bundleRelease" in text

    app_gradle = (ROOT / "app/build.gradle.kts").read_text()
    assert "google-services.json" in app_gradle
    assert 'apply(plugin = "com.google.gms.google-services")' in app_gradle

    gitignore = (ROOT / ".gitignore").read_text()
    for secretish in ["local.properties", "signing.properties", "*.jks", "*.keystore", "app/google-services.json"]:
        assert secretish in gitignore


def test_community_avatar_sync_and_like_contract_are_source_controlled():
    migration = ROOT / 'supabase/migrations/20260826070000_reconcile_community_avatar_sync_and_post_likes.sql'
    text = migration.read_text()
    assert 'after insert or update of display_name, avatar_url on public.profiles' in text
    assert 'private.ensure_community_profile_for_account(new.id)' in text
    assert 'revoke all on function private.ensure_community_profile_after_profile_change() from public, anon, authenticated;' in text
    assert 'create or replace function public.toggle_community_post_like(p_post_id uuid)' in text
    assert 'if v_actor_id is null then' in text
    assert "raise exception 'AUTH_REQUIRED'" in text
    assert "raise exception 'GUIDELINES_NOT_ACCEPTED'" in text
    assert 'pg_advisory_xact_lock' in text
    assert 'revoke all on function public.toggle_community_post_like(uuid) from public, anon;' in text
    assert 'grant execute on function public.toggle_community_post_like(uuid) to authenticated;' in text
    assert 'viewer_has_liked' in text
    assert 'create or replace view public.community_comment_feed' in text
    assert 'cp.updated_at as avatar_updated_at' in text
    assert 'grant insert on public.community_reactions to authenticated;' not in text
    assert 'grant update on public.community_reactions to authenticated;' not in text
    assert 'grant delete on public.community_reactions to authenticated;' not in text


def test_inert_resident_activity_and_following_controls_are_not_presented_as_live():
    community = _function_body(COMMUNITY_FEED, 'CommunityScreen', ['ComposerCard', 'CommunityActionFeedback'])
    # Following-only filter remains unavailable; Saved bookmarks are now a live server-backed tab.
    assert 'followingOnly' not in community
    assert 'Follow a Community topic to see it here.' not in (COMMUNITY_FEED + ACCOUNT_SCREEN + ACCOUNT_NOTIFICATIONS)
    assert 'Topic and people following is not available yet.' not in (COMMUNITY_FEED + ACCOUNT_SCREEN + ACCOUNT_NOTIFICATIONS)
    assert 'tab == "Saved"' in COMMUNITY_FEED or 'label = { Text("Saved") }' in COMMUNITY_FEED
    assert 'AccountAction(title: String, description: String, icon: ImageVector, onClick: (() -> Unit)? = null)' in ACCOUNT_DIALOGS
    assert 'Open the Moderation workspace to act on assigned queue items.' in ADMIN_MODERATION


def test_reference_community_feed_actions_and_one_time_guideline_gate_are_real():
    community = _function_body(COMMUNITY_FEED, 'CommunityScreen', ['ComposerCard', 'CommunityActionFeedback'])
    feed_card = _function_body(COMMUNITY_CARD, 'CommunityPostCard', [])
    assert 'resumeComposerAfterGuidelines' in community
    assert 'composerOpen = true' in community
    assert 'guidelinesOpen = true' in community
    assert 'onToggleLike = communityViewModel::toggleLike' in community
    assert 'onSharePost = onSharePost' in community
    assert 'onToggleLike(post.id)' in feed_card
    assert 'onOpenPost(post)' in feed_card
    assert 'onSharePost(post)' in feed_card
    assert 'Photo' in COMMUNITY_FEED and 'Video' in COMMUNITY_FEED
    assert 'timestampLabel = relativeTimeLabel(post.createdAt)' in feed_card
    assert 'postCommentAfterGuidelines' in COMMUNITY_DETAIL
    assert 'CommunityPostCard(' in COMMUNITY_FEED
    assert 'Text("${post.reactions} reactions  •  ${post.comments} comments"' not in COMPONENTS
    assert 'timestampLabel: String = post.createdAt' in COMPONENTS
    resident = (ROOT / 'app/src/main/java/za/org/rtc/community/app/RtcResidentCoordinator.kt').read_text()
    safe_error = (ROOT / 'app/src/main/java/za/org/rtc/community/app/SafeUiError.kt').read_text()
    assert 'SafeUiError.community' in resident
    assert 'fun community(error: Throwable, fallback: String)' in safe_error


def test_administrator_reference_tiles_are_accessible_real_actions_not_fake_analytics():
    tile = _function_body(ADMIN_COMPONENTS, 'AdminReferenceToolTile', ['AdminReferenceListRow'])
    row = _function_body(ADMIN_COMPONENTS, 'AdminReferenceListRow', ['AdminWorkspaceNavigation'])
    analytics = _function_body(ADMIN_PRIVACY, 'AdministratorPrivacyAnalyticsScreen', ['AdminAnalyticsMetricCard'])
    metric = _function_body(ADMIN_COMPONENTS, 'AdminWorkspaceMetricTile', ['AdminReferenceToolTile'])
    assert 'clickable(role = Role.Button, onClick = onClick)' in metric
    assert 'clickable(role = Role.Button, onClick = onClick)' in tile
    assert 'heightIn(min = RtcSize.minimumTouchTarget * 2)' in tile
    assert 'clickable(role = Role.Button, onClick = onClick)' in row
    assert 'dashboard.value("VERIFIED_SIGNUPS")' in analytics
    assert 'dashboard.value("NOTIFICATIONS_DELIVERED")' in analytics
    assert 'dashboard.value("DIRECTORY_SEARCHES")' in analytics
    assert 'does not process live device location' in analytics
    assert 'distinct from any future Nearby search area or live device-location feature.' in analytics


def test_profile_photo_uses_visual_picker_and_has_a_terminal_safe_result():
    auth = (ROOT / 'app/src/main/java/za/org/rtc/community/app/RtcAuthenticationCoordinator.kt').read_text()
    safe_error = (ROOT / 'app/src/main/java/za/org/rtc/community/app/SafeUiError.kt').read_text()
    account_surface = ACCOUNT_SCREEN + ACCOUNT_DIALOGS + ACCOUNT_NOTIFICATIONS
    # Gallery/camera entry remains present on the account profile surface.
    assert 'Choose gallery' in account_surface or 'PickVisualMedia' in account_surface
    assert 'import za.org.rtc.community.feature.account.AccountScreen' in MAIN
    assert 'PROFILE_PHOTO_OPERATION_TIMEOUT_MS = 45_000L' in auth
    assert 'withTimeout(PROFILE_PHOTO_OPERATION_TIMEOUT_MS)' in auth
    assert 'SafeUiError.profilePhoto(error)' in auth
    assert 'fun profilePhoto(error: Throwable)' in safe_error
    assert 'Profile photo saved and refreshed across Community.' in auth


def test_support_find_centre_uses_real_centres_directory_route():
    support = _function_body(SUPPORT_SCREENS, 'SupportScreen', ['SupportRequestSheet'])
    assert 'onOpenCentres: () -> Unit' in support
    assert 'SupportChoice("Find a centre", "Open published support locations and service information.", Icons.Filled.LocationOn, onOpenCentres)' in support
    assert 'view = "Find a centre"' not in support
    assert 'onOpenCentres = { navController.navigateOverlay(exploreDirectoryRoute("centres")) }' in MAIN
    assert 'import za.org.rtc.community.feature.support.SupportScreen' in MAIN
