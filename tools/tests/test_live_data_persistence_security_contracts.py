from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
PRODUCTION_UX = (ROOT / "app/src/main/java/za/org/rtc/community/supabase/ProductionUxRepository.kt").read_text()
PUBLIC_REPORTS = (ROOT / "app/src/main/java/za/org/rtc/community/feature/publicreports/data/SupabasePublicReportRepository.kt").read_text()
HOME_REPORTS = (ROOT / "app/src/main/java/za/org/rtc/community/feature/home/HomePublicReportViewModel.kt").read_text()
RTC_REPOSITORY = (ROOT / "app/src/main/java/za/org/rtc/community/data/RtcRepository.kt").read_text()


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


def test_global_search_is_backend_authoritative():
    assert 'postgrest.rpc("public_search"' in PRODUCTION_UX
    search_start = PRODUCTION_UX.index("suspend fun searchPublicContent(")
    search_body = PRODUCTION_UX[search_start: search_start + 2500]
    assert "RtcMockData" not in search_body
    assert "getSamplePublicSearchResults" not in search_body


def test_production_repository_does_not_seed_community_content_from_mock_data():
    assert "CommunityMockData" not in RTC_REPOSITORY
    assert "SampleCommunityEvents" not in RTC_REPOSITORY
    assert "getSamplePosts" not in RTC_REPOSITORY
