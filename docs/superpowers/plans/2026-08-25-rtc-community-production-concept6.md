# RTC Community Production + Concept 6 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver a source-complete, production-hardened RTC Community Android application using Concept 6 without changing business/security semantics.

**Architecture:** Preserve the ViewModel/repository/Supabase boundaries. Add explicit bootstrap/DI, central route access policy, durable local persistence and reusable Concept 6 Compose components. Promote only tested forward Supabase migrations.

**Tech Stack:** Kotlin 2.3, Jetpack Compose/Material3, Navigation Compose, Hilt, Room, DataStore, WorkManager, Media3, Firebase Messaging, Supabase Kotlin/Postgres/Storage/Functions.

**Spec:** `docs/superpowers/specs/2026-08-25-rtc-community-production-concept6-design.md`

## Global Constraints
- Resident bottom navigation is Home / Community / Explore / Support only.
- Account is secondary.
- No service-role secrets in Android.
- No demo production data.
- No whole-video in-memory loading.
- Protected routes fail closed.
- Leaked-password protection remains disabled due current plan limitation and is documented, not silently treated as enabled.

---

### Task 1: Restore Bootstrap, DI and Notification Runtime
- [ ] Add failing source-contract tests for missing application/DI/FCM/channel code.
- [ ] Add `RtcCommunityApplication`, Hilt network/database module and `RtcFirebaseMessagingService`.
- [ ] Add channel creation and validated notification intent routing.
- [ ] Re-run tests.

### Task 2: Centralize Route Authorization
- [ ] Add failing route-policy tests.
- [ ] Add pure Kotlin route access policy and integrate navigation shell.
- [ ] Remove privileged unknown-route fallbacks and add safe eviction.
- [ ] Re-run tests and pure Kotlin compiler gate.

### Task 3: Concept 6 Design System
- [ ] Add failing design-contract tests.
- [ ] Replace theme tokens and add reusable Concept 6 components.
- [ ] Migrate resident/staff/admin shell navigation.
- [ ] Re-run source-contract tests.

### Task 4: Persistent Support + Preferences + Drafts
- [ ] Add failing persistence tests.
- [ ] Remove seeded cases/work queue fixtures from release state.
- [ ] Wire Support RPCs, DataStore preferences, Room drafts/outbox and WorkManager recovery.
- [ ] Re-run tests.

### Task 5: Honest Community Feed
- [ ] Add failing semantic tests.
- [ ] Add staging/production feed projection with real reactions/follow state/time decay.
- [ ] Update Android models/repository/UI filters.
- [ ] Re-run live query and source tests.

### Task 6: Media Reliability
- [ ] Add failing tests prohibiting whole-video `readBytes()` and unrestricted picker.
- [ ] Add file-based media preparation and resumable/file upload.
- [ ] Use server finalization, durable outbox, progress/error state.
- [ ] Add pager gallery, poster state, Media3 error/buffering/retry and signed-URL refresh.
- [ ] Re-run tests.

### Task 7: Concept 6 Resident Screens
- [ ] Migrate Public/Auth, Home, Community, Explore, Support, Account, Search, Notifications and Help.
- [ ] Preserve callbacks/models and accessibility semantics.
- [ ] Run source-contract/UI structure tests.

### Task 8: Concept 6 Staff/Admin Screens
- [ ] Migrate Work Queue, My Work Profile, Alerts, Content, Moderation, RTC AI, Admin Workspace, Access, Operations, Privacy and MFA.
- [ ] Keep role/MFA safeguards and auditable actions.
- [ ] Run security/source-contract tests.

### Task 9: Supabase Release Closure
- [ ] Re-run security/performance advisors and contract inventory.
- [ ] Promote tested feed/function/migration deltas to Production.
- [ ] Verify Edge Function hashes and schedulers.
- [ ] Document accepted leaked-password limitation.

### Task 10: Final Verification + Package
- [ ] Run all local tests and pure Kotlin compile checks.
- [ ] Run Gradle debug/unit/lint/release if Android toolchain is available; otherwise document the exact environment blocker.
- [ ] Create updated source ZIP, migration map, modified-file manifest and GO/NO-GO report.
