from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ACCOUNT_SCREEN = ROOT / "app/src/main/java/za/org/rtc/community/feature/account/AccountScreen.kt"
AUTH_COORDINATOR = ROOT / "app/src/main/java/za/org/rtc/community/app/RtcAuthenticationCoordinator.kt"
REPOSITORY = ROOT / "app/src/main/java/za/org/rtc/community/data/RtcRepository.kt"


def test_account_profile_exposes_confirmed_logout_action():
    source = ACCOUNT_SCREEN.read_text()

    assert 'title = "Log out"' in source
    assert 'description = "Sign out of this device and return to the welcome screen."' in source
    assert "Icons.Filled.Logout" in source
    assert "logoutConfirmationOpen = true" in source
    assert 'Text("Log out")' in source
    assert "viewModel.signOutToPublicWelcome()" in source


def test_logout_keeps_existing_secure_session_teardown_path():
    coordinator = AUTH_COORDINATOR.read_text()
    repository = REPOSITORY.read_text()

    assert "fun signOutToPublicWelcome() = authenticationCoordinator.signOutToPublicWelcome()" in coordinator.replace("\n", " ") or "fun signOutToPublicWelcome()" in coordinator
    assert "suspend fun signOutToPublicWelcome(): Result<Unit>" in repository
    logout_start = repository.index("suspend fun signOutToPublicWelcome(): Result<Unit>")
    logout_end = repository.index("suspend fun enrollSystemAdministratorTotp", logout_start)
    logout_body = repository[logout_start:logout_end]
    assert "supabase.auth.signOut()" in logout_body
    assert "database.cachedSessionDao().logoutAll()" in logout_body
    assert "clearAccountScopedSessionState()" in logout_body
