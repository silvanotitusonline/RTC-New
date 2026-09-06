# RTC Community Production + Concept 6 Design

## Goal
Migrate RTC Community 1.0.5 to the Concept 6 Community Engagement Feed design while preserving existing navigation, roles, Supabase contracts, MFA, audit semantics, and business logic, and close remaining production-readiness defects.

## Architecture
Keep `RtcViewModel`, `RtcRepository`, `ProductionUxRepository`, Supabase, Hilt, Navigation Compose and role/MFA boundaries. Add focused bootstrap, authorization, local persistence, media, notification and reusable UI component files instead of moving backend calls into composables. Resident primary navigation remains Home / Community / Explore / Support; Account remains secondary.

## Backend
Production remains canonical. Non-Production is the validation environment. Use forward migrations only. Community feed exposes real engagement and follow semantics. Support uses guarded RPCs. Security-definer functions remain callable only when intentional and internally authorized. Leaked-password protection is an accepted subscription limitation and is not a release blocker for this iteration.

## UI
Use semantic deep-ink/graphite surfaces, emerald interaction accents, civic gold only for protected/admin emphasis, red only for danger, 48dp targets, responsive typography, explicit empty/loading/error states and shared native Compose components. Reference PNGs are visual specifications only.

## Reliability
No seeded production cases/work items, no report-count Trending, no fake Following, no arbitrary-size video `readBytes()`, durable drafts/outbox via Room/DataStore/WorkManager, lifecycle-aware Media3 playback, signed-URL renewal, FCM token rotation and cold/warm-start routing.

## Verification
Use unit/source-contract tests first, pure Kotlin compilation where possible, Supabase live contract checks, static route/security checks, and Gradle/Android build gates when SDK/tooling is available. Never claim a passed Android build without executing it.
