# Phase 45 — Administrator and Onboarding Static Readiness Audit

**Date:** 2026-08-26  
**Branch:** `recovery/nonproduction-baseline`  
**Scope:** Static analysis of the Android administrator workspace, onboarding, routes, custom deep links, Android manifest, and release-quality gates  
**Production status:** Unchanged. No production Supabase project, data, authentication state, credentials, policy, or release channel was accessed or modified.

## Audit result

The source-level administrator and onboarding navigation design is **functionally connected and fail-closed at its Android boundary**. Administrator destinations are evaluated by role and live-session MFA status, the only exported application entry point is the launcher/custom URI activity, and inbound community deep links are constrained to the declared scheme, host, and path before a bounded identifier reaches a protected content loader.

The static release gate is **not a production-release approval**. Android lint has no errors, but the project retains dependency, resource, and code-hygiene warnings that must be triaged. More importantly, the fresh physical-device/authenticated-session gate remains unfulfilled because the sandbox cannot boot a compatible emulator without KVM and no handset is connected.

## Deep-link and navigation findings

| Surface | Static finding | Assessment |
|---|---|---|
| Exported Android entry point | `MainActivity` is deliberately exported for launcher use and the `rtc://community` custom URI. Services and providers are not exported. | Acceptable with the handler constraints below. |
| Community post deep link | Handler requires `rtc` scheme, `community` host, and first path segment `post`; the ViewModel accepts only a non-empty identifier of up to 128 ASCII letters, digits, hyphens, or underscores. | Pass. The route proceeds through the existing protected post-detail loading path. |
| Community alert deep link | Handler requires the same scheme/host and `alert` path, then applies the same bounded identifier rule before notification routing. | Pass. |
| Administrator workspace routes | Access Management, Operational Controls, Privacy Analytics, System Health, Administrative Activity, work profile, and staff routes are rendered through `ProtectedRoute`. | Pass. Visual navigation does not itself grant access. |
| System Administrator MFA | `ADMIN_MFA` is System Administrator-only. Consequential administrator routes require verified MFA for live Supabase sessions. | Pass at source level. A real MFA session remains to be exercised on-device. |
| Onboarding navigation | Get started, Sign in, Create account, recovery, account-mode switching, and Back are internally connected through supported authentication callbacks. | Pass at source level. |
| Inert controls | No `onClick = {}` handlers remain in `MainActivity.kt`. | Pass. |

## Static security findings

| Control | Finding | Assessment |
|---|---|---|
| Privileged credentials | Repository scan found no Android-embedded service-role key, database password, service-account JSON, or private-key marker. | Pass. |
| Backup | `android:allowBackup="false"` remains set. | Pass. |
| Cleartext traffic | `android:usesCleartextTraffic="false"` was added to make the HTTPS-only posture explicit. | Pass; committed source contract requires it. |
| Notification permission | The Android 13 `POST_NOTIFICATIONS` permission launch is now guarded directly at its action boundary by the API-33 check. | Remediated. |
| Transport literals | No cleartext application network endpoint was found. Manifest/XML namespace URIs are not transport endpoints. | Pass. |

## Automated validation

| Gate | Result |
|---|---|
| Source regression contracts | **70/70 passed** via `uv run --no-project tools/tests/run_contract_tests.py`. This includes protected-route, deep-link, onboarding, transparent splash, no-credential, cleartext-disabled, and API-33 permission contracts. |
| JVM unit tests | **Passed** via `:app:testDebugUnitTest`. |
| Android resource processing | **Passed** via `:app:processDebugResources`. |
| Android lint | **Passed** via `:app:lintDebug` with **0 errors, 56 warnings, and 2 hints**. |

## Lint warning triage

The lint report has no errors. Most warnings are update advisories rather than a demonstrated runtime fault: 26 Gradle dependency warnings, 10 newer-version warnings, and 2 Android Gradle Plugin version advisories. These must be upgraded through a separately tested dependency-maintenance slice, not blindly during production hardening.

The remaining code and resource warnings are remediation-grade rather than release-proof: five duplicated launcher-icon findings, three unused splash/launcher resources, five optional Kotlin extension recommendations, two cursor-lifetime findings that need manual lifecycle review despite `.use` usage, one window-size API recommendation, one obsolete SDK guard, and two Compose autoboxing hints. Existing Kotlin deprecation warnings for Material icons and top-app-bar colors should also be cleaned as a quality task. None is a lint error, but the set should be triaged before a production release candidate is approved.

## Remaining release gates

A static scan cannot prove a release-ready application. The following remain **NO-GO** conditions:

1. Fresh isolated authenticated-device validation of the administrator role, verified MFA, protected routes, profile/sign-out, and backend-authorized operations.
2. Device-level custom URI tests for malformed, valid, signed-out, and signed-in post/alert links.
3. Fresh end-to-end onboarding verification: splash rendering, email confirmation, sign-in, recovery, and return navigation.
4. Release signing, Play Console configuration, dependency-update triage, and a signed release artifact validation.

No production approval, production deployment, or remote push is implied by this audit.
