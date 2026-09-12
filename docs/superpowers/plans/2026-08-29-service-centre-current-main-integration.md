# Service Centre Current-Main Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Integrate the verified Service Centre MVP onto the current Brand+Community+Marketplace+Security `main` without importing stale trunk history or weakening existing trust boundaries.

**Architecture:** Current `main` is authoritative. Reuse the donor Service Centre feature package and backend source where it is isolated; reconcile shared seams manually in current `MainActivity`, DI, Marketplace entry points, navigation, notification plumbing, and Supabase function configuration. Production Supabase remains untouched during integration; non-production is used only for parity/runtime verification.

**Tech Stack:** Kotlin, Jetpack Compose, Hilt, Navigation Compose, Supabase/PostgreSQL, Supabase Edge Functions/Deno, Firebase Messaging, GitHub Actions.

**Spec:** `docs/SERVICE_CENTRE_MVP_HANDOFF.md` and donor plan `docs/superpowers/plans/2026-08-28-service-centre-mvp.md`

## Global Constraints

- Preserve the current single-Activity architecture and Brand-configured app root.
- Preserve Marketplace, Community and Supabase Security ownership already on `main`.
- Do not merge `feature/service-centre-mvp` wholesale.
- Do not deploy or mutate Production Supabase.
- Keep direct Service Centre table DML denied to `anon`/`authenticated`; lifecycle remains RPC-authoritative.
- Browser payment return is never authoritative for payment confirmation; webhook confirmation remains authoritative.
- Keep service-role/Yoco/signing/Firebase secrets out of Android/source control.
- Require exact-head GitHub Actions verification before merge.

---

### Task 1: Port isolated Service Centre domain/data/presentation source

**Files:**
- Add: `app/src/main/java/za/org/rtc/community/feature/servicecentre/**`
- Add: Service Centre JVM tests under `app/src/test/java/za/org/rtc/community/feature/servicecentre/**`
- Add: `tools/tests/test_service_centre_source_contracts.py`

- [ ] Copy the verified donor feature/package files byte-for-byte where they do not overlap current `main`.
- [ ] Preserve donor validation, booking state, locality resolution, repositories and scoped ViewModels.
- [ ] Run source/JVM contracts through GitHub Actions after the first integration checkpoint.

### Task 2: Reconcile Android shared seams against current main

**Files:**
- Modify: `app/src/main/java/za/org/rtc/community/MainActivity.kt`
- Modify: `app/src/main/java/za/org/rtc/community/di/AppModule.kt`
- Modify: `app/src/main/java/za/org/rtc/community/navigation/RtcNavigation.kt`
- Modify: `app/src/main/java/za/org/rtc/community/ui/navigation/RtcCommunityApp.kt`
- Modify: `app/src/main/java/za/org/rtc/community/ui/navigation/RtcCommunityNavGraph.kt`
- Modify: `app/src/main/java/za/org/rtc/community/notifications/RtcNotificationChannels.kt`
- Modify: `app/src/main/java/za/org/rtc/community/app/RtcFirebaseMessagingService.kt`
- Modify: Marketplace Home/Business Service Centre entry points only.

- [ ] Preserve current Brand-configured app root and one-Activity bootstrap.
- [ ] Add Service Centre deep-link handling without replacing existing Community/Marketplace deep links.
- [ ] Add Service Centre routes to the current navigation graph without replacing Brand route presentation or existing protected-route policy.
- [ ] Add Hilt bindings and notification channel additions without removing current bindings/channels.
- [ ] Keep Marketplace ownership intact; add only Service Centre entry CTAs.

### Task 3: Port and audit backend source

**Files:**
- Add: `supabase/migrations/20260828144225_service_centre_mvp.sql`
- Add: `supabase/functions/service-centre-payment-create/index.ts`
- Add: `supabase/functions/service-centre-payment-webhook/index.ts`
- Add: `supabase/functions/service-centre-notify/index.ts`
- Modify: `supabase/config.toml`

- [ ] Preserve current shared Edge authorization module and Security-hardened function configuration.
- [ ] Verify migration version uniqueness against current main.
- [ ] Verify Service Centre tables are RLS-enabled/forced and client direct DML remains revoked.
- [ ] Verify payment attach/confirm RPCs remain service-role-only.
- [ ] Verify payment-create remains user-JWT gated, webhook remains HMAC-authoritative, and notify explicitly authenticates either user JWT or internal secret.

### Task 4: Non-production parity and runtime verification

- [ ] Confirm the Service Centre migration/version already deployed to RTC Community Non-Production matches repository source or document any parity delta before merge.
- [ ] Confirm all three deployed Edge Functions are active with intended JWT/HMAC/internal-secret boundaries.
- [ ] Run rollback-only authenticated discovery/provider/booking/chat scenarios where possible.
- [ ] Verify anon/direct-table denial and service-role-only payment lifecycle boundaries.
- [ ] Do not perform a live Yoco charge unless the required non-production Yoco secrets are verified and explicitly safe to use.

### Task 5: Exact-head integration verification and merge handoff

**Files:**
- Add: `docs/SERVICE_CENTRE_INTEGRATION_FINAL_REPORT.md`

- [ ] Open a draft PR from `integration/service-centre-production-v2` to `main`.
- [ ] Require source contracts, shared Deno auth tests, JVM tests, lint, debug APK, AndroidTest APK compilation, artifact upload and cleanup to pass.
- [ ] Record artifact ID/size/SHA-256 and non-production verification results.
- [ ] Run a final report-head confirmation workflow.
- [ ] Merge only with an expected-head SHA guard while `main` remains unchanged and the PR remains mergeable.
- [ ] Require the post-merge `main` push workflow to pass before declaring Service Centre integration closed.
