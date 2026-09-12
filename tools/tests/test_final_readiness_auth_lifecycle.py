from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
REPOSITORY = ROOT / "app/src/main/java/za/org/rtc/community/data/RtcRepository.kt"


def _function_body(text: str, signature: str, next_signature: str) -> str:
    start = text.index(signature)
    end = text.index(next_signature, start)
    return text[start:end]


def test_sign_out_fails_closed_locally_even_when_remote_sign_out_fails():
    text = REPOSITORY.read_text(encoding="utf-8")
    body = _function_body(
        text,
        "suspend fun signOutToPublicWelcome()",
        "suspend fun enrollSystemAdministratorTotp()",
    )

    assert "suspend fun signOutToPublicWelcome(): Result<Unit> = runCatching" not in body
    assert "val wasSupabaseAuthenticated" in body
    assert "runCatching { supabase.auth.signOut() }" in body
    assert "runCatching { supabase.auth.clearSession() }" in body
    assert "clearAccountScopedSessionState()" in body
    assert body.index("clearAccountScopedSessionState()") > body.index("runCatching { supabase.auth.signOut() }")


def test_sign_out_purges_all_account_and_staff_scoped_memory():
    text = REPOSITORY.read_text(encoding="utf-8")
    body = _function_body(
        text,
        "private fun clearAccountScopedSessionState()",
        "/** Synthetic role switching",
    )

    required_resets = (
        "_session.value = publicSession()",
        "_cases.value = emptyList()",
        "_supportCaseMessages.value = emptyList()",
        "_assignedSupportCases.value = emptyList()",
        "_workQueue.value = emptyList()",
        "_aiProposals.value = emptyList()",
        "_accessManagedAccount.value = null",
        "_accessRoleChangeRequests.value = emptyList()",
        "_accessRoleAuditEvents.value = emptyList()",
        "_adminAnalyticsDashboard.value = AdminAnalyticsDashboard()",
        "_adminAnalyticsLocalities.value = emptyList()",
        "_adminAnalyticsAccountProfile.value = null",
        "_adminAnalyticsAuditEvents.value = emptyList()",
        "_staffWorkPreferences.value = StaffWorkPreferences()",
        "_operationsWorkQueue.value = emptyList()",
        "_operationsControls.value = OperationsControlState()",
        "_operationalIncidents.value = emptyList()",
        "_systemHealth.value = emptyList()",
        "_administrativeActivity.value = emptyList()",
        "_moderationQueue.value = emptyList()",
        "_moderationAppeals.value = emptyList()",
        "_editorialNotices.value = emptyList()",
        "_notifications.value = emptyList()",
        "_pendingSyncCount.value = 0",
    )
    for reset in required_resets:
        assert reset in body, f"sign-out isolation is missing: {reset}"


def test_sdk_session_status_revocation_fails_closed_and_refresh_rehydrates_roles():
    text = REPOSITORY.read_text(encoding="utf-8")

    assert "import io.github.jan.supabase.auth.status.SessionSource" in text
    assert "import io.github.jan.supabase.auth.status.SessionStatus" in text
    assert "supabase.auth.sessionStatus.collectLatest { status ->" in text

    lifecycle = _function_body(
        text,
        "private suspend fun handleSessionStatus(status: SessionStatus)",
        "private fun clearAccountScopedSessionState()",
    )
    assert "is SessionStatus.NotAuthenticated" in lifecycle
    assert "is SessionStatus.RefreshFailure" in lifecycle
    assert lifecycle.count("clearAccountScopedSessionState()") >= 2
    assert "is SessionStatus.Authenticated" in lifecycle
    assert "status.source is SessionSource.Storage" in lifecycle
    assert "status.source is SessionSource.Refresh" in lifecycle
    assert "hydrateSupabaseSession()" in lifecycle
    assert "supabase.auth.clearSession()" not in lifecycle
