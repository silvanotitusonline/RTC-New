# RTC Community Production Integration Foundation Report

Date: 2026-08-28

## 1. Purpose

This branch prepares the post-PR-#6 Android trunk for safe parallel feature integration and a future signed production release without modifying any of the four concurrently owned feature workstreams:

- Marketplace optimisation;
- Community optimisation;
- Supabase security hardening;
- Brand & Experience Customizer optimisation.

The work is intentionally limited to CI, release runtime/signing boundaries, source contracts, and integration documentation.

## 2. Branch boundary

- Repository: `silvanotitusonline/RTC-Community-Production`
- Base branch: `main`
- Starting `main` SHA: `ff083d90496632796104f98ebeae65b9c3b97eb2`
- Working branch: `hardening/production-integration-foundation`
- Verified implementation SHA before this report: `9c3fa039a2325f4f5c902031f816968a770c6650`
- Draft PR: #9 — `build: harden production integration foundation`

`main` was not modified by this workstream.

## 3. Problem addressed

The first post-PR-#6 `main` workflow could fail before source contracts or Android tests ran when the repository did not contain live non-production Supabase secrets. That coupled compile/source verification to connected runtime credentials and made a structurally valid trunk appear broken for configuration reasons.

At the same time, the release build type did not yet have an explicit production Supabase runtime injection boundary.

## 4. Implemented CI separation

Normal source/debug verification no longer requires live Supabase credentials.

The workflow now:

1. consumes `RTC_NONPROD_SUPABASE_URL` and `RTC_NONPROD_SUPABASE_PUBLISHABLE_KEY` when configured;
2. otherwise writes a deliberately non-routable compile-only debug configuration:
   - `https://rtc-ci.invalid`
   - `sb_publishable_ci_placeholder`;
3. proceeds through source contracts, JVM tests, lint, debug APK assembly, and AndroidTest assembly.

This placeholder is only for compile/source verification. It is not a connected-integration-test credential and must never be treated as one.

## 5. Release runtime boundary

`app/build.gradle.kts` now distinguishes debug and release Supabase configuration.

Release tasks use:

- `supabase.production.url` / `RTC_PROD_SUPABASE_URL`;
- `supabase.production.publishableKey` / `RTC_PROD_SUPABASE_PUBLISHABLE_KEY`.

An ignored `release.runtime.properties` file is supported for local controlled release builds.

When a Gradle task containing `Release` is requested, required production runtime values are validated. Debug-only configuration does not require them.

No Supabase service-role key or other privileged server credential was introduced into the Android application.

## 6. Release signing boundary

Local controlled builds may continue to use ignored `signing.properties`.

CI supports masked environment variables:

- `RTC_ANDROID_KEYSTORE_PATH`;
- `RTC_ANDROID_KEYSTORE_PASSWORD`;
- `RTC_ANDROID_KEY_ALIAS`;
- `RTC_ANDROID_KEY_PASSWORD`.

A requested release task fails closed unless the complete signing configuration is available.

GitHub Actions decodes the keystore only into a temporary ignored `rtc-ci-release.keystore`; signing passwords are not serialized into a new CI signing-properties file.

## 7. Complete CI release secret bundle

A signed release-candidate build requires all of:

1. `RTC_PROD_SUPABASE_URL`
2. `RTC_PROD_SUPABASE_PUBLISHABLE_KEY`
3. `GOOGLE_SERVICES_JSON_BASE64`
4. `RTC_ANDROID_KEYSTORE_BASE64`
5. `RTC_ANDROID_KEYSTORE_PASSWORD`
6. `RTC_ANDROID_KEY_ALIAS`
7. `RTC_ANDROID_KEY_PASSWORD`

If any are absent, source/debug verification remains green-capable while the release-candidate assembly step exits successfully without pretending a release was produced.

When the full bundle exists, CI runs:

```text
gradle --no-daemon --stacktrace assembleRelease bundleRelease
```

## 8. Credential cleanup

The workflow always cleans reconstructed material:

- `runtime.local.properties`;
- `release.runtime.properties`;
- `signing.properties` if present;
- `rtc-ci-release.keystore`;
- `app/google-services.json`.

The corresponding runtime/signing/Firebase files remain excluded from source control.

## 9. Test-first evidence

A production-integration source contract was added before the implementation.

The first PR-triggered contract run on the contract-only state failed at the new release-boundary contracts, confirming the red phase before implementation:

- workflow run #126;
- run ID `33150930321`;
- expected failure at source regression contracts.

After implementation, the same contracts pass.

## 10. Verified implementation gate

GitHub Actions workflow:

- name: `Android Production Verification`
- run number: #139
- run ID: `33151098850`
- job ID: `98783019775`
- verified branch SHA: `9c3fa039a2325f4f5c902031f816968a770c6650`
- conclusion: **success**

Results:

- Source regression contracts: **96/96 passed**
- `testDebugUnitTest`: **passed**
- `lintDebug`: **passed**
- `assembleDebug`: **passed**
- `assembleDebugAndroidTest`: **passed**
- Debug verification artifact upload: **passed**
- Credential cleanup: **passed**

The Android instrumentation test APK was compiled and packaged; this run did not execute the instrumentation suite on an emulator/device.

## 11. Debug verification artifact

Run #139 produced:

- Artifact name: `rtc-community-debug-verification`
- Artifact ID: `9677919715`
- Size: `39,458,082` bytes
- SHA-256: `22e01c5b5a86a5d49486cb2dafe983af4d53569ac0b8d039598a18526d470c23`
- Created: `2026-08-28T07:29:20Z`
- Expires: `2026-11-26T07:20:32Z`

The artifact contains the debug application APK, Android test APK, lint report, and JVM unit-test report selected by the workflow.

## 12. Release-candidate status

No release APK/AAB was produced in run #139.

The release step explicitly reported that all seven release secrets listed in section 7 were absent, so release assembly was skipped. The release-artifact upload found no APK/AAB and uploaded nothing.

This is intentional. It prevents an unsigned, unconfigured, or incorrectly connected artifact from being represented as a production release candidate.

## 13. Feature-domain isolation

This implementation does not modify:

- `feature/marketplace/**`;
- `feature/community/**`;
- Supabase migrations/functions;
- Brand & Experience feature implementation;
- `MainActivity.kt`;
- navigation implementation.

The post-PR-#6 feature architecture remains authoritative.

## 14. Compatibility with parallel AI workstreams

### Supabase Security AI

Can change server-side RLS/RPC/Edge authorization independently. It must not add service-role credentials to Android. Any Android-visible publishable-key/runtime changes should conform to the release boundary established here.

### Marketplace AI

Can work inside the canonical Marketplace `data/domain/presentation` architecture without touching CI/release files unless a genuinely required build dependency appears. Its final branch should be reviewed against the then-current trunk rather than merged from its historical donor branch.

### Community AI

Can semantically port the useful donor behavior into canonical `feature/community/`. It should retain the source/debug verification gates established here and avoid changing release credential handling.

### Brand & Experience AI

May require manifest/theme/configuration changes and therefore has the largest eventual integration surface. It must preserve the single-Activity architecture and must not move production runtime secrets into application source.

## 15. Remaining repository-settings action

This branch does not change GitHub branch protection or repository rulesets. Those are repository settings rather than source files, and no compatible write action is available through the current GitHub connector.

Before final production promotion, `main` should be protected with a ruleset requiring pull requests and the Android verification status, while blocking force pushes and branch deletion as appropriate.

## 16. Existing warnings not introduced by this branch

The successful Android compile still reports existing Kotlin/Compose deprecation warnings, including annotation-target future-behavior warnings and some deprecated non-auto-mirrored Material icons. These are not release-pipeline failures and should be handled during the relevant feature/domain optimisation passes rather than mixed into this isolated build/release change.

GitHub Actions also reports that several current action major versions target Node.js 20 and are being forced onto Node.js 24. This should be addressed in a later CI dependency-maintenance pass (for example, migrating to supported newer action majors after compatibility review) rather than changing multiple infrastructure variables in this release-boundary patch.

## 17. Production backend status

No Supabase production or non-production database mutation was performed by this workstream.

No Edge Function was deployed.

No production credentials were created, copied into source, or exposed.

## 18. Merge recommendation

The CI/release-foundation change is suitable for integration **after the report-only branch head receives the same green Android verification gate**.

Keep PR #9 draft until that latest-head check is confirmed.

After integration, the preferred feature-integration order remains:

1. Supabase Security Hardening;
2. Marketplace Optimisation;
3. Community Optimisation;
4. Brand & Experience Customizer.

Each feature branch should first be rebased/recreated from the then-current trunk as necessary, independently audited, and semantically integrated rather than blindly merged from historical donor architecture.
