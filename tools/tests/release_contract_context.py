from pathlib import Path
import re
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
MAIN_ACTIVITY = (ROOT / 'app/src/main/java/za/org/rtc/community/MainActivity.kt').read_text()
APP_ROOT = (ROOT / 'app/src/main/java/za/org/rtc/community/ui/navigation/RtcCommunityApp.kt').read_text()
NAV_GRAPH = (ROOT / 'app/src/main/java/za/org/rtc/community/ui/navigation/RtcCommunityNavGraph.kt').read_text()
NAV_CHROME = (ROOT / 'app/src/main/java/za/org/rtc/community/ui/navigation/RtcNavigationChrome.kt').read_text()
MAIN = "\n".join([MAIN_ACTIVITY, APP_ROOT, NAV_GRAPH, NAV_CHROME])
VM = (ROOT / 'app/src/main/java/za/org/rtc/community/app/RtcViewModel.kt').read_text()
REPO = (ROOT / 'app/src/main/java/za/org/rtc/community/data/RtcRepository.kt').read_text()
PROD = (ROOT / 'app/src/main/java/za/org/rtc/community/supabase/ProductionUxRepository.kt').read_text()
ROUTES = (ROOT / 'app/src/main/java/za/org/rtc/community/navigation/RouteAccessPolicy.kt').read_text()
NAV = (ROOT / 'app/src/main/java/za/org/rtc/community/navigation/RtcNavigation.kt').read_text()
COMPONENTS = (ROOT / 'app/src/main/java/za/org/rtc/community/ui/components/Concept6Components.kt').read_text()
HOME_COMPONENTS = (ROOT / 'app/src/main/java/za/org/rtc/community/feature/home/HomeComponents.kt').read_text()
HOME_SCREEN = (ROOT / 'app/src/main/java/za/org/rtc/community/feature/home/HomeScreen.kt').read_text()
COMMUNITY_FEED = (ROOT / 'app/src/main/java/za/org/rtc/community/feature/community/CommunityFeedScreen.kt').read_text()
COMMUNITY_CARD = (ROOT / 'app/src/main/java/za/org/rtc/community/feature/community/CommunityPostCard.kt').read_text()
COMMUNITY_DETAIL = (ROOT / 'app/src/main/java/za/org/rtc/community/feature/community/CommunityPostDetailScreen.kt').read_text()
COMMUNITY_MEDIA = (ROOT / 'app/src/main/java/za/org/rtc/community/feature/community/CommunityMedia.kt').read_text()
COMMUNITY_COMPOSER = (ROOT / 'app/src/main/java/za/org/rtc/community/feature/community/PostComposer.kt').read_text()
EXPLORE_SCREEN = (ROOT / 'app/src/main/java/za/org/rtc/community/feature/explore/ExploreScreen.kt').read_text()
SUPPORT_SCREENS = (ROOT / 'app/src/main/java/za/org/rtc/community/feature/support/SupportScreen.kt').read_text()
ACCOUNT_SCREEN = (ROOT / 'app/src/main/java/za/org/rtc/community/feature/account/AccountScreen.kt').read_text()
ACCOUNT_DIALOGS = (ROOT / 'app/src/main/java/za/org/rtc/community/feature/account/AccountDialogs.kt').read_text()
ACCOUNT_NOTIFICATIONS = (ROOT / 'app/src/main/java/za/org/rtc/community/feature/account/AccountProfileNotifications.kt').read_text()
PUBLIC_SEARCH = (ROOT / 'app/src/main/java/za/org/rtc/community/feature/explore/SearchScreen.kt').read_text()
ADMIN_WORKSPACE = (ROOT / 'app/src/main/java/za/org/rtc/community/feature/administration/AdminWorkspace.kt').read_text()
ADMIN_COMPONENTS = (ROOT / 'app/src/main/java/za/org/rtc/community/feature/administration/AdminWorkspaceComponents.kt').read_text()
ADMIN_MFA = (ROOT / 'app/src/main/java/za/org/rtc/community/feature/administration/AdminWorkspaceComponents.kt').read_text()
ADMIN_MFA_SCREEN = (ROOT / 'app/src/main/java/za/org/rtc/community/feature/administration/AdministrationAccess.kt').read_text()
ADMIN_ACCESS = (ROOT / 'app/src/main/java/za/org/rtc/community/feature/administration/AdministrationAccess.kt').read_text()
ADMIN_PRIVACY = (ROOT / 'app/src/main/java/za/org/rtc/community/feature/administration/AdministrationAccess.kt').read_text()
ADMIN_CONTENT = (ROOT / 'app/src/main/java/za/org/rtc/community/feature/administration/AdministrationContentModeration.kt').read_text()
ADMIN_MODERATION = (ROOT / 'app/src/main/java/za/org/rtc/community/feature/administration/AdministrationContentModeration.kt').read_text()
ADMIN_OPERATIONAL = (ROOT / 'app/src/main/java/za/org/rtc/community/feature/administration/AdministrationOperations.kt').read_text()
ADMIN_MY_WORK = (ROOT / 'app/src/main/java/za/org/rtc/community/feature/administration/AdministrationWorkAi.kt').read_text()
ADMIN_AI = (ROOT / 'app/src/main/java/za/org/rtc/community/feature/administration/AdministrationWorkAi.kt').read_text()
ACCOUNT_WELCOME = (ROOT / 'app/src/main/java/za/org/rtc/community/feature/account/PublicWelcomeScreen.kt').read_text()
BRAND_LOCKUP = (ROOT / 'app/src/main/java/za/org/rtc/community/ui/components/RtcBrandLockup.kt').read_text()
STAFF_ALERTS = (ROOT / 'app/src/main/java/za/org/rtc/community/feature/alerts/CommunityAlertsScreens.kt').read_text()
NAV_METADATA = (ROOT / 'app/src/main/java/za/org/rtc/community/ui/navigation/RtcNavigationMetadata.kt').read_text()
NAV_CHROME = (ROOT / 'app/src/main/java/za/org/rtc/community/ui/navigation/RtcNavigationChrome.kt').read_text()
GRADLE = (ROOT / 'app/build.gradle.kts').read_text()
VERSIONS = (ROOT / 'gradle/libs.versions.toml').read_text()


def _function_body(source: str, function_name: str, next_names: list[str]) -> str:
    marker = f"fun {function_name}"
    start = source.find(marker)
    assert start >= 0, f"{function_name} is missing"
    candidates = [source.find(f"fun {name}", start + len(marker)) for name in next_names]
    candidates = [c for c in candidates if c >= 0]
    end = min(candidates) if candidates else len(source)
    return source[start:end]
