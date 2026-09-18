from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def test_profile_and_settings_actions_are_real_click_targets():
    hub = read("app/src/main/java/za/org/rtc/community/feature/account/AccountHubScreen.kt")
    screen = read("app/src/main/java/za/org/rtc/community/feature/account/AccountScreen.kt")
    nav = read("app/src/main/java/za/org/rtc/community/ui/navigation/RtcCommunityNavGraph.kt")
    assert 'contentDescription = "Open resident profile"' in hub
    assert "onClick = onOpenProviderProfile" in hub
    assert "onOpenProfile = { navController.navigateOverlay(RtcRoute.MY_WORK) }" in nav
    assert "onOpenNotifications = { navController.navigateOverlay(RtcRoute.NOTIFICATIONS) }" in nav
    assert "onOpenNotifications = onOpenNotifications" in screen
    assert "showPrivacyDialog" in screen
    assert "viewModel::setTheme" in screen


def test_post_submission_has_client_idempotency_and_terminal_ui_state():
    migration = read("supabase/migrations/20260918030000_community_post_client_idempotency.sql")
    composer = read("app/src/main/java/za/org/rtc/community/feature/community/PostComposer.kt")
    coordinator = read("app/src/main/java/za/org/rtc/community/app/RtcResidentCoordinator.kt")
    repository = read("app/src/main/java/za/org/rtc/community/supabase/ProductionUxRepository.kt")
    feed = read("app/src/main/java/za/org/rtc/community/feature/community/CommunityFeedScreen.kt")
    assert "client_post_id" in migration
    assert "community_posts_author_client_post_id_uq" in migration
    assert "p_client_post_id" in migration and "p_client_post_id" in repository
    assert "val clientPostId = rememberSaveable" in composer
    assert "enabled = !isSubmitting" in composer
    assert "isSubmitted || isSubmitting" in composer
    assert "clientPostId" in coordinator
    assert "composerOpen = false" in feed
    assert "communityViewModel.refresh()" in feed


def test_media_and_sync_feedback_are_bounded_and_stable():
    media = read("app/src/main/java/za/org/rtc/community/feature/community/CommunityMedia.kt")
    banner = read("app/src/main/java/za/org/rtc/community/ui/components/LiveSyncStatusBanner.kt")
    assert "delay(350)" in media
    assert "Image could not be loaded." in media
    assert 'text = "System Live • Synced at $formattedTime (#$syncCount)"' in banner
    assert "Updating system across all users" not in banner


def test_public_report_picker_and_manual_location_survive_recreation():
    screen = read("app/src/main/java/za/org/rtc/community/feature/publicreports/presentation/PublicReportComposerScreen.kt")
    view_model = read("app/src/main/java/za/org/rtc/community/feature/publicreports/presentation/PublicReportComposerViewModel.kt")
    assert "takePersistableUriPermission" in screen
    assert "Intent.FLAG_GRANT_READ_URI_PERMISSION" in screen
    assert 'savedStateHandle["draft_public_location"]' in view_model
    assert 'savedStateHandle["draft_exact_address"]' in view_model
    assert 'get<String>("draft_public_location")' in view_model
    assert 'get<String>("draft_exact_address")' in view_model


def test_public_report_feed_collects_repository_snapshot_stream():
    view_model = read("app/src/main/java/za/org/rtc/community/feature/publicreports/presentation/PublicReportViewModel.kt")
    home = read("app/src/main/java/za/org/rtc/community/feature/home/HomeViewModel.kt")
    assert "repository.dashboardUpdates.collect" in view_model
    assert "repository.dashboardUpdates.collect" in home
    assert "_feed.update { it.copy(dashboard = dashboard) }" in view_model


def test_report_filters_and_form_use_compact_material_layout():
    filters = read("app/src/main/java/za/org/rtc/community/feature/publicreports/presentation/PublicReportFilterSheet.kt")
    composer = read("app/src/main/java/za/org/rtc/community/feature/publicreports/presentation/PublicReportComposerScreen.kt")
    assert "ModalBottomSheet" in filters
    assert "LazyRow" in filters
    assert "Filter reports" in filters
    assert "ElevatedCard" in composer
    assert "supportingText" in composer


def test_community_map_is_removed_from_explore_and_onboarding():
    explore = read("app/src/main/java/za/org/rtc/community/feature/explore/ExploreScreen.kt")
    onboarding = read("app/src/main/java/za/org/rtc/community/feature/onboarding/InteractiveOnboardingTutorial.kt")
    assert "InteractiveMunicipalCanvasMap" not in explore
    assert "Community Activity Map" not in explore
    assert "MapTutorialStep" not in onboarding
    assert "Open Community Map" not in onboarding
    assert "val totalSteps = 2" in onboarding
    assert not (ROOT / "app/src/main/java/za/org/rtc/community/feature/explore/InteractiveMunicipalCanvasMap.kt").exists()


def test_account_preferences_are_functional_and_theme_choices_are_persisted():
    menu = read("app/src/main/java/za/org/rtc/community/feature/account/AccountSettingsMenu.kt")
    screen = read("app/src/main/java/za/org/rtc/community/feature/account/AccountScreen.kt")
    repository = read("app/src/main/java/za/org/rtc/community/data/RtcRepository.kt")
    root = read("app/src/main/java/za/org/rtc/community/ui/config/RtcConfiguredAppRoot.kt")
    assert "ThemePreference.entries" in menu
    assert "onSetTheme: (ThemePreference) -> Unit" in menu
    assert "onSetNotificationPreference: (String, Boolean) -> Unit" in menu
    assert "viewModel::setTheme" in screen
    assert "viewModel::setNotificationPreference" in screen
    assert "viewModel.toggleReadingMode()" in screen
    assert "viewModel.saveDeclaredLocality(localityDraft)" in screen
    assert "fun setTheme(preference: ThemePreference)" in repository
    assert "preferencesStore.setTheme(persisted.themePreference)" in repository
    assert "preference = session.darkMode" in root
