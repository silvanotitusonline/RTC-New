from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
REPOSITORY = ROOT / "app/src/main/java/za/org/rtc/community/feature/dailypost/data/DailyPostRepository.kt"
VIEW_MODEL = ROOT / "app/src/main/java/za/org/rtc/community/feature/dailypost/ui/DailyPostViewModel.kt"
STUDIO = ROOT / "app/src/main/java/za/org/rtc/community/feature/dailypost/ui/DailyPostStudioScreen.kt"


def test_daily_post_repository_uses_authoritative_supabase_lifecycle():
    source = REPOSITORY.read_text()
    for rpc in (
        '"daily_post_page_v1"',
        '"daily_post_admin_page_v1"',
        '"daily_post_get_v1"',
        '"daily_post_save_draft_v1"',
        '"daily_post_publish_v1"',
        '"daily_post_archive_v1"',
    ):
        assert rpc in source
    assert "SupabaseClient" in source
    assert "dao.insertArticles" in source
    assert "deletePublishedNotIn" in source


def test_daily_post_mutations_report_failures_instead_of_claiming_success():
    source = VIEW_MODEL.read_text()
    assert "runCatching" in source
    assert "could not be published" in source
    assert "could not be saved" in source
    assert "could not be archived" in source


def test_daily_post_studio_validates_preview_publish_and_previews_notifications():
    source = STUDIO.read_text()
    assert "Add a headline and article content before publishing." in source
    assert "Push notification" in source
    assert "NOTIFICATION PREVIEW" in source
    assert "p_push_enabled" not in source  # notification preference belongs to the repository contract
