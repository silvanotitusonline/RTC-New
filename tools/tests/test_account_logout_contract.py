from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ACCOUNT_SCREEN = ROOT / "app/src/main/java/za/org/rtc/community/feature/account/AccountScreen.kt"
AUTH_COORDINATOR = ROOT / "app/src/main/java/za/org/rtc/community/app/RtcAuthenticationCoordinator.kt"
REPOSITORY = ROOT / "app/src/main/java/za/org/rtc/community/data/RtcRepository.kt"


def test_account_menu_places_logout_as_final_action():
    source = ACCOUNT_SCREEN.read_text()

    support_index = source.index('title = "Support"')
    logout_index = source.index('title = "Log out"')

    assert logout_index > support_index
    assert 'description = "Sign out of this device and return to the welcome screen."' in source
    assert "Icons.Filled.Logout" in source
    assert "logoutConfirmationOpen = true" in source
    assert 'Text("Log out")' in source
    assert "viewModel.signOutToPublicWelcome()" in source


def test_logout_uses_existing_secure_session_teardown_path():
    coordinator = AUTH_COORDINATOR.read_text()
    repository = REPOSITORY.read_text()

    assert "fun signOutToPublicWelcome()" in coordinator
    assert "scope.launch { repository.signOutToPublicWelcome() }" in coordinator
    assert "suspend fun signOutToPublicWelcome(): Result<Unit>" in repository
    logout_start = repository.index("suspend fun signOutToPublicWelcome(): Result<Unit>")
    logout_body = repository[logout_start:logout_start + 2200]
    assert "supabase.auth.signOut()" in logout_body
    assert "database.cachedSessionDao().logoutAll()" in logout_body
    assert "clearAccountScopedSessionState()" in logout_body
