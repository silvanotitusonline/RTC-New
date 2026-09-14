from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

RTC_REPOSITORY = ROOT / "app/src/main/java/za/org/rtc/community/data/RtcRepository.kt"
PRODUCTION_UX_REPOSITORY = ROOT / "app/src/main/java/za/org/rtc/community/supabase/ProductionUxRepository.kt"
EVENTS_REPOSITORY = ROOT / "app/src/main/java/za/org/rtc/community/feature/events/data/remote/SupabaseCommunityEventsRepository.kt"


def _text(path: Path) -> str:
    return path.read_text(encoding="utf-8")


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

    forbidden = (
        'runCatching {\n            supabase.postgrest.rpc("list_my_support_cases")',
        'runCatching {\n            supabase.postgrest.rpc("list_assigned_support_cases")',
        'runCatching {\n            supabase.from("community_alert_inbox")',
        'runCatching {\n            supabase.postgrest.rpc("admin_privacy_analytics_dashboard")',
        'runCatching {\n            supabase.postgrest.rpc("ops_list_work_queue")',
        'runCatching {\n            supabase.postgrest.rpc("moderation_list_queue")',
        'runCatching {\n            supabase.postgrest.rpc("get_public_directory_metrics")',
        'runCatching {\n            supabase.from("directory_projects")',
        'runCatching {\n            supabase.from("directory_centres")',
        'runCatching {\n            supabase.from("directory_opportunities")',
        'runCatching {\n            supabase.postgrest.rpc(\n                "search_public_directory"',
    )

    for pattern in forbidden:
        assert pattern not in production, f"backend failure is still swallowed near: {pattern}"


def test_events_repository_starts_empty_and_does_not_manufacture_server_success():
    events = _text(EVENTS_REPOSITORY)

    assert "MutableStateFlow<List<CommunityEvent>>(emptyList())" in events
    assert 'draft.id ?: "event-${System.currentTimeMillis()}"' not in events
    assert 'runCatching {\n            supabase.postgrest.rpc("upsert_community_event"' not in events
    assert 'runCatching {\n            supabase.postgrest.rpc("publish_community_event"' not in events
    assert 'runCatching {\n            supabase.postgrest.rpc("cancel_community_event"' not in events
