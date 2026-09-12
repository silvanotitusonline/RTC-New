from pathlib import Path
import re


ROOT = Path(__file__).resolve().parents[2]
THEME = ROOT / "app/src/main/java/za/org/rtc/community/ui/theme"
MAIN = "\n".join([
    (ROOT / "app/src/main/java/za/org/rtc/community/MainActivity.kt").read_text(),
    (ROOT / "app/src/main/java/za/org/rtc/community/ui/navigation/RtcCommunityApp.kt").read_text(),
    (ROOT / "app/src/main/java/za/org/rtc/community/ui/navigation/RtcCommunityNavGraph.kt").read_text(),
    (ROOT / "app/src/main/java/za/org/rtc/community/ui/navigation/RtcNavigationChrome.kt").read_text(),
])
COMPONENTS = (
    ROOT
    / "app/src/main/java/za/org/rtc/community/ui/components/Concept6Components.kt"
).read_text()


def test_mathematical_foundation_is_centralized():
    math = (THEME / "RtcMath.kt").read_text()
    tokens = (THEME / "RtcDesignTokens.kt").read_text()
    for symbol in [
        "Phi",
        "GoldenMajor",
        "GoldenMinor",
        "Pi",
        "goldenLandscapeHeight",
        "circleCircumference",
    ]:
        assert symbol in math
    for value in [
        "3.dp",
        "5.dp",
        "8.dp",
        "13.dp",
        "21.dp",
        "34.dp",
        "55.dp",
        "89.dp",
    ]:
        assert value in tokens
    assert "minimumTouchTarget = 48.dp" in tokens


def test_density_and_responsive_contracts_exist():
    tokens = (THEME / "RtcDesignTokens.kt").read_text()
    math = (THEME / "RtcMath.kt").read_text()
    for density in [
        "RESIDENT_COMFORTABLE",
        "FEED_CONTENT",
        "ADMIN_COMPACT",
        "ANALYTICAL_DENSE",
    ]:
        assert density in tokens
    for width in ["COMPACT", "MEDIUM", "EXPANDED"]:
        assert width in math
    assert "600" in math and "840" in math


def test_shared_geometry_uses_tokens_and_true_circles():
    assert "RoundedCornerShape(999.dp)" not in COMPONENTS
    assert "CircleShape" in COMPONENTS
    assert "RtcSpacing." in COMPONENTS
    assert "RtcRadius." in COMPONENTS
    assert "RtcSize.minimumTouchTarget" in COMPONENTS


def test_screen_measurements_are_semantic_and_media_is_golden():
    assert re.search(r"\b\d+(?:\.\d+)?\.dp\b", MAIN) is None
    media = (ROOT / "app/src/main/java/za/org/rtc/community/feature/community/CommunityMedia.kt").read_text()
    preview = re.search(
        r"internal fun CommunityMediaPreview\(.*?(?=\n@Composable|\Z)",
        media,
        re.S,
    ).group(0)
    assert "aspectRatio(RtcMath.Phi)" in preview
    assert ".height(184.dp)" not in preview
    assert "RtcWindowWidth" in MAIN


def test_motion_vocabulary_and_documentation_exist():
    tokens = (THEME / "RtcDesignTokens.kt").read_text()
    for duration in ["89", "144", "233", "377", "610"]:
        assert duration in tokens
    assert (ROOT / "docs/MATHEMATICAL_DESIGN_SYSTEM.md").exists()
    assert (ROOT / "docs/MATHEMATICAL_HARMONY_AUDIT.md").exists()
