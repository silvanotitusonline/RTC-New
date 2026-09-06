from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SMOKE = ROOT / "app/src/androidTest/java/za/org/rtc/community/ResidentSurfaceSmokeTest.kt"
WORKFLOW = ROOT / ".github/workflows/android-ci.yml"


def test_resident_surface_smoke_suite_covers_five_destination_chrome_without_network_dependencies():
    assert SMOKE.exists(), "Resident Compose smoke suite is missing"
    smoke = SMOKE.read_text(encoding="utf-8")
    assert "class ResidentSurfaceSmokeTest" in smoke
    for test_name in (
        "homeSurfaceRendersWithLocalState",
        "communitySurfaceRendersWithLocalState",
        "exploreSurfaceRendersWithLocalState",
        "supportSurfaceRendersWithLocalState",
        "fiveDestinationResidentChromeRendersWithoutLegacySupportTab",
    ):
        assert f"fun {test_name}()" in smoke
    for screen in ("HomeScreen(", "CommunityScreen(", "ExploreScreen(", "SupportScreen("):
        assert screen in smoke
    for forbidden in (
        "RtcViewModel",
        "RtcRepository",
        "SupabaseClient",
        "http://",
        "https://",
    ):
        assert forbidden not in smoke, f"Smoke test must remain network-free: {forbidden}"


def test_ci_compiles_compose_instrumentation_smoke_tests():
    workflow = WORKFLOW.read_text(encoding="utf-8")
    assert "- name: Compile Compose smoke tests" in workflow
    assert "assembleDebugAndroidTest" in workflow
    assert "app/build/outputs/apk/androidTest/debug/*.apk" in workflow
