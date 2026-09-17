from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
FEATURE = ROOT / "app/src/main/java/za/org/rtc/community/feature/servicecentre"
MIGRATION = ROOT / "supabase/migrations/20260828144225_service_centre_mvp.sql"
PAYMENT_REMOVAL = ROOT / "supabase/migrations/20260907035000_remove_service_centre_payments.sql"
PAYMENT_CREATE = ROOT / "supabase/functions/service-centre-payment-create/index.ts"
PAYMENT_WEBHOOK = ROOT / "supabase/functions/service-centre-payment-webhook/index.ts"
NOTIFY = ROOT / "supabase/functions/service-centre-notify/index.ts"
CONFIG = ROOT / "supabase/config.toml"
NAVIGATION = ROOT / "app/src/main/java/za/org/rtc/community/navigation/RtcNavigation.kt"
NAV_GRAPH = ROOT / "app/src/main/java/za/org/rtc/community/ui/navigation/RtcCommunityNavGraph.kt"
FCM = ROOT / "app/src/main/java/za/org/rtc/community/app/RtcFirebaseMessagingService.kt"
CHANNELS = ROOT / "app/src/main/java/za/org/rtc/community/notifications/RtcNotificationChannels.kt"
MAIN_ACTIVITY = ROOT / "app/src/main/java/za/org/rtc/community/MainActivity.kt"
MARKETPLACE_HOME = ROOT / "app/src/main/java/za/org/rtc/community/feature/marketplace/presentation/MarketplaceHomeScreen.kt"
MARKETPLACE_BUSINESS = ROOT / "app/src/main/java/za/org/rtc/community/feature/marketplace/presentation/MarketplaceBusinessScreen.kt"
BUILD = ROOT / "app/build.gradle.kts"


def _read(path: Path) -> str:
    assert path.exists(), f"Missing required Service Centre implementation file: {path}"
    return path.read_text(encoding="utf-8")


def test_service_centre_feature_slice_is_bounded_and_present():
    required = [
        FEATURE / "domain/ServiceCentreModels.kt",
        FEATURE / "domain/ServiceCentreBookingState.kt",
        FEATURE / "domain/ServiceCentreRepositories.kt",
        FEATURE / "domain/ServiceCentreValidation.kt",
        FEATURE / "data/remote/ServiceCentreJsonMappers.kt",
        FEATURE / "data/remote/SupabaseServiceCentreRepository.kt",
        FEATURE / "presentation/ServiceCentreComponents.kt",
        FEATURE / "presentation/ServiceCentreHomeScreen.kt",
        FEATURE / "presentation/ServiceCentreProviderProfileScreen.kt",
        FEATURE / "presentation/ServiceCentreRequestBookingScreen.kt",
        FEATURE / "presentation/ServiceCentreBookingHubScreen.kt",
        FEATURE / "presentation/ServiceCentreBookingDetailScreen.kt",
        FEATURE / "presentation/ServiceCentreChatScreen.kt",
        FEATURE / "presentation/viewmodel/ServiceCentreDiscoveryViewModel.kt",
        FEATURE / "presentation/viewmodel/ServiceCentreProviderViewModel.kt",
        FEATURE / "presentation/viewmodel/ServiceCentreBookingViewModel.kt",
    ]
    for path in required:
        assert path.exists(), f"Missing bounded Service Centre file: {path}"
    assert not (FEATURE / "presentation/ServiceCentrePaymentScreen.kt").exists()


def test_service_centre_database_is_rpc_only_for_transactional_tables():
    sql = _read(MIGRATION).lower()
    for table in (
        "service_centre_provider_profiles",
        "service_centre_bookings",
        "service_centre_booking_messages",
        "service_centre_booking_events",
    ):
        assert f"alter table public.{table} enable row level security" in sql
        assert f"alter table public.{table} force row level security" in sql
        assert f"revoke all on table public.{table} from anon, authenticated" in sql
    assert "geography(point,4326)" in sql
    assert "service_radius_km" in sql
    assert "customer_user_id <> provider_user_id" in sql


def test_service_centre_rpc_surface_is_backend_authoritative_and_bounded():
    sql = _read(MIGRATION).lower()
    removal = _read(PAYMENT_REMOVAL).lower()
    required_rpcs = (
        "service_centre_categories",
        "service_centre_local_radar",
        "service_centre_my_provider_profile",
        "service_centre_upsert_provider_profile",
        "service_centre_set_provider_active",
        "service_centre_create_booking",
        "service_centre_accept_booking",
        "service_centre_decline_booking",
        "service_centre_cancel_booking",
        "service_centre_complete_booking",
        "service_centre_my_bookings",
        "service_centre_booking_detail",
        "service_centre_booking_messages",
        "service_centre_send_message",
        "service_centre_notification_context",
    )
    for name in required_rpcs:
        assert f"function public.{name}" in sql or f"function public.{name}" in removal
    assert "select auth.uid()" in sql
    assert "for update" in removal
    assert "set status = 'confirmed'" in removal
    assert "'pending_provider','confirmed','completed','declined','cancelled'" in removal.replace(" ", "")
    assert "100.00" not in removal
    assert "greatest(1, least(coalesce(p_limit, 20), 50))" in sql
    assert "least(coalesce(p_radius_metres, 25000), 50000)" in sql


def test_service_centre_public_radar_does_not_return_raw_provider_coordinates():
    sql = _read(MIGRATION)
    radar = sql[sql.index("service_centre_local_radar"):sql.index("service_centre_my_provider_profile")]
    returns_start = radar.lower().index("returns table")
    returns_end = radar.lower().index("language", returns_start)
    lowered = radar[returns_start:returns_end].lower()
    assert "distance_metres" in lowered
    assert "locality" in lowered
    assert "latitude" not in lowered
    assert "longitude" not in lowered
    assert "coordinates geography" not in lowered


def test_service_centre_payment_subsystem_is_removed():
    removal = _read(PAYMENT_REMOVAL).lower()
    config = _read(CONFIG).lower()
    notify = _read(NOTIFY)
    runtime_source = "\n".join(path.read_text(encoding="utf-8") for path in FEATURE.rglob("*.kt"))
    navigation = _read(NAVIGATION) + "\n" + _read(NAV_GRAPH)

    assert not PAYMENT_CREATE.exists()
    assert not PAYMENT_WEBHOOK.exists()
    assert "service-centre-payment-create" not in config
    assert "service-centre-payment-webhook" not in config
    assert "[functions.service-centre-notify]\nverify_jwt = true" in _read(CONFIG)

    assert "drop table if exists public.service_centre_booking_payments" in removal
    assert "drop function if exists public.service_centre_prepare_commitment_payment" in removal
    assert "drop function if exists public.service_centre_confirm_commitment_payment" in removal
    assert "drop function if exists public.service_centre_attach_commitment_checkout" in removal
    assert "drop column if exists commitment_fee_amount" in removal

    for forbidden in (
        "ACCEPTED_AWAITING_PAYMENT",
        "ServiceCentrePaymentCheckout",
        "ServiceCentrePaymentRoute",
        "createCommitmentCheckout",
        "SERVICE_BOOKING_CONFIRMED",
    ):
        assert forbidden not in runtime_source
        assert forbidden not in navigation

    assert "SERVICE_CENTRE_INTERNAL_NOTIFY_SECRET" not in notify
    assert "x-service-centre-internal-secret" not in notify
    assert "authenticateCaller" in notify


def test_service_centre_notification_function_cannot_target_arbitrary_users():
    source = _read(NOTIFY)
    assert "service_centre_notification_context" in source
    assert "bookingId" in source
    assert "eventType" in source
    assert "recipientId" not in source[source.find("readJsonObject"): source.find("service_centre_notification_context")]
    assert "SERVICE_BOOKING_NEW" in source
    assert "SERVICE_BOOKING_MESSAGE" in source
    assert "rtc://service-centre/booking/" not in source  # route is derived by the authoritative RPC
    assert "authenticateCaller" in source


def test_android_service_centre_navigation_notification_and_marketplace_entry_points_exist():
    routes = _read(NAVIGATION)
    graph = _read(NAV_GRAPH)
    fcm = _read(FCM)
    channels = _read(CHANNELS)
    activity = _read(MAIN_ACTIVITY)
    home = _read(MARKETPLACE_HOME)
    business = _read(MARKETPLACE_BUSINESS)
    for route in (
        "community/service-centre",
        "community/service-centre/request/{providerId}",
        "account/service-centre/provider",
        "account/service-centre/bookings",
        "account/service-centre/booking/{bookingId}",
        "account/service-centre/chat/{bookingId}",
    ):
        assert route in routes
    assert "account/service-centre/payment" not in routes
    assert "ServiceCentrePaymentRoute" not in graph
    assert "ServiceCentreHomeRoute" in graph
    assert "ServiceCentreRequestBookingRoute" in graph
    assert "RTC_SERVICE_BOOKINGS_CHANNEL" in channels
    assert "SERVICE_BOOKING" in fcm
    assert "ACTION_OPEN_SERVICE_BOOKING" in fcm
    assert "handleServiceCentreIntent" in activity
    # Marketplace entry is now a services hand-off rather than a hard-coded label.
    assert "onSwitchToServices" in home or "Open Service Centre" in home
    assert "Request Booking" in business or "MarketplaceRequestBookingLabel" in business


def test_mvp_does_not_add_realtime_or_offline_booking_queue_dependency():
    # App-level Realtime is allowed for community/notifications; Service Centre itself must stay RPC-poll based.
    service_source = "\n".join(path.read_text(encoding="utf-8") for path in FEATURE.rglob("*.kt")) if FEATURE.exists() else ""
    assert "WorkManager" not in service_source
    assert "Room" not in service_source
    assert "RealtimeChannel" not in service_source
    assert "supabase.realtime" not in service_source.lower()
