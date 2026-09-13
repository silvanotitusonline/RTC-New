from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
REPOSITORY = ROOT / "app/src/main/java/za/org/rtc/community/data/RtcRepository.kt"


def _restore_body() -> str:
    source = REPOSITORY.read_text()
    start = source.index("suspend fun restoreSupabaseSession")
    end = source.index("suspend fun signInWithEmail", start)
    return source[start:end]


def test_session_restoration_waits_for_supabase_auth_initialization():
    restore = _restore_body()

    initialization = restore.index("supabase.auth.awaitInitialization()")
    user_lookup = restore.index("supabase.auth.currentUserOrNull()")
    assert initialization < user_lookup


def test_room_session_cache_never_grants_supabase_auth_authority():
    restore = _restore_body()

    # The Auth plugin owns persisted access/refresh tokens. Room may cache UX/profile data,
    # but a database row must never resurrect authentication after SDK storage is absent,
    # revoked, cleared, or invalid. A local SUPABASE_AUTH projection is allowed only after
    # the initialized SDK itself has returned a non-null current user, and it must fail closed
    # to resident privilege until server role hydration runs.
    user_lookup = restore.index("val user = supabase.auth.currentUserOrNull()")
    missing_sdk_user = restore.index("if (user == null)")
    authority_projection = restore.index("authority = SessionAuthority.SUPABASE_AUTH")

    assert user_lookup < missing_sdk_user < authority_projection
    assert "database.cachedSessionDao().getActiveSession()" not in restore
    assert "resolveLocalRestoredRole" not in restore
    assert "role = UserRole.RESIDENT_A" in restore
    assert "cachedProfile?.role" not in restore
    assert "CachedUserProfileEntity" not in restore


def test_missing_sdk_session_invalidates_stale_login_marker_and_fails_closed():
    restore = _restore_body()

    assert "database.cachedSessionDao().logoutAll()" in restore
    assert "clearAccountScopedSessionState()" in restore
    assert "return@runCatching false" in restore
