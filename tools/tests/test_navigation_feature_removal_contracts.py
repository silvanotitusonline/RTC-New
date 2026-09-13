from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
NAV = (ROOT / "app/src/main/java/za/org/rtc/community/navigation/RtcNavigation.kt").read_text()
ACCOUNT = (ROOT / "app/src/main/java/za/org/rtc/community/feature/account/AccountScreen.kt").read_text()
BINDINGS = (ROOT / "app/src/main/java/za/org/rtc/community/ui/navigation/ResidentModernisationBindings.kt").read_text()
GRAPH = (ROOT / "app/src/main/java/za/org/rtc/community/ui/navigation/RtcCommunityNavGraph.kt").read_text()
APP_MODULE = (ROOT / "app/src/main/java/za/org/rtc/community/di/AppModule.kt").read_text()
MIGRATION = (ROOT / "supabase/migrations/20260914000008_remove_service_centre_domain.sql").read_text()
ACCOUNT_BOOKINGS = ROOT / "app/src/main/java/za/org/rtc/community/feature/account/AccountServiceBookingsTracker.kt"
SERVICE_CENTRE_MAIN = ROOT / "app/src/main/java/za/org/rtc/community/feature/servicecentre"
SERVICE_CENTRE_TEST = ROOT / "app/src/test/java/za/org/rtc/community/feature/servicecentre"


def test_account_settings_and_messages_have_dedicated_destinations():
    assert 'const val ACCOUNT_SETTINGS = "account/settings"' in NAV
    assert 'const val ACCOUNT_MESSAGES = "inbox?tab=messages"' in NAV
    assert 'onMarketplace(RtcRoute.ACCOUNT_SETTINGS)' in ACCOUNT
    assert 'onMarketplace(RtcRoute.ACCOUNT_MESSAGES)' in ACCOUNT
    assert 'composable(RtcRoute.ACCOUNT_SETTINGS)' in BINDINGS
    assert 'ResidentInboxScreen(' in BINDINGS


def test_settings_no_longer_routes_to_help_and_provider_entry_is_removed():
    settings_block = ACCOUNT.split('title = "Settings"', 1)[1].split(')', 1)[0]
    assert 'onHelp' not in settings_block
    assert 'title = "Provider profile"' not in ACCOUNT
    assert 'Become a provider' not in ACCOUNT
    assert 'Provider status and account identity' not in ACCOUNT


def test_create_business_keeps_its_dedicated_marketplace_route():
    menu = (ROOT / "app/src/main/java/za/org/rtc/community/feature/account/AccountSettingsMenu.kt").read_text()
    assert 'title = "Create New Business Profile"' in menu
    assert 'RtcRoute.MARKETPLACE_BUSINESS_NEW' in menu


def test_booking_and_provider_routes_are_not_exposed_by_app_navigation():
    forbidden = [
        'SERVICE_CENTRE_REQUEST',
        'SERVICE_CENTRE_REQUEST_PATTERN',
        'SERVICE_CENTRE_PROVIDER',
        'SERVICE_CENTRE_BOOKINGS',
        'SERVICE_CENTRE_BOOKING',
        'SERVICE_CENTRE_CHAT',
        'MARKETPLACE_PROVIDER_ONBOARDING',
        'MARKETPLACE_PROVIDER_PROFILE',
        'MARKETPLACE_BOOKINGS',
        'serviceCentreRequest(',
        'serviceCentreBooking(',
        'serviceCentreChat(',
    ]
    for token in forbidden:
        assert token not in NAV, token
        assert token not in GRAPH, token


def test_service_centre_runtime_and_tests_are_permanently_removed():
    assert not SERVICE_CENTRE_MAIN.exists()
    assert not SERVICE_CENTRE_TEST.exists()
    assert not ACCOUNT_BOOKINGS.exists()
    assert 'ServiceCentre' not in APP_MODULE
    assert 'servicecentre' not in APP_MODULE.lower()


def test_forward_migration_removes_service_centre_database_surface():
    required = [
        "p.proname like 'service_centre\\_%'",
        'drop table if exists public.service_centre_booking_messages cascade;',
        'drop table if exists public.service_centre_booking_events cascade;',
        'drop table if exists public.service_centre_booking_payments cascade;',
        'drop table if exists public.service_centre_bookings cascade;',
        'drop table if exists public.service_centre_provider_profiles cascade;',
    ]
    for token in required:
        assert token in MIGRATION, token
