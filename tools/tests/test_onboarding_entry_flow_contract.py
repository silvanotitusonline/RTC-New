from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
WELCOME = ROOT / "app/src/main/java/za/org/rtc/community/feature/account/PublicWelcomeScreen.kt"
APP = ROOT / "app/src/main/java/za/org/rtc/community/ui/navigation/RtcCommunityApp.kt"
REPOSITORY = ROOT / "app/src/main/java/za/org/rtc/community/data/RtcRepository.kt"
PREFERENCES = ROOT / "app/src/main/java/za/org/rtc/community/data/local/UserPreferencesStore.kt"


def _function_body(source: str, start_marker: str, end_marker: str) -> str:
    start = source.index(start_marker)
    end = source.index(end_marker, start)
    return source[start:end]


def test_get_started_opens_auth_choice_before_sign_in_form():
    source = WELCOME.read_text()

    assert 'mode = "AUTH_MENU"' in source
    assert 'mode == "AUTH_MENU"' in source
    assert '"Get started"' in source
    assert '"Sign in"' in source
    assert '"Create account"' in source


def test_sign_in_and_create_forms_return_to_auth_choice():
    source = WELCOME.read_text()

    assert 'if (isForgotPassword) mode = "SIGN_IN" else mode = "AUTH_MENU"' in source


def test_tutorial_is_rendered_for_authenticated_accounts_only():
    source = APP.read_text()

    assert "if (isInteractiveTutorialVisible && session.role != UserRole.ANONYMOUS_PUBLIC)" in source
    assert "InteractiveOnboardingTutorial(" in source


def test_first_login_tutorial_is_not_blocked_by_guidelines_refresh():
    source = REPOSITORY.read_text()

    email_sign_in = _function_body(
        source,
        "suspend fun signInWithEmail",
        "suspend fun signInWithGoogleIdToken",
    )
    google_sign_in = _function_body(
        source,
        "suspend fun signInWithGoogleIdToken",
        "/**\n     * Email confirmation remains enabled",
    )
    guidelines_refresh = _function_body(
        source,
        "private suspend fun refreshCommunityGuidelinesStatus",
        "suspend fun checkAndTriggerOnboardingTutorial",
    )

    assert "checkAndTriggerOnboardingTutorial" in email_sign_in
    assert "checkAndTriggerOnboardingTutorial" in google_sign_in
    assert "checkAndTriggerOnboardingTutorial" not in guidelines_refresh


def test_tutorial_completion_remains_account_scoped_and_persistent():
    source = PREFERENCES.read_text()

    assert 'booleanPreferencesKey("tutorial_completed_${userId.ifBlank { "default" }}")' in source
    assert "setInteractiveTutorialCompleted(userId, true)" in REPOSITORY.read_text()
