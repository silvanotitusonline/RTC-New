# RTC Community Android

Production-oriented RTC Community Android source for application ID `za.org.rtc.community`.

This repository contains the native Jetpack Compose client, Concept 6 design system, Supabase forward migrations, deployed Edge Function source that is source-controlled in this workspace, regression contracts, and GitHub Actions Android verification.

Concept 6 now uses a centralized mathematical design system: semantic Fibonacci-backed spatial tokens, tempered proportional typography, true circular geometry, Golden-ratio Community media framing, four content-density modes and compact/medium/expanded window classes. Product behavior, the four-destination resident navigation and protected trust boundaries remain unchanged.

## Toolchain

- Android compile/target SDK 36
- Minimum SDK 26
- Android Gradle Plugin 8.13.2
- Gradle 8.13
- Kotlin 2.3.0
- JDK/JVM 21

## Verify source contracts

```bash
python3 tools/tests/run_contract_tests.py
```

The complete suite contains 38 source regression contracts, including mathematical-harmony and Android compile-compatibility requirements. To verify that ordinary screen/shared-component measurements remain semantic:

```bash
rg -n '\b[0-9]+(?:\.[0-9]+)?\.dp\b' \
  app/src/main/java/za/org/rtc/community/MainActivity.kt \
  app/src/main/java/za/org/rtc/community/ui/components/Concept6Components.kt
```

The search should return no matches. Physical values remain centralized in `RtcDesignTokens.kt`.

## Design-system documentation

- [Mathematical design system](docs/MATHEMATICAL_DESIGN_SYSTEM.md)
- [Mathematical harmony and accessibility audit](docs/MATHEMATICAL_HARMONY_AUDIT.md)
- [Production-readiness report](docs/PRODUCTION_READINESS_REPORT.md)

## Android CI

GitHub Actions installs the required Android SDK and runs unit tests, lint and a debug APK build. For a Firebase-enabled release-candidate build, configure repository secret `GOOGLE_SERVICES_JSON_BASE64` with the base64-encoded Android `google-services.json` for this application.

Release signing material must remain outside source control.

See `docs/PRODUCTION_READINESS_REPORT.md` for the complete release status and limitations. Source-level success is not a substitute for the Android CI and device gates.
