# RTC Community Service Centre MVP Implementation Plan

> **Execution status:** COMPLETE. Final implementation/deployment evidence is recorded in `docs/SERVICE_CENTRE_MVP_HANDOFF.md`. The historical task design below is retained to preserve the implementation sequence and TDD record.

**Goal:** Build the smallest secure Service Centre that turns local provider discovery into a booking, lightweight chat, and webhook-confirmed commitment payment without changing Marketplace ownership or unrelated RTC features.

**Architecture:** Add a feature-sliced `feature/servicecentre` Android domain that reuses existing RTC auth, Marketplace categories/location concepts, Hilt, Navigation Compose, Supabase RPCs, FCM, and Custom Tabs. Persist provider profiles, bookings, messages, payments, and booking events behind forced-RLS/RPC-only tables. Keep payment and FCM trust decisions server-side through constrained Edge Functions.

**Tech Stack:** Kotlin 2.x, Jetpack Compose/Material 3, Hilt, Supabase Kotlin Auth/PostgREST/Functions, PostgreSQL/PostGIS/RLS, Supabase Edge Functions (Deno/TypeScript), Firebase Cloud Messaging, Yoco hosted Checkout, Android Custom Tabs.

**Spec:** User-approved AI CODING MISSION in conversation, implemented on `feature/service-centre-mvp` only.

## Global Constraints

- Base from `main` SHA `3d2c64c39cffb8384313cb7d33d425b9d3018af7`; do not merge or push `main`.
- Do not merge historical Marketplace donor branches.
- Do not touch `feature/community/**`, Brand customizer implementation, global design-token architecture, authentication architecture, or unrelated admin code.
- Do not add Supabase Realtime, offline mutation queues, escrow, provider payouts, AI matching/scheduling, dynamic pricing, contracts, or media-heavy chat.
- Android never receives service-role, Yoco secret, FCM service-account, or arbitrary notification-recipient authority.
- Production Supabase is not modified. Non-Production project is `eqwstpdjoineycrkhpht`.
- Latest observed Non-Production migration before Service Centre is `20260828134946_repair_ui_configuration_storage_policy_boundary`; use a later unique canonical migration.
- All booking/payment state changes are backend-authoritative and idempotent.

---

### Task 1: RED domain contracts

**Files:**
- Create: `app/src/test/java/za/org/rtc/community/feature/servicecentre/domain/ServiceCentreValidationTest.kt`
- Create: `app/src/test/java/za/org/rtc/community/feature/servicecentre/domain/ServiceCentreBookingStateTest.kt`
- Create: `tools/tests/test_service_centre_source_contracts.py`

**Interfaces:**
- Produces behavior contracts for provider/booking/message validation, visible booking-tab mapping, allowed transitions, routes, RPC names, table grants, Yoco webhook authority, and constrained FCM notification input.

- [x] Write tests that reference the not-yet-existing Service Centre domain and source files.
- [x] Verify the branch CI/source contracts are RED because the implementation is absent.
- [x] Commit tests only.

### Task 2: PostgreSQL Service Centre domain

**Files:**
- Create: `supabase/migrations/20260828144225_service_centre_mvp.sql`

**Interfaces:**
- Produces tables: `service_centre_provider_profiles`, `service_centre_bookings`, `service_centre_booking_messages`, `service_centre_booking_payments`, `service_centre_booking_events`.
- Produces RPCs: `service_centre_categories`, `service_centre_local_radar`, `service_centre_my_provider_profile`, `service_centre_upsert_provider_profile`, `service_centre_set_provider_active`, `service_centre_create_booking`, `service_centre_accept_booking`, `service_centre_decline_booking`, `service_centre_cancel_booking`, `service_centre_complete_booking`, `service_centre_my_bookings`, `service_centre_booking_detail`, `service_centre_booking_messages`, `service_centre_send_message`, `service_centre_prepare_commitment_payment`, `service_centre_confirm_commitment_payment`, `service_centre_notification_context`.

- [x] Create forced-RLS/RPC-only tables, constraints, indexes, helper projection functions, and append-only booking events.
- [x] Derive actors from `auth.uid()`, lock state-transition rows, reject self-booking/stale transitions, and snapshot a flat R100 commitment fee on acceptance.
- [x] Restrict table grants and function EXECUTE grants explicitly.
- [x] Apply the migration only to RTC Community Non-Production and verify schema/grants/state behavior with rollback-only/read-only SQL.
- [x] Commit migration source.

### Task 3: Android domain and repository

**Files:**
- Create: `app/src/main/java/za/org/rtc/community/feature/servicecentre/domain/ServiceCentreModels.kt`
- Create: `app/src/main/java/za/org/rtc/community/feature/servicecentre/domain/ServiceCentreBookingState.kt`
- Create: `app/src/main/java/za/org/rtc/community/feature/servicecentre/domain/ServiceCentreRepositories.kt`
- Create: `app/src/main/java/za/org/rtc/community/feature/servicecentre/domain/ServiceCentreValidation.kt`
- Create: `app/src/main/java/za/org/rtc/community/feature/servicecentre/data/remote/ServiceCentreJsonMappers.kt`
- Create: `app/src/main/java/za/org/rtc/community/feature/servicecentre/data/remote/SupabaseServiceCentreRepository.kt`
- Modify: `app/src/main/java/za/org/rtc/community/di/AppModule.kt`

**Interfaces:**
- `ServiceCentreDiscoveryRepository`: categories, radar.
- `ServiceCentreProviderRepository`: current profile, upsert, active toggle.
- `ServiceCentreBookingRepository`: create/list/detail/transition/messages/payment checkout/refresh.

- [x] Implement enough domain behavior to make Task 1 Kotlin tests GREEN.
- [x] Implement Supabase RPC/Functions adapter using existing Kotlin Supabase patterns.
- [x] Bind repository interfaces through Hilt.
- [x] Run targeted and CI JVM tests.
- [x] Commit Android data/domain layer.

### Task 4: Provider activation and Local Radar

**Files:**
- Create: `app/src/main/java/za/org/rtc/community/feature/servicecentre/presentation/ServiceCentreComponents.kt`
- Create: `app/src/main/java/za/org/rtc/community/feature/servicecentre/presentation/ServiceCentreHomeScreen.kt`
- Create: `app/src/main/java/za/org/rtc/community/feature/servicecentre/presentation/ServiceCentreProviderProfileScreen.kt`
- Create: `app/src/main/java/za/org/rtc/community/feature/servicecentre/presentation/viewmodel/ServiceCentreDiscoveryViewModel.kt`
- Create: `app/src/main/java/za/org/rtc/community/feature/servicecentre/presentation/viewmodel/ServiceCentreProviderViewModel.kt`

**Interfaces:**
- Provider onboarding asks only category, area/current location, and starting ZAR price; radius defaults to 25 km.
- Radar uses adaptive Compose grid behavior, category chips, locality/current-location fallback, and no raw provider coordinates.

- [x] Implement ViewModels against repository interfaces and existing Marketplace location concepts.
- [x] Implement accessible Material 3 screens using existing RTC spacing/size tokens.
- [x] Preserve form state on errors and reuse idempotency keys for explicit retry where transactional.
- [x] Run compile/tests.
- [x] Commit provider/discovery UI.

### Task 5: Booking Hub and chat

**Files:**
- Create: `app/src/main/java/za/org/rtc/community/feature/servicecentre/presentation/ServiceCentreRequestBookingScreen.kt`
- Create: `app/src/main/java/za/org/rtc/community/feature/servicecentre/presentation/ServiceCentreBookingHubScreen.kt`
- Create: `app/src/main/java/za/org/rtc/community/feature/servicecentre/presentation/ServiceCentreBookingDetailScreen.kt`
- Create: `app/src/main/java/za/org/rtc/community/feature/servicecentre/presentation/ServiceCentreChatScreen.kt`
- Create: `app/src/main/java/za/org/rtc/community/feature/servicecentre/presentation/viewmodel/ServiceCentreBookingViewModel.kt`

**Interfaces:**
- Request form has exactly date/time, service location text, and offer amount.
- Hub maps `PENDING_PROVIDER` to Pending; `ACCEPTED_AWAITING_PAYMENT`/`CONFIRMED` to Accepted; terminal states to Completed/Declined/Cancelled history.
- Chat is text-only, bounded to 1000 characters, fetched initially and refreshed every 5 seconds only while visible.

- [x] Implement request form/state, lifecycle actions, hub filters, detail, and text-only chat.
- [x] Never mutate booking status directly from Android tables.
- [x] Run tests/compile.
- [x] Commit booking/chat UI.

### Task 6: Yoco payment and Service Centre FCM boundary

**Files:**
- Create: `supabase/functions/service-centre-payment-create/index.ts`
- Create: `supabase/functions/service-centre-payment-webhook/index.ts`
- Create: `supabase/functions/service-centre-notify/index.ts`
- Reuse source: `supabase/functions/_shared/auth.ts`
- Create: `app/src/main/java/za/org/rtc/community/feature/servicecentre/presentation/ServiceCentrePaymentScreen.kt`

**Interfaces:**
- Checkout-create accepts only `bookingId` and idempotency key, authenticates the customer, derives fee amount server-side, and uses Yoco idempotency.
- Webhook verifies raw-body HMAC/timestamp against `YOCO_WEBHOOK_SECRET`, confirms only matching ZAR commitment payments through backend-only RPC, and does not trust browser return.
- Notify accepts only booking/event/message context; recipient/title/body are derived server-side.

- [x] Add Edge Function source tests/contracts before function source.
- [x] Implement functions with shared caller verification, bounded inputs, rate limiting where user-triggered, and no arbitrary recipient/body authority.
- [x] Deploy only to Non-Production; payment-create remains fail-closed until `YOCO_SECRET_KEY` is configured if the secret is absent.
- [x] Implement Custom Tab payment launch and refresh-on-return UI.
- [x] Commit payment/notification source.

### Task 7: Navigation, deep links, Marketplace entry points

**Files:**
- Modify: `app/src/main/java/za/org/rtc/community/navigation/RtcNavigation.kt`
- Modify: `app/src/main/java/za/org/rtc/community/ui/navigation/RtcCommunityNavGraph.kt`
- Modify: `app/src/main/java/za/org/rtc/community/feature/marketplace/presentation/MarketplaceHomeScreen.kt`
- Modify: `app/src/main/java/za/org/rtc/community/feature/marketplace/presentation/MarketplaceBusinessScreen.kt`
- Modify: `app/src/main/java/za/org/rtc/community/notifications/RtcNotificationChannels.kt`
- Modify: `app/src/main/java/za/org/rtc/community/app/RtcFirebaseMessagingService.kt`
- Modify: `app/src/main/java/za/org/rtc/community/MainActivity.kt`

**Interfaces:**
- Routes: `community/service-centre`, `community/service-centre/request/{providerId}`, `account/service-centre/provider`, `account/service-centre/bookings`, `account/service-centre/booking/{bookingId}`, `account/service-centre/chat/{bookingId}`, `account/service-centre/payment/{bookingId}`.
- Deep link: `rtc://service-centre/booking/{bookingId}`.
- Notification channel: `rtc_service_bookings`.
- Marketplace integration is callback/navigation-only; Marketplace does not own Service Centre ViewModels.

- [x] Add Service Centre route family to existing NavHost only.
- [x] Add Marketplace Home entry card and offering-level Request Booking callback carrying business/offering context.
- [x] Extend FCM receiver with explicit Service Centre routing without changing Community alert behavior.
- [x] Run route/source contracts and Android compile.
- [x] Commit integration seams.

### Task 8: Final verification and handoff

**Files:**
- Create: `docs/SERVICE_CENTRE_MVP_HANDOFF.md`

- [x] Re-fetch current `main`; compare for parallel Brand/Community changes and report/reconcile drift without overwriting unrelated work.
- [x] Run/observe source regression contracts, JVM tests, Android lint, `assembleDebug`, and AndroidTest/Compose smoke compilation through repository CI.
- [x] Verify Non-Production tables/functions/grants and all three deployed Service Centre Edge Functions.
- [x] Verify no Production Supabase deployment/merge action was performed.
- [x] Record exact implementation SHA, changed files/scope, migration, RPCs, functions, verification evidence, Yoco-secret prerequisite, known MVP limits, and merge-risk notes in the final handoff.
- [x] Do not merge the branch into `main`.
