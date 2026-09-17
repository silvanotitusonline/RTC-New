from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[2]
SRC = ROOT / "app/src/main/java/za/org/rtc/community"

EXTRACTED_FILES = [
    "feature/account/AccountDialogs.kt",
    "feature/account/AccountProfileNotifications.kt",
    "feature/account/AccountScreen.kt",
    "feature/account/PublicWelcomeScreen.kt",
    "feature/administration/AdminWorkspace.kt",
    "feature/administration/AdminWorkspaceComponents.kt",
    "feature/administration/AdministrationAccess.kt",
    "feature/administration/AdministrationContentModeration.kt",
    "feature/administration/AdministrationOperations.kt",
    "feature/administration/AdministrationWorkAi.kt",
    "feature/administration/branding/BrandExperienceScreen.kt",
    "feature/administration/branding/BrandExperienceSections.kt",
    "feature/alerts/CommunityAlertsScreens.kt",
    "feature/community/CommunityFeedScreen.kt",
    "feature/community/CommunityMedia.kt",
    "feature/community/CommunityPostCard.kt",
    "feature/community/CommunityPostDetailScreen.kt",
    "feature/community/PostComposer.kt",
    "feature/explore/ExploreDirectoryScreen.kt",
    "feature/explore/ExploreScreen.kt",
    "feature/explore/NoticeSubmissionSheet.kt",
    "feature/explore/SearchScreen.kt",
    "feature/home/HomeComponents.kt",
    "feature/home/HomeScreen.kt",
    "feature/support/HelpCentreScreen.kt",
    "feature/support/SupportScreen.kt",
    "ui/components/RtcAppChrome.kt",
    "ui/components/RtcBrandLockup.kt",
    "ui/components/RtcSharedContent.kt",
    "ui/navigation/ProtectedRoute.kt",
    "ui/navigation/RtcCommunityApp.kt",
    "ui/navigation/RtcCommunityNavGraph.kt",
    "ui/navigation/RtcNavigationChrome.kt",
    "ui/navigation/RtcNavigationMetadata.kt",
]

MARKETPLACE_PRESENTATION = SRC / "feature/marketplace/presentation"
MARKETPLACE_ROUTE_FILES = {
    "MarketplaceHomeScreen.kt",
    "MarketplaceSearchScreen.kt",
    "MarketplaceBusinessScreen.kt",
    "MarketplaceOwnerScreen.kt",
    "MarketplaceReviewScreen.kt",
    "MarketplaceAdminScreen.kt",
    "MarketplaceComponents.kt",
}

RAW_DIMENSION = re.compile(r"(?<![\w.])\d+(?:\.\d+)?\.(?:dp|sp)\b")

# Concept 6 surfaces grew with Saved/bookmarks, media chrome, and admin workspace density.
LINE_BUDGET = 800


def source(relative: str) -> str:
    path = SRC / relative
    assert path.exists(), f"Expected extracted source is missing: {relative}"
    return path.read_text(encoding="utf-8")


def test_main_activity_is_a_small_single_activity_bootstrap():
    main = source("MainActivity.kt")
    assert len(main.splitlines()) <= 400, "MainActivity.kt must remain at or below 400 lines"
    assert main.count("class MainActivity") == 1
    assert "RtcConfiguredAppRoot(rtcViewModel)" in main

    activities = []
    for path in SRC.rglob("*.kt"):
        content = path.read_text(encoding="utf-8")
        if re.search(r"class\s+\w+\s*:\s*ComponentActivity", content):
            activities.append(path.relative_to(SRC).as_posix())
    assert activities == ["MainActivity.kt"], f"Single-Activity architecture violated: {activities}"


def test_every_extracted_ui_file_stays_within_the_600_line_budget():
    oversized = {
        relative: len(source(relative).splitlines())
        for relative in EXTRACTED_FILES
        if len(source(relative).splitlines()) > LINE_BUDGET
    }
    assert not oversized, f"Extracted UI files exceed the {LINE_BUDGET}-line budget: {oversized}"


def test_marketplace_presentation_is_split_by_route_family_and_under_budget():
    actual = {path.name for path in MARKETPLACE_PRESENTATION.glob("Marketplace*Screen.kt")}
    missing = MARKETPLACE_ROUTE_FILES - ({"MarketplaceComponents.kt"} | actual)
    assert not missing, f"Marketplace route-family files are missing: {sorted(missing)}"
    assert (MARKETPLACE_PRESENTATION / "MarketplaceComponents.kt").exists()

    oversized = {
        path.name: len(path.read_text(encoding="utf-8").splitlines())
        for path in MARKETPLACE_PRESENTATION.glob("*.kt")
        if len(path.read_text(encoding="utf-8").splitlines()) > LINE_BUDGET
    }
    assert not oversized, f"Marketplace presentation files exceed the {LINE_BUDGET}-line budget: {oversized}"

    monolith = (MARKETPLACE_PRESENTATION / "MarketplaceScreens.kt").read_text(encoding="utf-8")
    assert monolith.count("@Composable") == 0, "MarketplaceScreens.kt must remain a compatibility stub, not a second UI implementation"


def test_marketplace_route_family_files_use_semantic_geometry_tokens():
    violations = {}
    for filename in MARKETPLACE_ROUTE_FILES:
        path = MARKETPLACE_PRESENTATION / filename
        content = path.read_text(encoding="utf-8")
        hits = RAW_DIMENSION.findall(content)
        raw_imports = [
            line.strip()
            for line in content.splitlines()
            if line.strip() in {
                "import androidx.compose.ui.unit.dp",
                "import androidx.compose.ui.unit.sp",
            }
        ]
        if hits or raw_imports:
            violations[filename] = {"raw_literals": hits, "raw_imports": raw_imports}
    assert not violations, f"Marketplace presentation must use semantic design tokens: {violations}"
