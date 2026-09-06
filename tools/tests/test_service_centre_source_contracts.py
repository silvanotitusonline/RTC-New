from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
FEATURE = ROOT / "app/src/main/java/za/org/rtc/community/feature/servicecentre"
MIGRATION = ROOT / "supabase/migrations/20260828144225_service_centre_mvp.sql"
PAYMENT_CREATE = ROOT / "supabase/functions/service-centre-payment-create/index.ts"
PAYMENT_WEBHOOK = ROOT / "supabase/functions/service-centre-payment-webhook/index.ts"
NOTIFY = ROOT / "supabase/functions/service-centre-notify/index.ts"
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
        FEATURE / "presentation/ServiceCentrePaymentScreen.kt",
        FEATURE / "presentation/viewmodel/ServiceCentreDiscoveryViewModel.kt",
        FEATURE / "presentation/viewmodel/ServiceCentreProviderViewModel.kt",
        FEATURE / "presentation/viewmodel/ServiceCentreBookingViewModel.kt",
    ]
    for path in required:
        assert path.exists(), f"Missing bounded Service Centre file: {path}"


def test_service_centre_database_is_rpc_only_for_transactional_tables():
    sql = _read(MIGRATION).lower()
    for table in (
        "service_centre_provider_profiles",
        "service_centre_bookings",
        "service_centre_booking_messages",
        "service_centre_booking_payments",
        "service_centre_booking_events",
    ):
        assert f"alter table public.{table} enable row level security" in sql
        assert f"alter table public.{table} force row level security" in sql
        assert f"revoke all on table public.{table} from anon, authenticated" in sql
    assert "geography(point,4326)" in sql
    assert "service_radius_km" in sql
    assert "commitment_fee_amount" in sql
    assert "customer_user_id <> provider_user_id" in sql


def test_service_centre_rpc_surface_is_backend_authoritative_and_bounded():
    sql = _read(MIGRATION).lower()
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
        "service_centre_prepare_commitment_payment",
        "service_centre_confirm_commitment_payment",
        "service_centre_notification_context",
    )
    for name in required_rpcs:
        assert f"function public.{name}" in sql
    assert "select auth.uid()" in sql
    assert "for update" in sql
    assert "accepted_awaiting_payment" in sql
    assert "confirmed" in sql
    assert "completed" in sql
    assert "declined" in sql
    assert "cancelled" in sql
    assert "100.00" in sql
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


def test_yoco_checkout_is_server_derived_and_webhook_authoritative():
    create = _read(PAYMENT_CREATE)
    webhook = _read(PAYMENT_WEBHOOK)
    assert "service_centre_prepare_commitment_payment" in create
    assert "YOCO_SECRET_KEY" in create
    assert "Idempotency-Key" in create
    assert "amount" not in create[create.find("readJsonObject"): create.find("service_centre_prepare_commitment_payment")]
    assert "YOCO_WEBHOOK_SECRET" in webhook
    assert "crypto.subtle.verify" in webhook or "timingSafeEqual" in webhook or "constantTime" in webhook
    assert "service_centre_confirm_commitment_payment" in webhook
    assert "rawBody" in webhook
    assert "Authorization" not in webhook[: webhook.find("Deno.serve")]


def test_service_centre_notification_function_cannot_target_arbitrary_users():
    source = _read(NOTIFY)
    assert "service_centre_notification_context" in source
    assert "bookingId" in source
    assert "eventType" in source
    assert "recipientId" not in source[source.find("readJsonObject"): source.find("service_centre_notification_context")]
    assert "SERVICE_BOOKING_NEW" in source
    assert "SERVICE_BOOKING_MESSAGE" in source
    assert "rtc://service-centre/booking/" in source


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
        "account/service-centre/payment/{bookingId}",
    ):
        assert route in routes
    assert "ServiceCentreHomeRoute" in graph
    assert "ServiceCentreRequestBookingRoute" in graph
    assert "RTC_SERVICE_BOOKINGS_CHANNEL" in channels
    assert "SERVICE_BOOKING" in fcm
    assert "ACTION_OPEN_SERVICE_BOOKING" in fcm
    assert "handleServiceCentreIntent" in activity
    assert "Open Service Centre" in home
    assert "Request Booking" in business


def test_mvp_does_not_add_realtime_or_offline_booking_queue_dependency():
    build = _read(BUILD)
    assert "supabase.realtime" not in build
    service_source = "\n".join(path.read_text(encoding="utf-8") for path in FEATURE.rglob("*.kt")) if FEATURE.exists() else ""
    assert "WorkManager" not in service_source
    assert "Room" not in service_source
