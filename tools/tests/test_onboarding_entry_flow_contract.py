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


def test_welcome_get_started_creates_account_and_sign_in_opens_login():
    source = WELCOME.read_text()

    get_started_label = 'text = welcome.primaryActionLabel.ifBlank { "Get started" }'
    sign_in_label = 'text = welcome.secondaryActionLabel.ifBlank { "Sign in" }'
    assert get_started_label in source
    assert sign_in_label in source

    get_started_block = source[source.rfind("Box(", 0, source.index(get_started_label)):source.index(get_started_label)]
    sign_in_block = source[source.rfind("Box(", 0, source.index(sign_in_label)):source.index(sign_in_label)]
    assert 'mode = "CREATE"' in get_started_block
    assert 'mode = "SIGN_IN"' in sign_in_block


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
