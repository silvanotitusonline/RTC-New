from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
PRODUCTION_UX = (ROOT / "app/src/main/java/za/org/rtc/community/supabase/ProductionUxRepository.kt").read_text()
PUBLIC_REPORTS = (ROOT / "app/src/main/java/za/org/rtc/community/feature/publicreports/data/SupabasePublicReportRepository.kt").read_text()
HOME_REPORTS = (ROOT / "app/src/main/java/za/org/rtc/community/feature/home/HomePublicReportViewModel.kt").read_text()
RTC_REPOSITORY = (ROOT / "app/src/main/java/za/org/rtc/community/data/RtcRepository.kt").read_text()
RTC_MOCKS = (ROOT / "app/src/main/java/za/org/rtc/community/data/RtcMockData.kt").read_text()
COMMUNITY_MOCKS = (ROOT / "app/src/main/java/za/org/rtc/community/feature/community/CommunityMockData.kt").read_text()
EVENT_MODELS = (ROOT / "app/src/main/java/za/org/rtc/community/feature/events/domain/CommunityEventModels.kt").read_text()


def test_public_reports_are_backend_authoritative_not_seeded_with_samples():
    assert "PublicReportMockData" not in PUBLIC_REPORTS
    assert "localReports" not in PUBLIC_REPORTS
    assert "localComments" not in PUBLIC_REPORTS
    assert "authoritativeResult" in PUBLIC_REPORTS
    for rpc in (
        "PublicReportRpcContract.TIMELINE",
        "PublicReportRpcContract.COMMENT_PAGE",
        "PublicReportRpcContract.ADD_COMMENT",
        "PublicReportRpcContract.SET_VOTE",
        "PublicReportRpcContract.VERIFIED_DASHBOARD",
    ):
        assert rpc in PUBLIC_REPORTS


def test_home_snapshot_is_sourced_from_public_report_repository():
    assert "private val repository: PublicReportRepository" in HOME_REPORTS
    assert "repository.dashboard()" in HOME_REPORTS
    assert "PublicReportMockData" not in HOME_REPORTS


def test_global_search_is_backend_authoritative_and_fixture_provider_is_empty():
    assert "supabase.postgrest.rpc(" in PRODUCTION_UX
    assert '"search_public_directory"' in PRODUCTION_UX
    assert 'put("p_query", query.trim())' in PRODUCTION_UX
    assert 'put("p_limit", pageSize)' in PRODUCTION_UX
    assert 'put("p_offset", offset)' in PRODUCTION_UX
    assert "getSamplePublicSearchResults(query: String): List<PublicSearchResult> = emptyList()" in RTC_MOCKS
    assert 'PublicSearchResult("PROJECT"' not in RTC_MOCKS
    assert 'PublicSearchResult("CENTRE"' not in RTC_MOCKS
    assert 'PublicSearchResult("OPPORTUNITY"' not in RTC_MOCKS


def test_legacy_fixture_providers_cannot_fabricate_production_content():
    for forbidden in (
        'id = "proj_',
        'id = "centre_',
        'id = "opp_',
        'id = "notice_',
        'id = "alert_',
        'id = "case_',
        'id = "work_',
        'id = "mock_post_',
        'id = "event-',
        "getSamplePosts(context: Context? = null): List<CommunityPost> {",
    ):
        assert forbidden not in RTC_MOCKS + COMMUNITY_MOCKS + EVENT_MODELS

    assert "fun getSamplePosts(context: Context? = null): List<CommunityPost> = emptyList()" in COMMUNITY_MOCKS
    assert "fun getSampleComments(postId: String): List<CommunityComment> = emptyList()" in COMMUNITY_MOCKS
    assert "val SampleCommunityEvents: List<CommunityEvent> = emptyList()" in EVENT_MODELS


def test_room_cache_remains_cache_only_for_real_community_records():
    assert "database.cachedPostDao()" in RTC_REPOSITORY
    assert "database.cachedCommentDao()" in RTC_REPOSITORY
    assert "productionUxRepository.publishedCommunityPosts()" in RTC_REPOSITORY
    assert "productionUxRepository.communityComments(" in RTC_REPOSITORY
