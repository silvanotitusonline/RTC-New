# Service Centre MVP — Current-Main Integration Final Report

## Executive summary

The Service Centre MVP has been reconciled onto the current Brand + Community + Marketplace + Supabase Security application architecture on `integration/service-centre-production-v2`.

This was a clean-room semantic integration. The stale donor branch `feature/service-centre-mvp` was **not** merged wholesale.

### Integration baseline

- current-main base: `4833d5c37c410796749e073be6ce6dc4ede93be3`
- verified donor/reference head: `b67719ff98299dfba4929a9f465c46c14ceb7299`
- implementation head: `e100029c0d8d2de210e904fa531351e05c80aeb8`
- integration PR: #19

At integration start the donor was 33 commits ahead and 15 commits behind current main. The final implementation branch is 8 commits ahead and 0 behind its current-main base.

## Architecture outcome

### Preserved current-main ownership

- one `MainActivity` remains the Android bootstrap;
- Brand's `RtcConfiguredAppRoot` remains authoritative for published configuration and theme application;
- Community's scoped feature ownership remains intact;
- Marketplace's scoped Discovery/Owner/Review/Admin architecture remains intact;
- Supabase Security shared authorization and current Edge-function trust boundaries remain authoritative;
- exactly four resident primary destinations remain unchanged.

### Added Service Centre ownership

- isolated `feature/servicecentre` domain/data/presentation package;
- scoped Discovery, Provider and Booking ViewModels/repository contracts;
- provider sidecar activation/edit/pause/resume;
- locality/category/radius provider discovery;
- provider detail and Marketplace request-booking entry points;
- booking request, Booking Hub/detail lifecycle and participant chat;
- commitment-payment preparation and hosted-checkout handoff;
- booking notification channel and `rtc://service-centre/booking/{bookingId}` navigation.

## Shared-seam reconciliation

The donor's shared files were not accepted wholesale.

### `MainActivity.kt`

Current Brand bootstrap was preserved. Service Centre booking intent/deep-link state is passed through a composition-local `ServiceCentreDeepLinkScope` around `RtcConfiguredAppRoot`, so remote Brand configuration remains active.

### Dependency injection

Only the three scoped Service Centre repository bindings were added. Existing Community and Marketplace bindings remain unchanged.

### Navigation

Typed Service Centre routes were added to the existing route model and current navigation graph. Brand's MFA-protected `ADMIN_BRANDING` destination and all current Community/Marketplace/admin routes remain present.

### Marketplace

Marketplace ownership was not moved into Service Centre. Marketplace Home/business screens only receive the intended Service Centre entry/request-booking actions.

### Notifications

Service Centre booking notifications were added to the existing FCM/channel plumbing without replacing Community notification behavior.

### Supabase configuration

The three Service Centre Edge Function declarations were added while retaining the current shared Security authorization module and existing function configuration.

## Backend trust boundary

### Repository migration

- `20260828144225_service_centre_mvp.sql`

### Non-production migration parity

RTC Community Non-Production (`eqwstpdjoineycrkhpht`) already contains migration version `20260828144225 service_centre_mvp`.

### Non-production Edge Functions

Verified ACTIVE:

- `service-centre-payment-create` — platform JWT verification enabled;
- `service-centre-payment-webhook` — platform JWT verification disabled intentionally; Yoco HMAC is authoritative;
- `service-centre-notify` — platform JWT verification disabled intentionally; function source explicitly authenticates either the allowed user path or internal notification secret.

### RPC-only table boundary

Live PostgreSQL inspection confirmed these tables all have RLS enabled and forced:

- `service_centre_booking_events`
- `service_centre_booking_messages`
- `service_centre_booking_payments`
- `service_centre_bookings`
- `service_centre_provider_profiles`

For both `anon` and `authenticated`, direct table SELECT/INSERT/UPDATE/DELETE privileges are absent. Service Centre lifecycle access remains RPC-authoritative.

### RPC security

Live PostgreSQL inspection confirmed all public `service_centre_%` RPCs are `SECURITY DEFINER` with a fixed empty `search_path`. `anon` has no execution access. Normal user lifecycle RPCs are authenticated-only.

Payment-authoritative RPCs remain service-role-only:

- `service_centre_attach_commitment_checkout(uuid,text)`
- `service_centre_confirm_commitment_payment(text,text,integer,text)`

The Android client therefore cannot self-confirm a payment.

## Non-production runtime verification

All test mutations described below were enclosed in transactions and rolled back.

### Provider sidecar

An authenticated resident successfully:

1. activated/updated a provider sidecar through `service_centre_upsert_provider_profile`;
2. read the resulting provider profile through the authenticated RPC surface.

No direct-table access was used.

### Full booking lifecycle

A rollback-only scenario successfully executed:

1. provider sidecar activation;
2. customer locality/category radar discovery;
3. customer booking creation;
4. customer Booking Hub retrieval;
5. participant chat message creation;
6. provider booking acceptance;
7. participant booking-detail retrieval;
8. customer commitment-payment preparation.

The deployed payment-preparation RPC returned:

- amount: `100.00`
- amountCents: `10000`
- currencyCode: `ZAR`
- initial status: `CREATED`

This runtime result verifies the fixed R100 commitment-fee invariant before any external checkout is attached.

### Payment-authority rule

The browser return path is not treated as authoritative. Checkout creation is server-derived and the authoritative paid transition remains the HMAC-validated payment webhook → service-role-only confirmation RPC chain.

A live Yoco charge/webhook was **not** executed during this integration because external payment secrets were not enumerated or exposed for this verification.

## Exact-head CI verification

Android Production Verification run **#384** / run ID `33218246770` completed successfully for implementation head `e100029c0d8d2de210e904fa531351e05c80aeb8`.

Results:

- source regression contracts: **156/156 passed**;
- shared Edge authorization tests: **14/14 passed, 0 failed**;
- `testDebugUnitTest`: PASS;
- `lintDebug`: PASS;
- `assembleDebug`: PASS;
- `assembleDebugAndroidTest`: PASS;
- debug verification artifact upload: PASS;
- CI credential cleanup: PASS.

Debug verification artifact:

- name: `rtc-community-debug-verification`
- artifact ID: `9704290319`
- size: `40,168,078` bytes
- SHA-256: `d16643d2f833e7197f47de2ac81e416a45d079cfbb17c46b399d8b7d0995b4d5`

## Production boundary

RTC Community Production was inspected read-only and does **not** yet contain the Service Centre Aug-28 migration. Production Supabase was not mutated by this integration.

The signed release candidate was not generated because the controlled release bundle remains incomplete in GitHub Actions:

- `RTC_PROD_SUPABASE_URL`
- `RTC_PROD_SUPABASE_PUBLISHABLE_KEY`
- `GOOGLE_SERVICES_JSON_BASE64`
- `RTC_ANDROID_KEYSTORE_BASE64`
- `RTC_ANDROID_KEYSTORE_PASSWORD`
- `RTC_ANDROID_KEY_ALIAS`
- `RTC_ANDROID_KEY_PASSWORD`

This is fail-closed release configuration, not a debug/integration source failure.

## Verification limitations

- Android instrumentation/Compose tests were compiled and packaged, but not executed on an emulator or physical device by this workflow.
- No live Yoco charge or webhook delivery was executed.
- No Production Supabase migration/function deployment was performed.
- Production signing/runtime secrets were not reconstructed or exposed.

## Merge gate

Before merging PR #19:

1. run a fresh Android Production Verification workflow on the report-only final head;
2. record its debug artifact metadata;
3. confirm `main` has not moved from the reviewed base;
4. confirm PR #19 remains mergeable with no unresolved review threads;
5. mark ready for review;
6. merge using the exact verified head SHA;
7. require the resulting `main` push workflow to pass.
