from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
REPOSITORY = ROOT / "app/src/main/java/za/org/rtc/community/data/RtcRepository.kt"
COORDINATOR = ROOT / "app/src/main/java/za/org/rtc/community/app/RtcResidentCoordinator.kt"


def _body(source: str, start_marker: str, end_marker: str) -> str:
    start = source.index(start_marker)
    end = source.index(end_marker, start)
    return source[start:end]


def test_community_post_publication_returns_server_authoritative_result():
    repository = REPOSITORY.read_text()
    create_post = _body(repository, "suspend fun createPost(", "suspend fun submitSupportRequest")

    assert "productionUxRepository.createCommunityPost" in create_post
    assert "runCatching { productionUxRepository.createCommunityPost" not in create_post
    assert "Result.success(postId)" not in create_post
    assert "UUID.randomUUID()" not in create_post
    assert "database.cachedPostDao().insertPost" not in create_post
    assert "_posts.value =" not in create_post


def test_draft_cleanup_and_refresh_happen_only_after_server_success():
    repository = REPOSITORY.read_text()
    create_post = _body(repository, "suspend fun createPost(", "suspend fun submitSupportRequest")

    server_call = create_post.index("val result = productionUxRepository.createCommunityPost")
    success_gate = create_post.index("if (result.isSuccess)")
    discard = create_post.index("discardDraft(DraftArea.COMMUNITY)")
    refresh = create_post.index("refreshLiveContent()")
    result_return = create_post.index("return result")

    assert server_call < success_gate < discard < refresh < result_return


def test_resident_coordinator_only_reports_published_on_repository_success():
    coordinator = COORDINATOR.read_text()
    create_post = _body(coordinator, "fun createPost(", "fun submitSupportRequest")

    repository_call = create_post.index("repository.createPost(text, mediaUris)")
    success_handler = create_post.index(".onSuccess")
    published_message = create_post.index('message = "Community post published."')
    failure_handler = create_post.index(".onFailure")

    assert repository_call < success_handler < published_message < failure_handler
