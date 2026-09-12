# Phase 41 — Device-Validation and Debug APK Packaging Evidence

**Date:** 2026-08-26  
**Branch:** `recovery/nonproduction-baseline`  
**Scope:** Safe validation of the Phase 40 Home dashboard refinement  
**Production status:** Unchanged. This work did not query, deploy to, or modify the production Supabase project, production data, policies, credentials, or application release channel.

## Review of Phase 40 evidence

Phase 40 accurately records the Home-only refinement: central `16.dp` dense-dashboard padding, a `76.dp` project-progress ring using a `6.dp` stroke, no outer snapshot-card click handler, and independent existing directory routes for Projects, Centres, and Opportunities. The review confirms that this slice retained the status-based-progress disclosure and did not introduce fabricated analytical data, back-end access changes, or global component-token changes.

| Reviewed Phase 40 statement | Current assessment |
|---|---|
| `RtcHomeDashboard` contains local Home reference tokens | Confirmed in source and covered by the regression suite. |
| The three snapshot metrics route independently | Confirmed by source contract: `projects`, `centres`, and `opportunities` are each passed to the existing directory callback. |
| The outer snapshot no longer redirects every tap to Projects | Confirmed by regression contract. |
| Physical/authenticated device evidence was unavailable | Still true: no handset or emulator was connected or configured in the restored sandbox. |
| Full APK packaging had not been attempted | Superseded by the successful debug packaging validation below. |

## Device-validation environment

No Android device was attached to ADB. The sandbox also has no Android emulator binary, Android Virtual Device, SDK system image, `app/src/androidTest` suite, or runnable instrumentation test target. Therefore, a genuine device/emulator interaction run could not be executed truthfully.

The executable substitute available in this restored environment was the repository’s source-level synthetic validation contract suite. This is **not** presented as a replacement for physical-device or fresh authenticated-session validation; it validates source contracts only.

| Check | Result |
|---|---|
| ADB attached devices | None |
| Local emulator / configured AVD / system image | None available |
| Android instrumentation suite | None present in the recovered repository |
| Synthetic source-contract suite | **66/66 passed** via `uv run --no-project tools/tests/run_contract_tests.py` |
| Relevant covered contract | `test_synthetic_device_validation_protocol_is_source_controlled_and_fail_closed` passed |

## Debug APK packaging status

The debug package was rebuilt locally with Gradle 8.13, JDK 21, one Gradle worker, bounded daemon memory, and no configuration cache. The first `assembleDebug` attempt reached `packageDebug` but failed without a usable root-cause message. A diagnostic `:app:packageDebug --stacktrace` retry then completed successfully. The complete `:app:assembleDebug` task was run again afterward and completed successfully with all 43 tasks up to date.

| Check | Result |
|---|---|
| Final assemble task | **Passed**: `:app:assembleDebug` completed successfully. |
| Debug APK | `app/build/outputs/apk/debug/app-debug.apk` |
| File size | 36,280,407 bytes |
| Package archive integrity | **Passed**: ZIP structure test completed successfully. |
| SHA-256 | `66b469da9cbcdf0f3f24fced61acfe0327ea35c0a6500d3864ddbc0580e206f0` |
| Application identity | `za.org.rtc.community`, version `1.0.5` (`versionCode 30`) |
| Supported SDK range | min SDK 26; target SDK 36 |

The Android build emitted non-blocking warnings about two native libraries that could not be stripped and several existing deprecated Material icon APIs. These warnings did not prevent packaging and are not introduced by the Phase 40 Home refinement.

## Release posture

A locally built, structurally valid **debug APK** now exists. This does not constitute production release approval, Play signing validation, real authenticated-session evidence, physical-device UX verification, or production deployment authorization. Remaining release gates include a connected Android device or configured emulator, a safe isolated authenticated test runtime, and validation of the relevant resident interactions on that environment.
