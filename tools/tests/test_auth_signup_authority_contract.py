from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
REPOSITORY = ROOT / "app/src/main/java/za/org/rtc/community/data/RtcRepository.kt"
AUTH_COORDINATOR = ROOT / "app/src/main/java/za/org/rtc/community/app/RtcAuthenticationCoordinator.kt"


def _signup_body() -> str:
    source = REPOSITORY.read_text()
    start = source.index("suspend fun signUpWithEmail")
    end = source.index("suspend fun requestPasswordRecovery", start)
    return source[start:end]


def test_email_signup_never_manufactures_an_authenticated_local_session():
    signup = _signup_body()

    # Account creation must be authoritative at Supabase. A failed request must escape the
    # surrounding Result and a confirmation-pending signup must not be represented locally as
    # an authenticated session before the user has actually established one.
    assert 'supabase.auth.signUpWith(Email, "rtc://community")' in signup
    assert "runCatching {\n            supabase.auth.signUpWith" not in signup
    assert "_session.value = RtcSession" not in signup
    assert "database.cachedSessionDao().upsertSession" not in signup
    assert "database.cachedUserProfileDao().insertProfile" not in signup
    assert "SessionAuthority.SUPABASE_AUTH" not in signup
    assert "recordPrivacyAnalyticsAppActivity()" not in signup
    assert "refreshLiveContent()" not in signup
    assert "enqueueUploadRecovery()" not in signup


def test_confirmation_pending_signup_stays_on_the_authentication_surface():
    coordinator = AUTH_COORDINATOR.read_text()
    start = coordinator.index("fun signUpWithEmail")
    end = coordinator.index("fun dismissAuthenticationMessage", start)
    signup_ui = coordinator[start:end]

    assert "confirmationRequired = true" in signup_ui
    assert "completeAuthentication()" not in signup_ui
