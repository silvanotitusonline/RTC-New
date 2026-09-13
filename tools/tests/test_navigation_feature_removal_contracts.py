from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
NAV = (ROOT / "app/src/main/java/za/org/rtc/community/navigation/RtcNavigation.kt").read_text()
ACCOUNT = (ROOT / "app/src/main/java/za/org/rtc/community/feature/account/AccountScreen.kt").read_text()
BINDINGS = (ROOT / "app/src/main/java/za/org/rtc/community/ui/navigation/ResidentModernisationBindings.kt").read_text()
GRAPH = (ROOT / "app/src/main/java/za/org/rtc/community/ui/navigation/RtcCommunityNavGraph.kt").read_text()
ACCOUNT_BOOKINGS = ROOT / "app/src/main/java/za/org/rtc/community/feature/account/AccountServiceBookingsTracker.kt"


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
        'serviceCentreRequest(',
        'serviceCentreBooking(',
        'serviceCentreChat(',
    ]
    for token in forbidden:
        assert token not in NAV, token
        assert token not in GRAPH, token


def test_hardcoded_account_booking_tracker_is_removed():
    assert not ACCOUNT_BOOKINGS.exists()
