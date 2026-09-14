from release_contract_context import *
from release_contract_context import _function_body

def test_protected_routes_fail_closed_at_render_boundary():
    protected = [
        'WORK_QUEUE', 'MY_WORK', 'STAFF_ALERTS', 'CONTENT', 'MODERATION', 'AI',
        'ACCESS_MANAGEMENT', 'OPERATIONAL_CONTROLS', 'ANALYTICS_DASHBOARD',
        'SYSTEM_HEALTH', 'ADMIN_ACTIVITY'
    ]
    for route in protected:
        assert f'ProtectedRoute(RtcRoute.{route}' in MAIN, route
    assert 'RouteAccessPolicy.evaluate' in MAIN
    assert 'returnToSafeWorkspace(session.role.isStaff)' in MAIN
    assert 'else -> RtcRoute.OPERATIONAL_CONTROLS' not in MAIN


def test_mfa_secret_is_one_time_qr_only():
    dialog = re.search(r'internal fun AdministratorMfaDialog\(.*?\n\}', ADMIN_MFA, re.S).group(0)
    auth = (ROOT / 'app/src/main/java/za/org/rtc/community/app/RtcAuthenticationCoordinator.kt').read_text()
    assert 'TotpQrCode' in dialog
    assert 'it.secret' not in dialog
    assert 'QRCodeWriter' in ADMIN_COMPONENTS
    assert 'internal fun AdministratorMfaDialog(' in ADMIN_MFA
    assert 'factor.data.secret' in REPO
    assert 'factor.data.uri' in REPO
    assert 'AdministratorMfaUiState(' in auth
    assert 'message = "Authenticator verification complete."' in auth
    assert 'fun dismissAdministratorMfaUi()' in auth
    assert '_administratorMfaUi.value = AdministratorMfaUiState()' in auth


def test_release_build_configuration_keeps_minification_and_resource_shrinking():
    assert 'isMinifyEnabled = true' in GRADLE
    assert 'isShrinkResources = true' in GRADLE
    assert 'androidx-compose-foundation' in VERSIONS
    assert 'coil-video' in VERSIONS
    assert 'zxing-core' in VERSIONS


def test_source_contains_forward_backend_contract_migration():
    migration = ROOT / 'supabase/migrations/20260825222000_complete_community_feed_and_idempotent_finalize.sql'
    text = migration.read_text()
    assert 'community_post_feed' in text
    assert 'trending_score' in text
    assert 'is_followed_topic' in text
    assert 'finalize_community_post' in text
    assert re.search(r'jsonb_array_length\(p_media\)\s*>\s*10', text)
    perf = (ROOT / 'supabase/migrations/20260825230000_release_candidate_performance_cleanup.sql').read_text()
    assert 'drop index if exists public.community_post_media_post_position_unique' in perf
    assert 'user_id = (select auth.uid())' in perf


def test_unsupported_alert_retry_workflow_is_absent():
    client = "\n".join([MAIN, VM, REPO, PROD])
    assert "retryFailedAlertDelivery" not in client
    assert "ops_retry_failed_community_alert" not in client
    assert "Retry failed delivery" not in client


def test_android_community_like_path_is_server_confirmed():
    models = (ROOT / 'app/src/main/java/za/org/rtc/community/core/Models.kt').read_text()
    scoped_repo = (ROOT / 'app/src/main/java/za/org/rtc/community/feature/community/SupabaseCommunityRepository.kt').read_text()
    scoped_vm = (ROOT / 'app/src/main/java/za/org/rtc/community/feature/community/CommunityViewModel.kt').read_text()
    assert 'val viewerHasLiked: Boolean = false' in models
    assert 'toggle_community_post_like' in scoped_repo
    assert 'CommunityPostLikeOutcomeRow' in scoped_repo
    assert 'fun toggleLike(postId: String)' in scoped_vm
    assert 'optimisticLikeToggle()' in scoped_vm
    assert 'withLikeOutcome(outcome)' in scoped_vm
    assert 'refreshLiveContent()' not in scoped_vm
    assert 'Unlike post' in COMMUNITY_DETAIL and 'Like post' in COMMUNITY_DETAIL
    assert 'Icons.Outlined.FavoriteBorder' in COMMUNITY_DETAIL
    assert 'import za.org.rtc.community.feature.community.CommunityPostDetailScreen' in MAIN


def test_community_feed_renders_server_projected_avatar_with_initials_fallback():
    assert 'private fun CommunityFeedAvatar(url: String?, name: String, syntheticAvatarRes: Int? = null)' in COMPONENTS
    assert 'model = url ?: syntheticAvatarRes' in COMPONENTS
    assert 'CommunityFeedAvatar(post.authorAvatarUrl, post.author, syntheticAvatarRes)' in COMPONENTS
    assert 'contentDescription = "$name\'s profile photo"' in COMPONENTS
    assert 'Text(name.take(1).uppercase(), fontWeight = FontWeight.Bold)' in COMPONENTS


def test_reference_home_and_explore_cards_use_approved_server_values():
    home_cards = (
        ROOT / 'app/src/main/java/za/org/rtc/community/feature/home/HomeResidentModernisationCards.kt'
    ).read_text()
    explore = _function_body(EXPLORE_SCREEN, 'ExploreScreen', ['ExploreActionRow'])
    for field in ('verifiedReports', 'activeReports', 'resolvedReports', 'unresolvedReports'):
        assert f'dashboard.{field}' in home_cards
    assert 'notices.count { it.status == NoticeStatus.PUBLISHED }' in explore
    assert 'count = "${projects.size} available"' in explore
    assert 'count = "${opportunities.size} open"' in explore
    assert 'LinearProgressIndicator' not in explore


def test_reference_onboarding_uses_supplied_splash_logo_and_real_full_page_auth_paths():
    welcome = _function_body(ACCOUNT_WELCOME, 'PublicWelcomeScreen', [])
    brand = _function_body(BRAND_LOCKUP, 'RtcBrandLockup', [])
    configuration = (ROOT / 'app/src/main/java/za/org/rtc/community/core/UiConfiguration.kt').read_text()
    welcome_configuration = re.search(
        r'data class WelcomeConfiguration\((.*?)\n\)',
        configuration,
        re.S,
    ).group(1)
    assert '@drawable/rtc_community_logo_transparent' in (ROOT / 'app/src/main/res/values/themes.xml').read_text()
    assert '@drawable/rtc_community_logo_transparent' in (ROOT / 'app/src/main/res/drawable/rtc_splash_background.xml').read_text()
    assert 'R.drawable.rtc_community_logo_transparent' not in brand
    assert 'R.drawable.rtc_logo_mark_transparent' in brand
    assert 'import za.org.rtc.community.feature.account.PublicWelcomeScreen' in MAIN
    assert 'PublicWelcomeScreen(' in MAIN
    assert 'val welcome = LocalRtcUiConfiguration.current.welcome' in welcome
    for configured_field in [
        'welcome.headline',
        'welcome.supportingText',
        'welcome.primaryActionLabel',
        'welcome.secondaryActionLabel',
        'welcome.launchTreatment',
    ]:
        assert configured_field in welcome
    assert re.findall(r'val (\w+):', welcome_configuration) == [
        'headline',
        'supportingText',
        'primaryActionLabel',
        'secondaryActionLabel',
        'launchTreatment',
    ]
    assert 'mode = "CREATE"' in welcome
    assert 'mode = "SIGN_IN"' in welcome
    assert 'Create your RTC account' in welcome
    assert 'Sign in to RTC Community' in welcome
    assert 'label = { Text("Your name") }' in welcome
    assert 'label = { Text("Confirm password") }' in welcome
    assert 'passwordConfirmation != password' in welcome
    assert 'onRequestPasswordRecovery(email)' in welcome
    assert 'onSignUp(email, password, displayName)' in welcome
    assert 'onSignIn(email, password)' in welcome
    assert 'RtcGoogleSignInButton(' in welcome
    assert 'onCredential = onGoogleCredential' in welcome
    assert 'onFailure = onGoogleSignInError' in welcome
    assert 'Text("Back")' in welcome


def test_reference_graphite_theme_is_the_default_for_system_preference_sessions():
    theme = (ROOT / 'app/src/main/java/za/org/rtc/community/ui/theme/Theme.kt').read_text()
    resolver = (ROOT / 'app/src/main/java/za/org/rtc/community/ui/theme/ThemeModeResolver.kt').read_text()
    assert 'resolveRtcUseDarkTheme(preference, isSystemInDarkTheme())' in theme
    assert 'ThemePreference.SYSTEM -> systemInDarkTheme' in resolver
    assert 'ThemePreference.LIGHT -> false' in resolver


def test_phase1_ui_configuration_is_global_bounded_and_server_authorized():
    migration = ROOT / 'supabase/migrations/20260827190000_add_nonproduction_ui_configuration_catalogue.sql'
    text = migration.read_text()
    assert 'create table if not exists public.ui_configuration_versions' in text
    assert 'create table if not exists public.ui_configuration_events' in text
    assert "check (audience_key = 'RESIDENT_GLOBAL')" in text
    assert "state in ('DRAFT', 'PUBLISHED', 'SUPERSEDED', 'REJECTED')" in text
    assert 'revoke all on table public.ui_configuration_versions from anon, authenticated;' in text
    assert 'revoke all on table public.ui_configuration_events from anon, authenticated;' in text
    assert 'private.access_assert_system_admin()' in text
    assert "'PUBLISH UI CONFIGURATION'" in text
    assert "'REVERT UI CONFIGURATION'" in text
    assert 'public.ui_configuration_effective_global_home()' in text
    assert "'external_url'" in text and "'script'" in text and "'asset_url'" in text
    assert 'grant execute on function public.ui_configuration_effective_global_home() to authenticated;' in text
