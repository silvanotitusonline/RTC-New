# RTC Community Service Centre MVP — Final Handoff

## 1. Completion Status

The Service Centre MVP implementation is complete on the isolated branch:

`feature/service-centre-mvp`

The implementation remains unmerged. `main` and Production Supabase were not modified by this work.

Verified implementation head before this documentation-only handoff commit:

`29f907b5ff129b08d873aa5a5b5d8edc76e61fb2`

GitHub Actions `Android Production Verification` run #374 completed successfully against that implementation head.

## 2. Product Scope Delivered

### Local Radar

- Dedicated Service Centre entry point adjacent to Marketplace.
- Category filtering using the existing Marketplace category taxonomy.
- Locality-first discovery with optional current-location enhancement.
- Radius-bounded discovery.
- Provider cards show category, locality/service area, starting price, optional linked Marketplace rating/verification, and distance only when the resident supplies an origin.
- Raw provider coordinates are not returned by the public Local Radar RPC.

### Provider Sidecar Profile

Existing RTC accounts can activate a provider profile without creating a second identity system.

Primary provider inputs:

1. service category;
2. service area/locality;
3. starting ZAR price.

Additional bounded behavior:

- travel radius defaults to 25 km and is constrained to 1–50 km;
- current-location permission is optional;
- typed locality can be resolved for storage when GPS is not used;
- providers can update, pause and resume the profile;
- a provider can optionally link an existing Marketplace business while Service Centre booking ownership remains independent from Marketplace publication ownership.

### Booking Request

The customer booking request is intentionally reduced to three primary fields:

1. requested date/time;
2. service location;
3. offered amount.

Creation is authenticated, validated, idempotent and committed through a database RPC. Android does not write booking tables directly.

### Booking Hub

The resident-facing Booking Hub provides three operational groupings:

- Pending;
- Accepted / Upcoming;
- Completed / Declined / Cancelled.

The customer/provider perspective is derived from the authenticated database actor rather than client-supplied role state.

### Booking Lifecycle

Authoritative states:

1. `PENDING_PROVIDER`
2. `ACCEPTED_AWAITING_PAYMENT`
3. `CONFIRMED`
4. terminal outcomes: `COMPLETED`, `DECLINED`, `CANCELLED`

Provider actions:

- Accept
- Decline
- Complete

Customer actions:

- Cancel where the state machine permits it
- Pay the commitment fee after provider acceptance

State transitions are performed through backend RPCs using authenticated identity, row locking, idempotency and server-side transition checks.

### Booking Chat

- Text-only participant chat.
- Maximum message length: 1,000 characters.
- Idempotent message submission.
- Initial fetch plus bounded five-second refresh while the conversation is foreground-visible.
- No Supabase Realtime dependency.
- No offline booking/chat mutation queue.

### Commitment Payment

The payment flow uses hosted Yoco Checkout rather than collecting card data in Android.

Security boundary:

- Android never receives the Yoco secret key.
- Android never confirms a payment.
- Commitment-fee amount is calculated/snapshotted server-side.
- Checkout amount is sent to Yoco as integer cents.
- Checkout creation uses Yoco idempotency.
- Browser return is informational only and never mutates booking/payment state.
- Payment confirmation occurs only after a verified Yoco webhook.
- Webhook verification uses the raw request body, webhook ID, timestamp, HMAC-SHA256, base64-decoded `whsec_` secret, constant-time comparison and a three-minute replay window.
- Confirmation reconciles external checkout ID, external payment ID, amount and `ZAR` currency through a `service_role`-only RPC.

### Notifications and Deep Links

- Dedicated Android notification channel: `rtc_service_bookings`.
- Default notification importance; booking events are not treated as emergency/safety alerts.
- Constrained Service Centre event types.
- Notification recipient/title/body/payload are derived from booking context on the server.
- Deep link: `rtc://service-centre/booking/{bookingId}`.
- Cold and warm notification intents feed the existing single-Activity Compose navigation architecture.

### Marketplace Integration

- Marketplace Home exposes `Open Service Centre`.
- Marketplace service offerings expose `Request Booking`.
- The existing Marketplace business ID can be resolved by the Service Centre provider-detail RPC when a provider profile is linked to that business.
- Marketplace publication, ownership and moderation architecture remains unchanged.

## 3. Non-Production Database Deployment

Target project:

`eqwstpdjoineycrkhpht`

Migration:

`supabase/migrations/20260828144225_service_centre_mvp.sql`

Tables deployed:

- `service_centre_provider_profiles`
- `service_centre_bookings`
- `service_centre_booking_messages`
- `service_centre_booking_payments`
- `service_centre_booking_events`

Live verification confirmed for all five tables:

- RLS enabled;
- RLS forced;
- no direct `SELECT`, `INSERT`, `UPDATE` or `DELETE` privileges for `anon`;
- no direct `SELECT`, `INSERT`, `UPDATE` or `DELETE` privileges for `authenticated`.

The Service Centre public RPC surface is present in Non-Production, including provider discovery/profile, booking lifecycle, messaging, payment preparation/confirmation and notification-context functions.

Sensitive payment RPC execution was verified:

- `service_centre_attach_commitment_checkout(uuid,text)` — `authenticated`: denied; `service_role`: allowed.
- `service_centre_confirm_commitment_payment(text,text,integer,text)` — `authenticated`: denied; `service_role`: allowed.

## 4. Non-Production Edge Function Deployment

The following functions are ACTIVE in RTC Community Non-Production:

### `service-centre-payment-create`

- deployed version at final verification: 2;
- `verify_jwt = true`;
- uses authenticated RTC caller identity;
- derives commitment fee from server RPC;
- creates hosted Yoco Checkout;
- attaches external checkout identity through `service_role`-only RPC.

### `service-centre-payment-webhook`

- deployed version at final verification: 1;
- `verify_jwt = false` because the caller is Yoco rather than a Supabase user;
- verifies the Yoco HMAC signature and replay window inside the function;
- confirms payment only through the restricted server RPC.

### `service-centre-notify`

- deployed version at final verification: 2;
- `verify_jwt = false` because it supports two explicit in-function authentication paths:
  - authenticated RTC-user calls validated against Supabase Auth;
  - internal payment-confirmation calls authenticated by the Service Centre internal secret;
- arbitrary recipient/title/body targeting is not accepted from Android.

The authenticated functions were parity-redeployed using the exact GitHub branch `index.ts` source and exact shared `supabase/functions/_shared/auth.ts` module after resolving the Supabase deployment bundle's sibling-path requirement.

## 5. Android / Source Verification

Verified workflow:

`Android Production Verification` — run #374 — `SUCCESS`

Verified implementation SHA:

`29f907b5ff129b08d873aa5a5b5d8edc76e61fb2`

Successful gates:

- 137/137 source regression contracts;
- 14/14 shared Edge authorization tests;
- JVM unit tests and Kotlin compilation;
- Android lint;
- debug APK assembly;
- Compose smoke-test compilation;
- debug verification artifact upload.

Uploaded verification artifact:

`rtc-community-debug-verification`

Artifact SHA-256 digest reported by GitHub Actions:

`7ea421612448c735af9df3bb57679b7714c14c09e0bc67ee652d390dc4a966cf`

A signed release APK/AAB was not generated because the CI environment does not contain the complete release signing/runtime/Firebase secret bundle. The release-candidate step therefore skipped signing rather than fabricating credentials. This does not invalidate the successful source, JVM, lint, debug assembly or Compose compilation gates.

## 6. Runtime Secret / External Configuration Prerequisites

The following values are intentionally not committed to source control:

- `YOCO_SECRET_KEY`
- `YOCO_WEBHOOK_SECRET`
- `SERVICE_CENTRE_INTERNAL_NOTIFY_SECRET`
- optional `SERVICE_CENTRE_PAYMENT_RETURN_URL`

The available Supabase management surface does not expose Edge Function secret values, so this handoff does not claim that those secret values are configured. The payment functions fail closed if mandatory Yoco configuration is absent.

Before a real end-to-end payment acceptance test:

1. configure/verify the Non-Production Yoco secret key;
2. configure/verify the Non-Production Yoco webhook signing secret;
3. configure the same internal notification secret for the webhook and notification function;
4. register the Non-Production `service-centre-payment-webhook` endpoint in Yoco;
5. execute a test checkout from an authenticated customer booking;
6. verify webhook-driven `CONFIRMED` transition and FCM booking notification.

Existing Firebase/FCM credential provisioning remains owned by the established RTC notification infrastructure.

## 7. Branch / Merge Boundary

At implementation verification:

- branch: `feature/service-centre-mvp`;
- PR: #18;
- PR: open, draft, mergeable and unmerged;
- implementation branch: ahead of `main` with no behind commits at verification;
- `main`: not modified by the Service Centre implementation workflow;
- Production Supabase: not targeted or merged;
- Non-Production Supabase: migration and three Service Centre Edge Functions deployed.

## 8. Known MVP Limits

Deliberately excluded from this MVP:

- escrow;
- provider payouts;
- dynamic/AI pricing;
- AI matching or scheduling;
- heavy legal-contract workflow;
- media chat;
- Supabase Realtime chat;
- offline transactional mutation queue;
- direct customer/provider card processing in Android.

These exclusions preserve the intended low-friction MVP: discover a local provider, request a booking, allow provider acceptance, secure the booking with a server-authoritative commitment payment, and keep both parties informed through lightweight chat and notifications.

## 9. Handoff Decision

The Service Centre source implementation is complete, Android/build verified and deployed to Non-Production. It is ready for controlled Non-Production functional/UAT testing.

Production promotion and PR merge remain separate explicit release decisions and were not performed by this implementation work.
