from release_contract_context import *
from release_contract_context import _function_body

def test_client_media_has_pager_retry_and_signed_url_refresh():
    scoped_vm = (ROOT / 'app/src/main/java/za/org/rtc/community/feature/community/CommunityViewModel.kt').read_text()
    scoped_repo = (ROOT / 'app/src/main/java/za/org/rtc/community/feature/community/SupabaseCommunityRepository.kt').read_text()
    media_runtime = COMMUNITY_MEDIA + (ROOT / 'app/src/main/java/za/org/rtc/community/ui/media/RtcMedia3VideoPlayer.kt').read_text()
    assert 'HorizontalPager' in COMMUNITY_MEDIA
    assert 'rememberPagerState' in COMMUNITY_MEDIA
    assert 'Player.Listener' in media_runtime
    assert 'STATE_BUFFERING' in media_runtime
    assert 'onPlayerError' in media_runtime
    assert 'repository.refreshMediaUrl(mediaId)' in scoped_vm
    assert 'override suspend fun refreshMediaUrl(mediaId: String)' in scoped_repo
    assert 'signedUrlCache.invalidate(mediaCacheKey(mediaId))' in scoped_repo
    assert 'createSignedUrl(storagePath, SIGNED_URL_TTL)' in scoped_repo
    assert 'onRefreshMediaUrl = communityViewModel::refreshMediaUrl' in COMMUNITY_FEED
    assert 'onRefreshMediaUrl = communityViewModel::refreshMediaUrl' in COMMUNITY_DETAIL
    assert 'VideoFrameDecoder.Factory()' in COMMUNITY_MEDIA
    assert 'videoFrameMillis' in COMMUNITY_MEDIA
    assert 'Previous' not in re.search(r'internal fun FullScreenMediaGallery\(.*?\n\}', COMMUNITY_MEDIA, re.S).group(0)


def test_concept6_shared_system_is_used_by_major_surfaces():
    assert 'RtcScreenScaffold' in COMPONENTS
    assert 'RtcProtectedAreaBanner' in COMPONENTS
    assert 'RtcCommunityFeedCard' in COMPONENTS
    assert 'RtcEmergencyBanner' in COMPONENTS
    assert 'RtcCaseProgress' in COMPONENTS
    for name in ['CommunityScreen', 'CommunityUpdatesScreen', 'SupportScreen', 'AccountScreen', 'SearchScreen', 'NotificationsScreen', 'AiAssistantScreen']:
        source = COMMUNITY_FEED if name == 'CommunityScreen' else EXPLORE_SCREEN if name == 'CommunityUpdatesScreen' else SUPPORT_SCREENS if name == 'SupportScreen' else ACCOUNT_SCREEN if name == 'AccountScreen' else ACCOUNT_NOTIFICATIONS if name == 'NotificationsScreen' else PUBLIC_SEARCH if name == 'SearchScreen' else ADMIN_AI
        block = re.search(rf'(?:private|internal) fun {name}\(.*?(?=\n@Composable|\Z)', source, re.S)
        assert block and 'RtcScreenScaffold' in block.group(0), name
    assert 'import za.org.rtc.community.feature.community.CommunityScreen' in MAIN
    assert 'import za.org.rtc.community.feature.explore.ExploreScreen' in MAIN
    assert 'import za.org.rtc.community.feature.support.SupportScreen' in MAIN
    assert 'import za.org.rtc.community.feature.account.AccountScreen' in MAIN
    assert 'import za.org.rtc.community.feature.account.NotificationsScreen' in MAIN
    assert 'import za.org.rtc.community.feature.explore.SearchScreen' in MAIN
    assert 'import za.org.rtc.community.feature.administration.AiAssistantScreen' in MAIN
    assert 'AiAssistantScreen(viewModel' in MAIN


def test_debug_resident_automation_entry_is_release_gated_and_non_privileged():
    assert 'private fun applyDebugSessionIntent(intent: Intent?)' in MAIN
    assert 'if (!BuildConfig.DEBUG) return' in MAIN
    assert 'EXTRA_DEBUG_SESSION_ROLE' in MAIN
    assert 'private const val DEBUG_RESIDENT_A = "RESIDENT_A"' in MAIN
    assert 'rtcViewModel.beginDevelopmentResidentSession()' in MAIN
    instrumentation = (ROOT / 'app/src/androidTest/java/za/org/rtc/community/DebugResidentMenuAutomationTest.kt').read_text()
    assert 'putExtra(MainActivity.EXTRA_DEBUG_SESSION_ROLE, "RESIDENT_A")' in instrumentation
    assert 'Open account or work queue' in instrumentation
    assert 'Text("Sign out") {' not in instrumentation
    assert 'onNodeWithText("Sign out")' in instrumentation


def test_remaining_protected_workspace_surfaces_use_concept6_scaffolds():
    source = (ROOT / "app/src/main/java/za/org/rtc/community/MainActivity.kt").read_text()
    sources = {
        "AccessManagementScreen": ADMIN_ACCESS,
        "AdministratorPrivacyAnalyticsScreen": ADMIN_PRIVACY,
        "ContentManagementScreen": ADMIN_CONTENT,
        "ModerationDashboard": ADMIN_MODERATION,
        "OperationalControlsScreen": ADMIN_OPERATIONAL,
        "MyWorkProfileScreen": ADMIN_MY_WORK,
        "StaffCommunityAlertsScreen": STAFF_ALERTS,
    }
    ordered = [
        "AccessManagementScreen",
        "AdministratorPrivacyAnalyticsScreen",
        "ContentManagementScreen",
        "ModerationDashboard",
        "OperationalControlsScreen",
        "MyWorkProfileScreen",
        "StaffCommunityAlertsScreen",
    ]
    for index, name in enumerate(ordered):
        body = _function_body(sources.get(name, source), name, ordered[index + 1 :])
        assert "RtcScreenScaffold" in body, f"{name} must use the shared Concept 6 scaffold"

    for name in ["AccessManagementScreen", "AdministratorPrivacyAnalyticsScreen", "OperationalControlsScreen"]:
        body = _function_body(sources.get(name, source), name, ordered)
        assert "RtcProtectedAreaBanner" in body, f"{name} must visibly communicate the protected administrator boundary"
    assert 'import za.org.rtc.community.feature.administration.OperationalControlsScreen' in MAIN
    assert 'OperationalControlsScreen(viewModel' in MAIN
    assert 'import za.org.rtc.community.feature.alerts.StaffCommunityAlertsScreen' in MAIN
    assert 'StaffCommunityAlertsScreen(viewModel' in MAIN


def test_synthetic_device_validation_protocol_is_source_controlled_and_fail_closed():
    protocol = ROOT / 'PHASE30_SYNTHETIC_DEVICE_MEDIA_VALIDATION_PROTOCOL_2026-08-26.md'
    text = protocol.read_text()
    assert 'eqwstpdjoineycrkhpht' in text
    assert 'pbzzfzfgwzwdstvnwzqu' in text
    assert 'synthetic-only' in text.lower()
    assert 'No device/emulator test has been run' in text
    assert 'NO-GO' in text
    assert 'Cross-Account Upload and Cross-Account Switch' not in text
    assert 'Interrupted Author Upload and Cross-Account Switch' in text


def test_video_has_explicit_accessible_muting_and_releases_player():
    video = _function_body(COMMUNITY_MEDIA, 'SignedVideoPlayer', ['CommunityAvatar'])
    assert 'var isMuted by rememberSaveable(mediaId)' in video
    assert 'player.volume = if (isMuted) 0f else 1f' in video
    assert 'Unmute video' in video and 'Mute video' in video
    assert 'player.release()' in video


def test_reference_driven_makeover_uses_transparent_rtc_brand_and_shared_palette():
    theme = (ROOT / 'app/src/main/java/za/org/rtc/community/ui/theme/Theme.kt').read_text()
    splash = (ROOT / 'app/src/main/res/values/themes.xml').read_text()
    splash_background = (ROOT / 'app/src/main/res/drawable/rtc_splash_background.xml').read_text()
    logo = ROOT / 'app/src/main/res/drawable-nodpi/rtc_logo_mark_transparent.png'
    assert 'val RtcInk = Color(0xFF0C1013)' in theme
    assert 'val RtcMint = Color(0xFF2EC27E)' in theme
    assert 'val RtcCivicGold = Color(0xFFD4AF37)' in theme
    assert '@drawable/rtc_splash_logo' in splash
    assert '@drawable/rtc_splash_logo' in splash_background
    assert logo.exists() and logo.stat().st_size > 0
    assert 'fun RtcBrandLockup(' in BRAND_LOCKUP
    assert 'R.drawable.rtc_logo_mark_transparent' in BRAND_LOCKUP
    assert 'import za.org.rtc.community.feature.account.PublicWelcomeScreen' in MAIN
    assert 'PublicWelcomeScreen(' in MAIN
    supplied_splash_logo = ROOT / 'app/src/main/res/drawable-nodpi/rtc_community_logo_transparent.png'
    assert supplied_splash_logo.exists() and supplied_splash_logo.stat().st_size > 0
    with Image.open(supplied_splash_logo) as splash_logo:
        assert splash_logo.mode == 'RGBA'
        assert splash_logo.getchannel('A').getextrema()[0] == 0


def test_reference_administrator_workspace_has_real_guarded_navigation_and_profile_exit():
    workspace = _function_body(ADMIN_WORKSPACE, 'AdminWorkspace', [])
    catalog = (ROOT / 'app/src/main/java/za/org/rtc/community/feature/administration/AdminWorkspaceDestinationCatalog.kt').read_text()
    mfa_screen = _function_body(ADMIN_MFA_SCREEN, 'AdministratorMfaVerificationScreen', [])
    profile = _function_body(ADMIN_MY_WORK, 'MyWorkProfileScreen', ['AssignedSupportCaseCard'])
    bottom_nav = _function_body(NAV_CHROME, 'StaffWorkspaceBottomNavigation', ['StaffWorkspacePane'])
    assert 'const val ADMIN_MFA = "administrator_mfa"' in NAV
    assert 'if (route == RtcRoute.ADMIN_MFA)' in ROUTES
    assert 'ProtectedRoute(RtcRoute.ADMIN_MFA' in MAIN
    assert 'AdministratorMfaVerificationScreen(' in MAIN
    assert 'import za.org.rtc.community.feature.administration.AdminWorkspace' in MAIN
    assert 'Verify and continue' in mfa_screen
    assert 'TotpQrCode(uri = it.uri)' in mfa_screen
    assert 'it.secret' not in mfa_screen
    for title in ['Access management', 'Operational controls', 'Privacy analytics']:
        assert f'title = "{title}"' in catalog
    assert 'route = RtcRoute.ACCESS_MANAGEMENT' in catalog
    assert 'route = RtcRoute.OPERATIONAL_CONTROLS' in catalog
    assert 'route = RtcRoute.ANALYTICS_DASHBOARD' in catalog
    assert 'adminWorkspaceDestinations(session.role)' in workspace
    assert 'resolveAdminDestinationRoute(destination, needsLiveAdministratorMfa)' in workspace
    assert 'AdminNeedsAttentionCard(' in workspace
    assert 'OperationsWorkItemCard(' in workspace
    assert 'internal fun StaffWorkspaceBottomNavigation(' in NAV_CHROME
    assert 'StaffWorkspaceBottomNavigation(' in MAIN
    assert '"Queue"' in bottom_nav and '"Access"' in bottom_nav and '"Controls"' in bottom_nav and '"Analytics"' in bottom_nav
    assert 'Text("Account profile")' in profile
    assert 'viewModel::signOutToPublicWelcome' in profile
    assert 'onClick = {}' not in (HOME_SCREEN + COMMUNITY_FEED + COMMUNITY_DETAIL + EXPLORE_SCREEN + SUPPORT_SCREENS + ACCOUNT_SCREEN + ACCOUNT_NOTIFICATIONS + ADMIN_WORKSPACE + ADMIN_ACCESS + ADMIN_CONTENT + ADMIN_OPERATIONAL + ADMIN_MY_WORK + STAFF_ALERTS)

def test_selected_launcher_icon_is_packaged_for_standard_android_densities():
    manifest = (ROOT / 'app/src/main/AndroidManifest.xml').read_text()
    assert 'android:icon="@mipmap/ic_launcher"' in manifest
    assert 'android:roundIcon="@mipmap/ic_launcher_round"' in manifest
    expected = {
        'mipmap-mdpi': 48,
        'mipmap-hdpi': 72,
        'mipmap-xhdpi': 96,
        'mipmap-xxhdpi': 144,
        'mipmap-xxxhdpi': 192,
    }
    for directory, pixels in expected.items():
        for filename in ('ic_launcher.webp', 'ic_launcher_round.webp'):
            with Image.open(ROOT / 'app/src/main/res' / directory / filename) as icon:
                assert icon.size == (pixels, pixels), (directory, filename, icon.size)
                assert icon.format == 'WEBP'


def test_explore_cards_use_real_counts_without_fabricated_progress():
    explore = _function_body(EXPLORE_SCREEN, 'ExploreScreen', ['ExploreActionRow'])
    assert 'Text("Community Notices"' in explore
    assert 'Text("Projects and Opportunities"' in explore
    assert 'notices.count { it.status == NoticeStatus.PUBLISHED }' in explore
    assert 'count = "${projects.size} available"' in explore
    assert 'count = "${opportunities.size} open"' in explore
    assert 'LinearProgressIndicator' not in explore
    assert 'Community progress' not in explore


def test_global_configuration_cache_is_local_fallback_not_personal_theme_replacement():
    preferences = (ROOT / 'app/src/main/java/za/org/rtc/community/data/local/UserPreferencesStore.kt').read_text()
    assert 'global_ui_configuration' in preferences
    assert 'global_ui_configuration_version' in preferences
    assert 'GlobalUiConfiguration.decodeOrDefault' in preferences
    assert 'GlobalUiConfiguration.decodeOrNull(rawConfiguration) ?: return false' in preferences
    assert 'suspend fun setTheme(value: ThemePreference)' in preferences
