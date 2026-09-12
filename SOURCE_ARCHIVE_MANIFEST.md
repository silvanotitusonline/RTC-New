# RTC Community 1.0.5 Complete Source Snapshot

Snapshot date: 2026-08-26

This archive contains the latest RTC Community 1.0.5 Concept 6 Android release-candidate source available in this workspace, plus a refreshed source snapshot of all six active RTC Community Production Supabase Edge Functions.

Included source areas:

- Native Android application source (Kotlin / Jetpack Compose)
- Gradle build configuration and Android CI workflow
- Concept 6 production-readiness documentation
- Supabase SQL migrations
- All six active production Edge Function source directories
- Source regression contracts and test runner

Sensitive runtime material is intentionally excluded. The archive does not contain service-role secrets, Firebase service-account private keys, Android signing keystores, signing passwords, or `google-services.json`.

Validation performed before packaging:

- `tools/tests/run_contract_tests.py`: 29/29 passed
- all six active production Edge Function source directories present
- no Android binary build is claimed by this manifest; Android compile/lint/APK/AAB remain CI/device verification gates
