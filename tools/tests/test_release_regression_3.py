from release_contract_context import *
from release_contract_context import _function_body

def test_rtc_ai_has_no_local_success_adapter():
    corpus = MAIN + ADMIN_AI + VM + REPO + PROD
    assert 'import za.org.rtc.community.feature.administration.AiAssistantScreen' in MAIN
    assert 'AiAssistantScreen(viewModel' in MAIN
    assert 'confirmAiProposalForDevelopment' not in corpus
    assert 'Development adapter recorded confirmation' not in corpus
    assert '"rtc-admin-ai"' in PROD
    assert 'rtc_admin_ai_command' in PROD
    assert 'rtc_admin_ai_confirm' in PROD
    assert 'Recaptcha.fetchClient' in PROD
    assert 'Confirm audited draft' in ADMIN_AI


def test_operational_controls_use_picker_not_raw_iso_entry():
    screen = re.search(r'(?:private|internal) fun OperationalControlsScreen\(.*?\n\}', ADMIN_OPERATIONAL, re.S).group(0)
    assert 'import za.org.rtc.community.feature.administration.OperationalControlsScreen' in MAIN
    assert 'OperationalControlsScreen(viewModel' in MAIN
    assert 'DatePickerDialog' in screen
    assert 'TimePickerDialog' in screen
    assert 'Choose expiry date and time' in screen
    assert 'Impact preview' in screen
    assert 'CONFIRM OPERATIONAL CONTROL' in screen
    assert 'label = { Text("Expiry ISO") }' not in screen


def test_debug_build_requires_ignored_nonproduction_supabase_configuration():
    assert 'runtime.local.properties' in GRADLE
    assert 'Debug builds require the non-production runtime property' in GRADLE
    assert 'buildConfigField("String", "SUPABASE_URL", "\\"\\"")' in GRADLE
    assert 'buildConfigField("String", "SUPABASE_PUBLISHABLE_KEY", "\\"\\"")' in GRADLE
    assert 'debugRuntimeValue("supabase.nonproduction.url")' in GRADLE
    assert 'debugRuntimeValue("supabase.nonproduction.publishableKey")' in GRADLE
    assert 'pbzzfzfgwzwdstvnwzqu' not in GRADLE


def test_hilt_workmanager_uses_custom_initializer_only():
    manifest = (ROOT / 'app/src/main/AndroidManifest.xml').read_text()
    app = (ROOT / 'app/src/main/java/za/org/rtc/community/RtcCommunityApplication.kt').read_text()
    assert 'Configuration.Provider' in app
    assert 'HiltWorkerFactory' in app
    assert 'androidx.startup.InitializationProvider' in manifest
    assert 'androidx.work.WorkManagerInitializer' in manifest
    assert 'tools:node="remove"' in manifest


def test_source_baseline_reconciles_obsolete_alert_retry_and_dispatch_path():
    migration = ROOT / 'supabase/migrations/20260826033632_reconcile_obsolete_alert_retry_and_dispatch_baseline.sql'
    text = migration.read_text()
    assert 'drop function if exists public.ops_retry_failed_community_alert(uuid, text, text, uuid);' in text
    assert 'drop function if exists public.assert_rtc_alert_dispatch_secret(text);' in text
    assert "jobname = 'rtc-community-alert-schedule'" in text
    assert "extname = 'pg_cron'" in text
    assert 'create extension' not in text
    assert 'drop extension' not in text
    assert 'vault.decrypted_secrets' not in text
    assert 'net.http_post' not in text


def test_community_sharing_uses_only_custom_post_uri_and_protected_load_path():
    manifest = (ROOT / 'app/src/main/AndroidManifest.xml').read_text()
    app = (ROOT / 'app/src/main/java/za/org/rtc/community/ui/navigation/RtcCommunityApp.kt').read_text()
    scoped_vm = (ROOT / 'app/src/main/java/za/org/rtc/community/feature/community/CommunityViewModel.kt').read_text()
    scoped_repo = (ROOT / 'app/src/main/java/za/org/rtc/community/feature/community/SupabaseCommunityRepository.kt').read_text()
    share = _function_body(NAV_METADATA, 'shareCommunityPost', ['directoryItemRoute'])
    assert 'android:scheme="rtc"' in manifest and 'android:host="community"' in manifest
    assert 'internal fun shareCommunityPost(' in NAV_METADATA
    assert 'shareCommunityPost(context, sharedPost.id)' in MAIN
    assert 'rtc://community/post/$safePostId' in share
    assert 'Intent.ACTION_SEND' in share
    assert 'signedUrl' not in share and 'author' not in share and 'content' not in share
    assert 'handleCommunityPostIntent(intent)' in MAIN
    assert 'openCommunityPostFromDeepLink' in VM
    assert 'pendingCommunityPostId?.takeIf { session.authority == SessionAuthority.SUPABASE_AUTH }' in app
    assert 'navController.navigateOverlay(communityPostRoute(postId))' in app
    assert 'communityViewModel.loadPostDetail(postId)' in COMMUNITY_DETAIL
    assert 'repository.loadPost(postId)' in scoped_vm
    assert 'override suspend fun loadPost(postId: String)' in scoped_repo
    assert 'supabase.from("community_post_feed").select' in scoped_repo


def test_profile_updates_rehydrate_community_identity_projections():
    profile = _function_body(REPO, 'updateProfile', ['setNotificationPreference'])
    assert 'productionUxRepository.saveOwnProfile(cleanName, cleanBio, cleanInterests).getOrThrow()' in profile
    assert 'hydrateSupabaseSession()' in profile
    assert 'refreshLiveContent()' in profile
    assert '_communityPostDetail.value?.id?.let { postId -> loadCommunityPostDetail(postId) }' in profile


def test_home_snapshot_uses_dense_geometry_and_independent_verified_report_routes():
    home = _function_body(HOME_SCREEN, 'HomeScreen', [])
    resident_cards = (
        ROOT / 'app/src/main/java/za/org/rtc/community/feature/home/HomeResidentModernisationCards.kt'
    ).read_text()
    snapshot = _function_body(resident_cards, 'HomeCommunityStatusCard', ['HomePostComposerCard'])
    tokens = (ROOT / 'app/src/main/java/za/org/rtc/community/ui/theme/RtcDesignTokens.kt').read_text()
    assert 'val pagePadding = 16.dp' in tokens
    assert 'val cardPadding = 16.dp' in tokens
    assert 'import za.org.rtc.community.feature.home.HomeScreen' in MAIN
    assert 'HomeScreen(' in MAIN
    assert 'contentPadding = PaddingValues(RtcHomeDashboard.pagePadding)' in home
    assert 'RtcCard {' in snapshot
    assert 'modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)' in snapshot
    for label, field, scope in (
        ('Verified', 'verifiedReports', 'VERIFIED'),
        ('Active', 'activeReports', 'ACTIVE'),
        ('Resolved', 'resolvedReports', 'RESOLVED'),
        ('Unresolved', 'unresolvedReports', 'UNRESOLVED'),
    ):
        assert f'HomeCommunityMetric("{label}", dashboard.{field}, PublicReportScope.{scope})' in snapshot
    assert 'onOpenScope(metric.scope)' in snapshot


def test_notification_permission_launch_is_guarded_at_the_api_33_action_boundary():
    notification_dialog = re.search(
        r'if \(notificationPermissionRationaleOpen\) \{.*?\n    \}',
        MAIN,
        re.S,
    ).group(0)
    assert 'if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)' in notification_dialog
    assert 'notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)' in notification_dialog


def test_home_quick_access_uses_truthful_directory_deep_links():
    home = _function_body(HOME_SCREEN, 'HomeScreen', ['LiveDashboardMetrics', 'QuickAccessSection'])
    quick_access = _function_body(HOME_COMPONENTS, 'QuickAccessSection', ['ContinueDraftCard'])
    assert 'QuickAccessSection(' in HOME_SCREEN
    assert 'onOpenDirectory = onOpenDirectory' in home
    assert 'onOpenDirectory("projects")' in quick_access
    assert 'onOpenDirectory("centres")' in quick_access
    assert 'Text("Help")' in quick_access
    assert 'Text("Customise")' not in quick_access


def test_home_renderer_uses_only_compiled_validated_sections_and_default_fallback():
    configuration = (ROOT / 'app/src/main/java/za/org/rtc/community/core/UiConfiguration.kt').read_text()
    home = _function_body(HOME_SCREEN, 'HomeScreen', ['LiveDashboardMetrics', 'QuickAccessSection'])
    rendering = (ROOT / 'app/src/main/java/za/org/rtc/community/ui/config/HomeRendering.kt').read_text()
    assert 'enum class HomeSection' in configuration
    for section in ['WELCOME', 'COMMUNITY_SNAPSHOT', 'QUICK_ACCESS', 'CONTINUE_DRAFT', 'PENDING_SYNC', 'NEXT_STEPS', 'LATEST_UPDATES', 'HELP']:
        assert section in configuration
    assert 'val mandatorySections = setOf(HomeSection.WELCOME, HomeSection.HELP)' in configuration
    assert 'fun validatedOrDefault(candidate: HomeLayout?): HomeLayout' in configuration
    assert 'fun decodeOrNull(rawConfiguration: String?): GlobalUiConfiguration?' in configuration
    assert 'ignoreUnknownKeys = false' in configuration
    assert 'layout = HomeLayout.default()' in MAIN
    assert 'HomeLayout.validatedOrDefault(layout)' in home
    assert 'resolvedLayout.renderItems()' in home
    assert 'renderItems.forEach { renderItem ->' in home
    assert 'sealed interface HomeRenderItem' in rendering
    assert 'data class Section(val section: HomeSection) : HomeRenderItem' in rendering
    assert 'data class Image(val widget: HomeImageWidget) : HomeRenderItem' in rendering
    assert 'val resolved = HomeLayout.validatedOrDefault(this)' in rendering
    assert 'resolved.sections.forEach { section ->' in rendering
    assert 'add(HomeRenderItem.Section(section))' in rendering
    assert 'widget?.anchorSection == section' in rendering
    assert 'HomeImagePlacement.BEFORE' in rendering
    assert 'HomeImagePlacement.AFTER' in rendering
    assert 'is HomeRenderItem.Section -> when (val section = renderItem.section)' in home
    assert 'is HomeRenderItem.Image -> item(key = "home_image_${renderItem.widget.assetId}")' in home
    assert 'HomeSection.HELP -> item(key = "home_help")' in home
    non_executable_renderer = (home + rendering).lower()
    for forbidden in ['external_url', 'javascript:', 'android.content.intent', 'android.net.uri']:
        assert forbidden not in non_executable_renderer
