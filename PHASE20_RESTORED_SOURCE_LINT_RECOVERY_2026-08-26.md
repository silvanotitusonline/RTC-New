# Phase 20 — Restored Source and Android Lint Recovery

**Date:** 26 August 2026
**Scope:** Local recovery of the complete Android source from the user-supplied feature-branch archive, followed by correction of independently reproduced Android lint errors.
**Environment:** Local build workspace only. The isolated Supabase non-production project was not changed during this phase. Production was not accessed or changed.

## Source provenance and restoration boundary

The user supplied `RTC-Community-Production-feature-mathematical-harmony-system.zip`. Archive integrity and entry paths were validated before extraction. The archive contains the complete Android project source, Gradle build configuration, module source/resources, tests, CI workflow, Supabase migrations, Edge Function source, and source contracts. It is the open `feature/mathematical-harmony-system` snapshot, not a merged or release-approved branch.

The archive does not include Gradle launcher scripts or the wrapper JAR. For local validation only, the official Gradle 8.13 distribution specified by the project wrapper properties was installed outside the source workspace. The reset environment also required restoration of an isolated Android SDK containing platform tools, API 36, and Build Tools 35.0.0, plus a local Java 21 development kit. These toolchain repairs are not application-source changes.

The retained isolated non-production migrations, synthetic real-session runner, and prior evidence were added as a separate source-control checkpoint without overwriting the restored Android adapter. The divergent adapter remains deliberately unmerged until a method-level contract reconciliation can be reviewed and tested.

## Reproduced build findings

The restored source regression contracts passed before Android lint work. Local `testDebugUnitTest` then passed once the reset environment’s missing Android SDK and Java compiler were restored. The first local lint run identified three error classes that were not safely resolved by a lint baseline:

| Error class | Source boundary | Corrective action |
|---|---|---|
| API-27 resource used by an API-26 minimum-SDK app | Base theme resource | Removed `android:windowLightNavigationBar` from `values/` and placed the complete themed style, including that attribute, in `values-v27/`. |
| Media3 unstable API opt-in was not recognised by Android lint | Bounded local video preparation | Replaced Kotlin opt-in annotations with AndroidX `@OptIn(markerClass = [UnstableApi::class])` on the three local transformation boundaries. |
| `StateFlow.value` read during composition | Support-case message author label | Collected the existing ViewModel session using lifecycle-aware Compose state and used the collected value. |

No application minimum SDK was raised. No lint suppression, lint baseline, backend bypass, or release configuration change was added.

## Regression coverage

`tools/tests/test_android_compile_contracts.py` now prevents regression of the exact three fixes by checking the version-qualified theme attribute, AndroidX Media3 opt-in annotation, and reactive support-case session observation. The full source-contract suite increased from 38 to 41 passing contracts.

## Verification evidence

All commands were local and sequential. The temporary build-memory settings were `-Dorg.gradle.jvmargs='-Xmx1024m -Dfile.encoding=UTF-8'` and `-Pkotlin.daemon.jvmargs=-Xmx768m`; no committed Gradle memory configuration was changed.

| Check | Verified outcome |
|---|---|
| `node --check tools/nonprod_synthetic_backend_tests.js` | Passed before restore reconciliation. |
| `python3 tools/tests/run_contract_tests.py` | 41/41 passed after lint fixes. |
| `testDebugUnitTest` | Passed after final source edits. |
| `lintDebug` | Passed after the three targeted fixes. |
| `assembleDebug` | Passed; a local debug APK was generated for build verification only. |

The debug APK is not a production artifact, was not distributed, and must not be represented as release-ready.

## Remaining boundaries

The open feature branch still requires full code review and a deliberate merge/release decision. The retained non-production adapter and real-session contract runner must be reconciled with the restored source at method level before relying on the Android implementation for the earlier recovery evidence. Real-device media playback, lifecycle behaviour, accessibility, expanded layouts, 200% font scale, TOTP QR scanning, FCM delivery, durable upload recovery, and all production release gates remain outstanding. Overall release status remains **NO-GO**.
