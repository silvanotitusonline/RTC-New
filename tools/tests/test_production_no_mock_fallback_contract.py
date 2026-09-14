from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

RTC_REPOSITORY = ROOT / "app/src/main/java/za/org/rtc/community/data/RtcRepository.kt"
PRODUCTION_UX_REPOSITORY = ROOT / "app/src/main/java/za/org/rtc/community/supabase/ProductionUxRepository.kt"
EVENTS_REPOSITORY = ROOT / "app/src/main/java/za/org/rtc/community/feature/events/data/remote/SupabaseCommunityEventsRepository.kt"


def _text(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def _function(source: str, signature_fragment: str, next_signature_fragment: str) -> str:
    start = source.index(signature_fragment)
    end = source.index(next_signature_fragment, start)
    return source[start:end]


def test_production_repositories_do_not_substitute_mock_or_sample_records():
    rtc = _text(RTC_REPOSITORY)
    production = _text(PRODUCTION_UX_REPOSITORY)
    events = _text(EVENTS_REPOSITORY)

    assert "RtcMockData" not in rtc
    assert "CommunityMockData" not in rtc
    assert "SampleCommunityEvents" not in rtc
    assert "RtcMockData" not in production
    assert "SampleCommunityEvents" not in events


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


def test_events_repository_starts_empty_and_does_not_manufacture_server_success():
    events = _text(EVENTS_REPOSITORY)

    assert "MutableStateFlow<List<CommunityEvent>>(emptyList())" in events
    assert 'draft.id ?: "event-${System.currentTimeMillis()}"' not in events
    assert 'runCatching {\n            supabase.postgrest.rpc("upsert_community_event"' not in events
    assert 'runCatching {\n            supabase.postgrest.rpc("publish_community_event"' not in events
    assert 'runCatching {\n            supabase.postgrest.rpc("cancel_community_event"' not in events
    assert 'UnsupportedOperationException("Community Event deletion is not exposed by the production backend.' in events
    assert 'UnsupportedOperationException("Community Event RSVP is not exposed by the production backend.' in events
