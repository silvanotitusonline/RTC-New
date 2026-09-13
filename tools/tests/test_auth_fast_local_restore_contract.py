from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
REPOSITORY = ROOT / "app/src/main/java/za/org/rtc/community/data/RtcRepository.kt"
COORDINATOR = ROOT / "app/src/main/java/za/org/rtc/community/app/RtcAuthenticationCoordinator.kt"
VIEW_MODEL = ROOT / "app/src/main/java/za/org/rtc/community/app/RtcViewModel.kt"


def _body(source: str, start_marker: str, end_marker: str) -> str:
    start = source.index(start_marker)
    end = source.index(end_marker, start)
    return source[start:end]


def test_restore_gate_is_not_bounded_by_arbitrary_two_point_five_second_timeout():
    coordinator = COORDINATOR.read_text()
    restore = _body(coordinator, "suspend fun restoreSession()", "fun registerCurrentFcmToken")

    assert "withTimeoutOrNull(2500L)" not in restore
    assert "repository.restoreSupabaseSession()" in restore


def test_repository_restore_is_local_after_supabase_auth_initialization():
    repository = REPOSITORY.read_text()
    restore = _body(repository, "suspend fun restoreSupabaseSession", "suspend fun signInWithEmail")

    assert "supabase.auth.awaitInitialization()" in restore
    assert "supabase.auth.currentUserOrNull()" in restore
    assert "hydrateSupabaseSession()" not in restore
    assert "recordPrivacyAnalyticsAppActivity()" not in restore
    assert "productionUxRepository" not in restore


def test_locally_restored_sdk_identity_fails_closed_to_resident_until_server_hydration():
    repository = REPOSITORY.read_text()
    restore = _body(repository, "suspend fun restoreSupabaseSession", "suspend fun signInWithEmail")

    user_lookup = restore.index("val user = supabase.auth.currentUserOrNull()")
    missing_sdk_user = restore.index("if (user == null)")
    cached_profile = restore.index("database.cachedUserProfileDao().getProfile(user.id)")
    resident_role = restore.index("role = UserRole.RESIDENT_A")
    sdk_authority = restore.index("authority = SessionAuthority.SUPABASE_AUTH")

    assert user_lookup < missing_sdk_user < cached_profile < resident_role < sdk_authority
    assert "administratorMfaStatus = AdministratorMfaStatus.NOT_REQUIRED" in restore
    assert "resolveLocalRestoredRole" not in restore
    assert "cachedProfile?.role" not in restore


def test_network_hydration_still_runs_after_local_restore_before_staff_initialization():
    view_model = VIEW_MODEL.read_text()
    init_body = _body(view_model, "init {", "fun consumeNotificationPermissionPrompt")

    restore = init_body.index("authenticationCoordinator.restoreSession()")
    refresh = init_body.index("repository.refreshLiveContent()")
    staff = init_body.index("administrationCoordinator.refreshOperationsHub()")
    assert restore < refresh < staff
