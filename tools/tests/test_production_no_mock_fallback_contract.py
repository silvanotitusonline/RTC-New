from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
MAIN_SOURCE = ROOT / "app/src/main/java"
RTC_REPOSITORY = MAIN_SOURCE / "za/org/rtc/community/data/RtcRepository.kt"
PRODUCTION_UX_REPOSITORY = MAIN_SOURCE / "za/org/rtc/community/supabase/ProductionUxRepository.kt"
EVENTS_REPOSITORY = MAIN_SOURCE / "za/org/rtc/community/feature/events/data/remote/SupabaseCommunityEventsRepository.kt"
COMMUNITY_REPOSITORY = MAIN_SOURCE / "za/org/rtc/community/feature/community/SupabaseCommunityRepository.kt"


def _text(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def _function(source: str, signature_fragment: str, next_signature_fragment: str) -> str:
    start = source.index(signature_fragment)
    end = source.index(next_signature_fragment, start)
    return source[start:end]


def test_runtime_source_does_not_depend_on_mock_or_sample_records():
    forbidden_tokens = ("RtcMockData", "CommunityMockData", "SampleCommunityEvents")
    violations: list[str] = []

    for path in MAIN_SOURCE.rglob("*.kt"):
        source = _text(path)
        definitions = {
            "RtcMockData": "object RtcMockData" in source,
            "CommunityMockData": "object CommunityMockData" in source,
            "SampleCommunityEvents": "val SampleCommunityEvents" in source,
        }
        for token in forbidden_tokens:
            if token in source and not definitions[token]:
                violations.append(f"{path.relative_to(ROOT)} -> {token}")

    assert not violations, "production runtime still depends on mock/sample records:\n" + "\n".join(violations)


def test_production_repository_reads_propagate_backend_failures_instead_of_swallowing_them():
    production = _text(PRODUCTION_UX_REPOSITORY)
    boundaries = (
        ("suspend fun mySupportCases()", "suspend fun listAssignedSupportCases()"),
        ("suspend fun listAssignedSupportCases()", "suspend fun updateAssignedSupportCaseState"),
        ("suspend fun communityAlertInbox()", "suspend fun communityAlert(alertId"),
        ("suspend fun adminAnalyticsMetrics", "suspend fun adminAnalyticsLocalities"),
        ("suspend fun operationsWorkQueue()", "suspend fun claimOperationsWorkItem"),
        ("suspend fun moderationQueue", "suspend fun moderationDecideReport"),
        ("suspend fun getDashboardMetrics()", "suspend fun listProjects"),
        ("suspend fun listProjects", "suspend fun listCentres"),
        ("suspend fun listCentres", "suspend fun listOpportunities"),
        ("suspend fun listOpportunities", "suspend fun searchPublicContent"),
        ("suspend fun searchPublicContent", "/** Returns a short-lived signed URL"),
    )

    for start, end in boundaries:
        body = _function(production, start, end)
        assert ".getOrNull()" not in body, f"backend failure is swallowed in {start}"
        assert "RtcMockData" not in body, f"mock data is substituted in {start}"


def test_resident_mutations_never_manufacture_local_success_before_server_confirmation():
    rtc = _text(RTC_REPOSITORY)

    comment = _function(rtc, "suspend fun createCommunityComment", "suspend fun toggleCommunityPostLike")
    like = _function(rtc, "suspend fun toggleCommunityPostLike", "suspend fun searchAccessManagedAccount")
    guidelines = _function(rtc, "suspend fun acceptCommunityGuidelines", "private suspend fun refreshCommunityGuidelinesStatus")
    profile = _function(rtc, "suspend fun updateProfile", "suspend fun setNotificationPreference")
    report = _function(rtc, "suspend fun reportCommunityPost", "suspend fun registerFcmDevice")

    assert "productionUxRepository.createCommunityComment" in comment
    assert "CachedCommentEntity" not in comment
    assert "UUID.randomUUID" not in comment

    assert "productionUxRepository.toggleCommunityPostLike" in like
    assert "viewerHasLiked =" not in like
    assert "database.cachedPostDao().insertPost" not in like

    server_call = guidelines.index("productionUxRepository.acceptCommunityGuidelines")
    persisted = guidelines.index("preferencesStore.setCommunityGuidelinesAccepted", server_call)
    assert persisted > server_call
    assert "recoverCatching" not in guidelines

    assert "SessionAuthority.PUBLIC -> error" in profile
    assert profile.index("productionUxRepository.saveOwnProfile") < profile.index("hydrateSupabaseSession")

    assert "productionUxRepository.reportCommunityPost" in report
    assert "CachedReportEntity" not in report
    assert "Result.success(Unit)" not in report


def test_community_repository_mutations_are_server_confirmed_before_cache_changes():
    community = _text(COMMUNITY_REPOSITORY)
    boundaries = (
        ("override suspend fun createComment", "override suspend fun updateComment"),
        ("override suspend fun updateComment", "override suspend fun deleteComment"),
        ("override suspend fun deleteComment", "override suspend fun deletePost"),
        ("override suspend fun deletePost", "override suspend fun moderateComment"),
        ("override suspend fun moderateComment", "override suspend fun toggleLike"),
        ("override suspend fun toggleLike", "override suspend fun toggleReaction"),
        ("override suspend fun toggleReaction", "override suspend fun repostPost"),
        ("override suspend fun repostPost", "override suspend fun toggleBookmark"),
        ("override suspend fun toggleBookmark", "override suspend fun searchPosts"),
    )

    for start, end in boundaries:
        body = _function(community, start, end)
        assert "optimistic" not in body.lower(), f"optimistic mutation remains in {start}"
        assert "remoteOutcome ?:" not in body, f"local success fallback remains in {start}"

    create = _function(community, "override suspend fun createComment", "override suspend fun updateComment")
    assert create.index("supabase.postgrest.rpc") < create.index("cachedCommentDao.insertComment") if "cachedCommentDao.insertComment" in create else True
    assert "UUID.randomUUID" not in create

    delete_comment = _function(community, "override suspend fun deleteComment", "override suspend fun deletePost")
    if "cachedCommentDao.deleteComment" in delete_comment:
        assert delete_comment.index("supabase.from") < delete_comment.index("cachedCommentDao.deleteComment")

    delete_post = _function(community, "override suspend fun deletePost", "override suspend fun moderateComment")
    if "cachedPostDao.deletePost" in delete_post:
        assert delete_post.index("supabase.from") < delete_post.index("cachedPostDao.deletePost")


def test_system_admin_mfa_status_fails_closed_on_missing_factor_and_auth_errors():
    rtc = _text(RTC_REPOSITORY)
    mfa = _function(rtc, "private suspend fun administratorMfaStatus", "private fun requireStrongPassword")

    assert "getOrDefault(emptyList())" not in mfa
    assert ".getOrNull()" not in mfa
    assert "ENROLLMENT_REQUIRED" in mfa
    assert "VERIFICATION_REQUIRED" in mfa
    assert "currentSessionOrNull()?.accessToken\n            ?: return AdministratorMfaStatus.VERIFICATION_REQUIRED" in mfa
    assert "assurance.current == AuthenticatorAssuranceLevel.AAL2" in mfa


def test_events_repository_starts_empty_and_does_not_manufacture_server_success():
    events = _text(EVENTS_REPOSITORY)

    assert "MutableStateFlow<List<CommunityEvent>>(emptyList())" in events
    assert 'draft.id ?: "event-${System.currentTimeMillis()}"' not in events
    assert 'runCatching {\n            supabase.postgrest.rpc("upsert_community_event"' not in events
    assert 'runCatching {\n            supabase.postgrest.rpc("publish_community_event"' not in events
    assert 'runCatching {\n            supabase.postgrest.rpc("cancel_community_event"' not in events
    assert "Instant.now()" not in events
    assert 'UnsupportedOperationException("Community Event deletion is not exposed by the production backend.' in events
    assert 'UnsupportedOperationException("Community Event RSVP is not exposed by the production backend.' in events
