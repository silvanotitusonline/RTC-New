from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
NAV_PATH = ROOT / "app/src/main/java/za/org/rtc/community/navigation/RtcNavigation.kt"
ACCOUNT_PATH = ROOT / "app/src/main/java/za/org/rtc/community/feature/account/AccountScreen.kt"
BINDINGS_PATH = ROOT / "app/src/main/java/za/org/rtc/community/ui/navigation/ResidentModernisationBindings.kt"
GRAPH_PATH = ROOT / "app/src/main/java/za/org/rtc/community/ui/navigation/RtcCommunityNavGraph.kt"
APP_PATH = ROOT / "app/src/main/java/za/org/rtc/community/ui/navigation/RtcCommunityApp.kt"
APP_MODULE_PATH = ROOT / "app/src/main/java/za/org/rtc/community/di/AppModule.kt"
MIGRATION_PATH = ROOT / "supabase/migrations/20260914000008_remove_service_centre_domain.sql"
ACCOUNT_BOOKINGS = ROOT / "app/src/main/java/za/org/rtc/community/feature/account/AccountServiceBookingsTracker.kt"
SERVICE_CENTRE_MAIN = ROOT / "app/src/main/java/za/org/rtc/community/feature/servicecentre"
SERVICE_CENTRE_TEST = ROOT / "app/src/test/java/za/org/rtc/community/feature/servicecentre"


def read(path: Path) -> str:
    assert path.exists(), f"expected source file: {path.relative_to(ROOT)}"
    return path.read_text(encoding="utf-8")


def test_account_settings_and_messages_have_dedicated_destinations():
    nav = read(NAV_PATH)
    account = read(ACCOUNT_PATH)
    bindings = read(BINDINGS_PATH)
    assert 'const val ACCOUNT_SETTINGS = "account/settings"' in nav
    assert 'const val ACCOUNT_MESSAGES = "inbox?tab=messages"' in nav
    assert 'onMarketplace(RtcRoute.ACCOUNT_SETTINGS)' in account
    assert 'onMarketplace(RtcRoute.ACCOUNT_MESSAGES)' in account
    assert 'composable(RtcRoute.ACCOUNT_SETTINGS)' in bindings
    assert 'ResidentInboxScreen(' in bindings


def test_settings_no_longer_routes_to_help_and_provider_entry_is_removed():
    account = read(ACCOUNT_PATH)
    settings_block = account.split('title = "Settings"', 1)[1].split(')', 1)[0]
    assert 'onHelp' not in settings_block
    assert 'title = "Provider profile"' not in account
    assert 'Become a provider' not in account
    assert 'Provider status and account identity' not in account


def test_service_centre_routes_and_deep_links_are_not_exposed_by_android():
    nav = read(NAV_PATH)
    graph = read(GRAPH_PATH)
    app = read(APP_PATH)
    forbidden = [
        'SERVICE_CENTRE_HOME',
        'SERVICE_CENTRE_REQUEST',
        'SERVICE_CENTRE_REQUEST_PATTERN',
        'SERVICE_CENTRE_PROVIDER',
        'SERVICE_CENTRE_BOOKINGS',
        'SERVICE_CENTRE_BOOKING',
        'SERVICE_CENTRE_CHAT',
        'serviceCentreRequest(',
        'serviceCentreBooking(',
        'serviceCentreChat(',
        'ServiceCentreDeepLink',
        'ACTION_OPEN_SERVICE_BOOKING',
    ]
    for token in forbidden:
        assert token not in nav, token
        assert token not in graph, token
        assert token not in app, token


def test_service_centre_runtime_tests_and_di_bindings_are_permanently_removed():
    app_module = read(APP_MODULE_PATH)
    assert not SERVICE_CENTRE_MAIN.exists()
    assert not SERVICE_CENTRE_TEST.exists()
    assert not ACCOUNT_BOOKINGS.exists()
    assert 'ServiceCentre' not in app_module
    assert 'servicecentre' not in app_module.lower()


def test_forward_migration_removes_service_centre_database_surface():
    assert MIGRATION_PATH.exists(), "service-centre retirement requires a forward-only migration"
    migration = read(MIGRATION_PATH).lower()
    required = [
        "p.proname like 'service_centre\\_%'",
        'drop table if exists public.service_centre_booking_messages cascade;',
        'drop table if exists public.service_centre_booking_events cascade;',
        'drop table if exists public.service_centre_booking_payments cascade;',
        'drop table if exists public.service_centre_bookings cascade;',
        'drop table if exists public.service_centre_provider_profiles cascade;',
    ]
    for token in required:
        assert token in migration, token


def test_marketplace_business_management_remains_after_service_centre_retirement():
    account = read(ACCOUNT_PATH)
    nav = read(NAV_PATH)
    assert 'Marketplace & Business Hub' in account
    assert 'MARKETPLACE_BUSINESS_NEW' in nav
    assert 'MARKETPLACE_MY_BUSINESSES' in nav
