# RTC Community Production Integration Foundation — Design

## Goal

Prepare the post-PR-#6 `main` architecture for safe parallel feature integration and an eventual signed production release without touching the four concurrently owned feature domains (Marketplace, Community, Supabase hardening, Brand/Experience Customizer).

## Architectural boundary

The current post-PR-#6 structure is authoritative. `MainActivity.kt` remains a thin bootstrap. Feature code remains under `feature/*`, navigation remains under the existing `navigation/` and `ui/navigation/` layers, and this workstream is limited to build/release/CI contracts plus integration documentation.

## CI separation

Source verification must not depend on live Supabase credentials. Pull requests and normal branch pushes should always be able to run source contracts, JVM tests, lint, debug assembly and AndroidTest assembly using non-routable compile-only Supabase placeholders when non-production secrets are absent.

Real non-production credentials may still be consumed when configured, but their absence must not prevent source verification from running.

Release builds remain fail-closed. A release task must require an explicitly supplied release runtime configuration and signing configuration. CI may construct those ignored files from repository secrets, but they are never committed.

## Release runtime configuration

Add an ignored `release.runtime.properties` source for:

- `supabase.production.url`
- `supabase.production.publishableKey`

The release build should inject these values into `BuildConfig` only when a release task is requested. Missing production runtime values must fail a release task clearly while leaving debug-only tasks configurable.

## Signing boundary

A release task must require `signing.properties` with the existing four signing fields. CI should be able to reconstruct the keystore plus `signing.properties` from secrets. No keystore or password is committed.

## Firebase boundary

`google-services.json` remains ignored and is restored from `GOOGLE_SERVICES_JSON_BASE64` when available. Release-candidate assembly requires Firebase configuration together with the release runtime and signing secret bundle.

## Workflow behaviour

The Android verification workflow keeps the existing full source gate:

1. source regression contracts;
2. `testDebugUnitTest`;
3. `lintDebug`;
4. `assembleDebug`;
5. `assembleDebugAndroidTest`;
6. debug artifact upload.

For source verification, absent non-production Supabase secrets fall back to `https://rtc-ci.invalid` and a compile-only publishable placeholder for all event types. This fallback is deliberately non-routable and must never be used for connected integration tests.

The release-candidate step runs only when the complete release credential bundle is available. It reconstructs ignored runtime/signing files, validates the secret presence, runs `assembleRelease bundleRelease`, uploads release artifacts, and removes reconstructed sensitive files during cleanup.

## Structural contracts

Add source contracts that prevent regressions in the release boundary:

- compile verification remains independent of live non-production secrets;
- release runtime configuration is ignored and injected only for release tasks;
- release tasks fail closed without production runtime/signing material;
- workflow references the complete release secret bundle;
- release secrets are materialized only into ignored files;
- no service-role key is introduced into Android build configuration.

## Parallel integration constraints

This branch must not modify Marketplace, Community, Supabase migrations/functions, or Brand/Experience feature code. Later integration reviews will compare each AI branch against the then-current trunk and port capabilities rather than merge historical branches wholesale.

## Non-goals

- No feature implementation.
- No production Supabase mutation.
- No branch-protection API mutation (the connector used here exposes protection reads, not repository-rule writes).
- No deployment or Play Store publishing.
- No emulator/device execution in this workstream unless a device runner is already available.
