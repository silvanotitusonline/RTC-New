# RTC Community — Current System Overview

**Application ID:** `za.org.rtc.community`  
**Client:** Jetpack Compose (Concept 6 mathematical design system)  
**Backend:** Supabase (Postgres + Auth + Storage + Edge Functions)  
**Last overview refresh:** 17 September 2026

## Product surfaces

- **Resident:** Home, Community feed, Explore (Daily Post + directory), Support cases, Account / privacy centre.
- **Staff / protected:** Work Queue, My Work, Staff Alerts, Content Management, Moderation, RTC AI, Access Management, Operational Controls, Privacy Analytics, System Health, Administrative Activity.
- **Marketplace:** Business listings, offerings, reviews, claims, verifications, featured placements.
- **Civic reports:** Public reporting + evidence + admin verification workflow.
- **Official notices & community alerts.**

## Trust boundaries

1. **UI visibility is not the security boundary.** Protected destinations are fail-closed on the client and enforced again by role-gated SECURITY DEFINER RPCs and Edge Functions.
2. **Roles** (`app_role`): `RESIDENT`, `CASE_STAFF`, `CONTENT_EDITOR`, `MODERATOR`, `EVIDENCE_REVIEWER`, `SYSTEM_ADMIN`.
3. **MFA / AAL** gates System Administrator actions.
4. **Direct table access** is denied for sensitive domains; clients call RPCs or Edge Functions.

## Data access patterns

| Domain | Client path | Notes |
|--------|-------------|-------|
| Community feed / posts | RPCs + RLS | Public published content readable; drafts owner-only |
| Messaging | RLS on conversations / messages / members | Participant-scoped |
| Notifications | RLS own-rows + Edge dispatch | Inserts from service role / Edge only |
| Civic reports | Public read RPCs + auth mutation RPCs | Evidence via private storage |
| Marketplace | Auth RPCs + storage policies | Replay-hardened mutations |
| Support cases | Auth RPCs only | No direct client CRUD |
| Privacy export / deletion | Edge Functions + request tables | Worker processes under service role |

## Edge Functions (active)

See `docs/EDGE_FUNCTION_JWT_POLICY.md` for JWT policy and retired payment stubs.

Key functions: FCM dispatch, community alert dispatch, media signed URLs, community post reporting, admin AI, privacy request intake + worker, Daily Post scheduler / language, civic-report media URLs.

## Design system

Concept 6 centralises spatial tokens (Fibonacci), tempered typography, Golden-ratio media framing, density modes, and compact/medium/expanded breakpoints. Physical `dp` literals are forbidden outside `RtcDesignTokens.kt`.

## Source contracts

38 source-level regression contracts (`python3 tools/tests/run_contract_tests.py`) cover navigation, protected routes, media, MFA lifecycle, AI path, design-system mathematics, and compile compatibility.

## Production readiness posture

Source/backend release-candidate. Binary GO still requires GitHub Actions Android CI (unit + lint + assemble) and device smoke tests. See `docs/PRODUCTION_READINESS_REPORT.md`.
