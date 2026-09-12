# Phase 44 — Onboarding Emulator UI Test Attempt

**Date:** 2026-08-26  
**Branch:** `recovery/nonproduction-baseline`  
**Scope:** Local Android emulator validation of splash resource packaging and onboarding navigation  
**Production status:** Unchanged. No production system, project, account, credential, or data was accessed or modified.

## Requested validation

The requested goal was to run automated Android UI checks for the splash and onboarding navigation flow on an emulator. The restored sandbox had no existing emulator binary, Android Virtual Device, system image, connected ADB device, or instrumentation source suite. A local test target was therefore prepared using the sandbox Android SDK only.

## Added automated coverage

`app/src/androidTest/java/za/org/rtc/community/OnboardingNavigationTest.kt` was added and compiled. It is intentionally navigation-only: it never enters credentials, calls a remote service, or uses production configuration.

| Test | Intended verification |
|---|---|
| `packagedSplashUsesSuppliedTransparentRtcLogo` | Confirms `rtc_community_logo_transparent` is packaged as an Android drawable resource. |
| `welcomeGetStartedOpensCreateAccountAndBackReturnsToWelcome` | Exercises Welcome → Get started → Create account → Back → Welcome. |
| `welcomeSignInOpensSecureSignInAndBackReturnsToWelcome` | Exercises Welcome → Sign in → Sign-in screen → Back → Welcome. |
| `accountModeSwitchesBetweenCreateAndSignInWithoutSubmittingCredentials` | Exercises Create account → Sign in → Create account without any credential submission. |

## Local emulator preparation and outcome

| Step | Result |
|---|---|
| Android emulator binary | Installed locally. |
| API 35 Google APIs x86_64 image and AVD | Created, but launch failed because x86_64 Android emulation requires KVM acceleration and `/dev/kvm` is unavailable in this sandbox. |
| API 35 ARM Google APIs Android Test Device image and AVD | Created as a fallback, but launch failed because the x86_64 host emulator does not support the ARM64 AVD architecture. |
| Connected device state | No ADB device became available. |
| Instrumentation test compilation | **Passed** via `:app:compileDebugAndroidTestKotlin`. |
| Connected test invocation | Attempted via `:app:connectedDebugAndroidTest`; failed with `DeviceException: No connected devices!`. |

## Interpretation

The instrumentation source is valid and the local debug package/test code compiled. However, **none of the UI tests executed on an emulator**. The reported connected-test failure is an infrastructure limitation, not a passing UI result or evidence of an application navigation failure.

A compliant rerun requires either a connected Android device or a Linux environment with KVM exposed to the sandbox and a matching x86_64 API 35 AVD. When such a target is available, run:

```bash
ANDROID_HOME=/home/ubuntu/android-sdk \
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 \
/home/ubuntu/rtc-build-tools/gradle-8.13/bin/gradle \
  :app:connectedDebugAndroidTest --no-daemon --no-configuration-cache --max-workers=1
```

No production-release approval can be inferred from this attempted emulator run.
