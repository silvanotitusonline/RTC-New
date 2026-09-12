from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
NAV_GRAPH = ROOT / "app/src/main/java/za/org/rtc/community/ui/navigation/RtcCommunityNavGraph.kt"
RPC_CONTRACT = ROOT / "app/src/main/java/za/org/rtc/community/feature/publicreports/data/PublicReportRpcContract.kt"
CANONICAL_REPOSITORY = ROOT / "app/src/main/java/za/org/rtc/community/feature/publicreports/data/SupabasePublicReportRepository.kt"
DUPLICATE_REPOSITORY = ROOT / "app/src/main/java/za/org/rtc/community/feature/publicreports/data/remote/SupabasePublicReportsRepository.kt"


def test_resident_modernisation_routes_are_registered_in_root_nav_host():
    source = NAV_GRAPH.read_text(encoding="utf-8")

    public_reports_index = source.index("publicReportRoutes(navController, session)")
    resident_bindings_index = source.index("residentModernisationBindings(navController, session)")

    assert resident_bindings_index > public_reports_index


def test_community_public_report_actions_open_detail_and_composer_routes():
    source = NAV_GRAPH.read_text(encoding="utf-8")

    assert (
        "onOpenReport = { reportId -> "
        "navController.navigateOverlay(RtcRoute.publicReportDetail(reportId)) }"
    ) in source
    assert (
        "onComposeReport = { "
        "navController.navigateOverlay(RtcRoute.PUBLIC_REPORT_NEW) }"
    ) in source


def test_canonical_verified_only_public_report_repository_remains_the_only_client():
    contract = RPC_CONTRACT.read_text(encoding="utf-8")
    canonical = CANONICAL_REPOSITORY.read_text(encoding="utf-8")

    assert 'const val PAGE = "civic_report_page_v1"' in contract
    assert 'const val VERIFIED_PAGE = "civic_report_page_v2"' in contract
    assert 'const val COMMENT_PAGE = "civic_report_comment_page_v1"' in contract
    assert '"p_cursor_created_at"' in contract
    assert "PublicReportRpcContract.VERIFIED_PAGE" in canonical
    assert "PublicReportRpcContract.PAGE," not in canonical
    assert "PublicReportRpcContract.COMMENT_PAGE" in canonical
    assert not DUPLICATE_REPOSITORY.exists()
