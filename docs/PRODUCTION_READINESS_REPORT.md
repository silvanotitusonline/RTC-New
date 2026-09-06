# RTC Community 1.0.5 — Production Readiness Report

**Release track:** Concept 6 — Community Engagement Feed  
**Application ID:** `za.org.rtc.community`  
**Assessment date:** 26 August 2026
**Scope:** Android client, Supabase contracts, protected workspaces, media, notifications, RTC AI, administrator MFA, persistence and regression controls.

## Executive status

RTC Community is at **source/backend release-candidate readiness**. The Android source now contains the mathematically harmonized Concept 6 design system, centralized protected-route authorization, durable Community media uploads, real Support persistence, guarded RTC AI transactions, administrator MFA/TOTP handling, hardened notification routing, and source-controlled forward Supabase migrations.

A final binary GO requires an Android-capable CI run to pass the actual Gradle compiler, unit-test, Android Lint and APK/AAB assembly gates. GitHub Actions is included in this repository specifically to provide that independent binary verification.

Leaked-password protection remains disabled as an explicitly accepted Supabase subscription limitation for this release. No attempt is made to bypass that plan restriction.

## Verified implementation areas

### Architecture and bootstrap

- Jetpack Compose, `RtcViewModel`, `RtcRepository`, `ProductionUxRepository`, Supabase and Hilt boundaries are preserved.
- `RtcCommunityApplication` is restored as `@HiltAndroidApp`.
- WorkManager uses the injected `HiltWorkerFactory`; the default AndroidX WorkManager initializer is removed from the manifest.
- FCM service and notification channels are implemented.
- Resident primary navigation remains Home, Community, Explore and Support; Account remains a secondary destination.

### Concept 6 UI/UX

- Semantic deep-ink/graphite surfaces, emerald resident actions, civic-gold protected-administration semantics and danger-only red are implemented.
- Mathematical relationships are centralized through `RtcMath`, Fibonacci-backed semantic tokens and a tempered complete Material type scale.
- Screen and shared-component code contains no raw `dp` measurement literals; physical values are isolated in the design-token source.
- Community media preview framing uses the Golden landscape ratio while dynamic cards and full-screen/native media remain content-driven.
- True pills/FABs use `CircleShape`; the one-time administrator QR remains a centered, undistorted square.
- Resident, feed, administrative and analytical density modes share one inherited component system.
- Compact, medium and expanded classification is centralized at the Android-compatible 600/840dp breakpoints.
- Shared Compose components include the RTC screen scaffold, cards, status chips, resident navigation, protected-area banners, Community feed cards, emergency treatment, case progress and empty states.
- Major resident, staff and administrator surfaces use the shared design system rather than static PNG artwork.
- Staff and System Administrator surfaces remain visually related to the resident application while retaining separate trust boundaries.

### Protected workspaces and authorization

- Protected destinations are evaluated through a centralized fail-closed route policy.
- Work Queue, My Work, Staff Alerts, Content Management, Moderation, RTC AI, Access Management, Operational Controls, Privacy Analytics, System Health and Administrative Activity are guarded.
- Authority/MFA loss returns the user to a safe workspace.
- The previous unknown-work-item fallback to Operational Controls is removed.
- Backend authorization remains authoritative; UI visibility is not treated as the security boundary.

### Support

- Resident Support uses persisted Supabase RPCs rather than seeded demo cases.
- Case listing, case messages and support-state workflows are wired to the hardened backend contracts.
- Direct client CRUD against protected Support tables remains denied.

### Community and media

- Trending uses legitimate engagement scoring and does not reward abuse/report counts.
- Following uses real topic-follow state.
- Community feed projection includes stable avatar revision and media metadata.
- Media selection is image/video scoped.
- Profile and feedback media are bounded before upload.
- Community upload preparation avoids full-video `readBytes()` flows.
- Room and WorkManager provide a durable upload outbox.
- `finalize_community_post` is idempotent for safe retry after uncertain network outcomes.
- Full-screen media uses pager navigation, video poster frames, Media3 buffering/error handling and signed-URL refresh with playback-position recovery.

### Notifications

- FCM token rotation is registered through the backend.
- Notification channels separate normal Community updates from safety/emergency alerts.
- Cold/warm notification intents are routed through explicit RTC destinations.
- Supabase notification delivery records support bounded retry, stale-token cleanup and accepted/permanent-failure tracking.

### RTC AI

- Android uses the guarded `rtc-admin-ai` Edge Function; the former local development-success adapter is removed.
- Commands and confirmations require fresh reCAPTCHA Enterprise verification.
- Role boundaries are enforced server-side.
- RTC AI follows Proposal → Review → Confirm → Transaction → Audit semantics.
- Proposal generation never represents itself as a completed production mutation.

### Administrator MFA and operational controls

- Protected System Administrator actions remain MFA/AAL gated.
- TOTP enrollment secrets are not persisted or rendered as plain text.
- Enrollment uses an in-memory QR representation and clears enrollment material after verification/dismissal.
- Operational Controls use Android date/time pickers rather than raw ISO entry.
- Consequential controls retain reason, impact preview, explicit typed confirmation and server audit behavior.

## Supabase release state

Forward migrations included in the source cover the final Community feed/finalization contract and release-candidate performance cleanup.

The final performance pass removes confirmed duplicate indexes, optimizes remaining hot RLS auth initialization, separates write policies from duplicate SELECT evaluation and adds covering indexes to advisor-identified foreign-key paths. Unused-index INFO notices are intentionally not treated as deletion instructions in a young environment.

All six RTC Edge Functions were previously verified for matching code hashes between Non-Production and Production:

- `rtc-admin-ai`
- `rtc-fcm-dispatch`
- `community-media-url`
- `rtc-privacy-requests`
- `report-community-post`
- `dispatch-community-alerts`

## Source regression gates

The repository contains source-level regression tests that verify:

- four-destination resident navigation;
- Concept 6 shared-component adoption;
- protected-route fail-closed behavior;
- real RTC AI Edge Function usage;
- MFA QR/secret lifecycle;
- bounded/durable media handling;
- genuine Community Trending/Following semantics;
- persisted Support contracts;
- Hilt WorkManager initialization;
- production minification/resource shrinking;
- no embedded privileged server credentials;
- source-controlled forward Supabase migrations;
- GitHub Android build pipeline requirements.
- centralized Golden Ratio, Fibonacci and π definitions;
- semantic screen/shared geometry and true circular controls;
- density modes, responsive window classes and Golden media framing;
- mathematical design-system and evidence-based audit documentation.

The completed suite contains **38 contracts**, preserving all 29 baseline contracts and adding five mathematical-harmony plus four Android compile-compatibility contracts.

Run locally with:

```bash
python3 tools/tests/run_contract_tests.py
```

## GitHub Android CI gate

`.github/workflows/android-ci.yml` provisions:

- JDK 21;
- Gradle 8.13;
- Android platform 36;
- Android SDK Build Tools 35.0.0;
- platform tools.

It then runs:

```text
source regression contracts
Gradle testDebugUnitTest
Gradle lintDebug
Gradle assembleDebug
```

If repository secret `GOOGLE_SERVICES_JSON_BASE64` is available, the workflow restores `app/google-services.json` only for the CI run and additionally attempts:

```text
Gradle assembleRelease
Gradle bundleRelease
```

The Firebase configuration file remains gitignored and is not committed to source control.

## Secrets and signing

The repository must not contain:

- Supabase service-role/secret keys;
- Google/Firebase service-account private keys;
- signing keystores;
- `signing.properties`;
- `local.properties`;
- production `google-services.json`.

For an actual signed release, configure the signing material through an approved CI secret mechanism and provide the Android Firebase configuration through `GOOGLE_SERVICES_JSON_BASE64`.

## Accepted limitation

**Supabase Leaked Password Protection:** disabled because the current subscription does not expose that feature. This is owner-approved for the current release and must be revisited if the plan changes.

## Remaining binary/device verification

The following claims must not be made until GitHub Actions or another Android-capable runner provides evidence:

- Kotlin/Android compilation succeeds end-to-end;
- Android Lint has no release-blocking findings;
- JVM unit tests compile and pass under the real Gradle dependency graph;
- debug APK assembles;
- release APK/AAB assembles with the real Firebase configuration;
- signed release installation succeeds;
- on-device notification, media playback, deep-link and MFA flows behave correctly under Android lifecycle/process-death conditions.
- compact/medium/expanded and portrait/landscape layouts render without clipping or disproportionate whitespace;
- TalkBack traversal, 200% font scaling, light/dark/system themes and Simplified Reading Mode remain visually and functionally accessible;
- the one-time administrator enrollment QR scans successfully at the bounded square display size.

The source-level proportional coherence audit is **96.3/100**. This score is not a binary readiness score and deliberately reserves uncertainty for rendered responsive, visual-weight, motion and accessibility evidence. See `docs/MATHEMATICAL_HARMONY_AUDIT.md`.

## Release decision

**Current classification:** MATHEMATICALLY HARMONIZED SOURCE/BACKEND RELEASE CANDIDATE — BINARY GO PENDING CI AND DEVICE VERIFICATION.

A production binary GO should be issued only after the GitHub Android workflow passes and the resulting artifact completes a final device smoke test.
