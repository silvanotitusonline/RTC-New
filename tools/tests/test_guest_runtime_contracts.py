from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def text(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def test_guest_entry_is_persisted_without_forging_authentication():
    preferences = text("app/src/main/java/za/org/rtc/community/data/local/UserPreferencesStore.kt")
    entry = text("app/src/main/java/za/org/rtc/community/feature/account/ResidentEntryViewModel.kt")
    app = text("app/src/main/java/za/org/rtc/community/ui/navigation/RtcCommunityApp.kt")

    assert 'booleanPreferencesKey("resident_entry_granted")' in preferences
    assert "val residentEntryGranted: Flow<Boolean>" in preferences
    assert "suspend fun setResidentEntryGranted(granted: Boolean)" in preferences
    assert "val residentEntryGranted" in entry
    assert "preferences.setResidentEntryGranted(true)" in entry
    assert "preferences.setResidentEntryGranted(false)" in entry
    assert "val residentEntryGranted by residentEntryViewModel.residentEntryGranted.collectAsStateWithLifecycle()" in app
    assert "session.role == UserRole.ANONYMOUS_PUBLIC && !residentEntryGranted" in app
    assert "PublicWelcomeGuestHost(" in app
    assert "role = UserRole.RESIDENT_A" not in entry
    assert "SessionAuthority.SUPABASE_AUTH" not in entry


def test_authenticated_session_clears_stale_guest_entry_for_future_sign_out():
    app = text("app/src/main/java/za/org/rtc/community/ui/navigation/RtcCommunityApp.kt")

    assert "LaunchedEffect(session.authority, residentEntryGranted)" in app
    assert "session.authority == SessionAuthority.SUPABASE_AUTH && residentEntryGranted" in app
    authenticated_reset = app.split("LaunchedEffect(session.authority, residentEntryGranted)", 1)[1].split("\n    }", 1)[0]
    assert "residentEntryViewModel.returnToWelcome()" in authenticated_reset


def test_selected_language_is_process_stable_and_provided_to_resident_ui():
    preferences = text("app/src/main/java/za/org/rtc/community/data/local/UserPreferencesStore.kt")
    entry = text("app/src/main/java/za/org/rtc/community/feature/account/ResidentEntryViewModel.kt")
    app = text("app/src/main/java/za/org/rtc/community/ui/navigation/RtcCommunityApp.kt")

    assert 'stringPreferencesKey("onboarding_language")' in preferences
    assert "val applicationLanguage = preferences.onboardingLanguage.stateIn" in entry
    assert "val applicationLanguage by residentEntryViewModel.applicationLanguage.collectAsStateWithLifecycle()" in app
    assert "LocalRtcApplicationLanguage provides applicationLanguage" in app


def test_guest_mutations_are_blocked_before_repository_write_and_offer_sign_in():
    coordinator = text("app/src/main/java/za/org/rtc/community/app/RtcResidentCoordinator.kt")
    app = text("app/src/main/java/za/org/rtc/community/ui/navigation/RtcCommunityApp.kt")

    assert "private fun requireAuthenticated(action: String): Boolean" in coordinator
    assert "repository.session.value.authority == SessionAuthority.SUPABASE_AUTH" in coordinator
    for action in [
        "acceptCommunityGuidelines",
        "createCommunityComment",
        "toggleCommunityPostLike",
        "updateCommunityComment",
        "deleteCommunityComment",
        "markNotificationsRead",
        "reportCommunityPost",
        "createPost",
        "submitSupportRequest",
        "addSupportCaseMessage",
    ]:
        block = coordinator.split(f"fun {action}", 1)[1].split("\n    fun ", 1)[0]
        assert "requireAuthenticated(" in block, f"{action} must fail closed for guest sessions"
    assert 'actionLabel = "Sign in"' in app
    assert "SnackbarResult.ActionPerformed" in app
    assert "residentEntryViewModel.returnToWelcome()" in app
