from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def test_resident_runtime_has_no_authentication_gate():
    nav_sources = "\n".join(
        p.read_text(encoding="utf-8")
        for p in (ROOT / "app/src/main/java/za/org/rtc/community").rglob("*.kt")
        if "navigation" in str(p).lower() or "app" in str(p).lower()
    )
    assert "resident_requires_authentication" not in nav_sources.lower()
    assert "requireResidentAuthentication" not in nav_sources


def test_installation_identity_cannot_grant_security_authority():
    candidates = list((ROOT / "app/src/main/java/za/org/rtc/community").rglob("*InstallationIdentity*.kt"))
    if not candidates:
        raise AssertionError("InstallationIdentity provider has not been implemented")
    source = "\n".join(p.read_text(encoding="utf-8") for p in candidates)
    forbidden = ["SUPABASE_AUTH", "SYSTEM_ADMIN", "RtcRole", "SessionAuthority"]
    for token in forbidden:
        assert token not in source, f"installation identity must not reference security authority: {token}"


def test_privileged_authentication_remains_server_authorized():
    guard = read("app/src/main/java/za/org/rtc/community/feature/administration/AdminGuard.kt")
    assert "admin_access_guard" in guard
    assert "currentUserOrNull" in guard or "currentSessionOrNull" in guard


def test_privileged_sign_out_preserves_anonymous_continuity_contract():
    sources = "\n".join(
        p.read_text(encoding="utf-8")
        for p in (ROOT / "app/src/main/java/za/org/rtc/community").rglob("*.kt")
        if "auth" in p.name.lower() or "session" in p.name.lower() or "repository" in p.name.lower()
    )
    assert "clearAnonymousContinuity" not in sources


def test_resident_mutations_still_cross_server_truth_boundary():
    community = read("app/src/main/java/za/org/rtc/community/data/AuthoritativeCommunityRepository.kt")
    reports = read("app/src/main/java/za/org/rtc/community/publicreports/AuthoritativePublicReportRepository.kt")
    assert "rpc" in community.lower() or "delegate" in community.lower()
    assert "rpc" in reports.lower() or "supabase" in reports.lower()
