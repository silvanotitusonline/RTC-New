# RTC Community — Final Production Readiness Plan

**Date:** 2026-08-29  
**Base:** `main` at `85856ec711af84280dec8e3b76bee0b2938193f2`  
**Working branch:** `release/final-production-readiness`  
**Purpose:** complete cross-cutting application QA, release engineering, repository hygiene, and final GO / NO-GO evidence after the verified Brand, Community, Marketplace, Supabase Security, and Service Centre integrations.

## Non-negotiable boundaries

1. Do not merge historical donor PRs or stale feature branches into `main`.
2. Do not deploy or mutate RTC Community Production Supabase without a separate explicit production-promotion authorization.
3. Preserve the single-Activity Android architecture and the resident primary destinations: Home / Community / Explore / Support.
4. Preserve centralized fail-closed route authorization and backend-authoritative authorization.
5. Do not weaken RLS, SECURITY DEFINER search-path controls, Edge Function authentication, release signing, Firebase, or CI secret boundaries to make tests pass.
6. Do not claim emulator/device execution when only AndroidTest compilation has occurred.
7. Every merge candidate must be verified on its exact head and again on the resulting `main` merge commit.

## Phase 0 — Freeze and baseline

- Confirm the branch starts exactly from verified post-Service-Centre `main`.
- Record current open/superseded PRs and active branches.
- Run the full existing Android Production Verification workflow on the final-readiness branch before implementation.
- Add final-readiness source contracts that intentionally expose remaining cross-cutting architecture/release debt.

## Phase 1 — Root application coordinator decomposition

The current `app/RtcViewModel.kt` remains the principal cross-cutting architecture debt. It owns or forwards authentication, MFA, account/profile, access management, privacy analytics, operations, editorial/moderation, resident discovery, Community mutation UI state, alerts, Support, feedback, drafts, AI, notification/deep-link state, and Firebase token registration.

Target structure:

- keep `RtcViewModel` as the compatibility/root session facade required by the configured application root;
- extract cohesive internal coordinators for authentication/account, administration/operations, and resident/support/community cross-cutting actions;
- move reusable safe error classification out of the root ViewModel;
- keep feature-owned Marketplace, Community, Brand, and Service Centre ViewModels/repositories authoritative;
- reduce root ViewModel implementation size and remove duplicated error-state plumbing without changing public screen behaviour.

Acceptance:

- root ViewModel has a bounded structural budget;
- no feature-specific business repository is moved into the root;
- existing call sites remain source-compatible unless a scoped migration is explicitly required;
- JVM/source contracts/Android lint/debug/AndroidTest compilation remain green.

## Phase 2 — Navigation and authorization single-source audit

Audit:

- `navigation/RtcRoute*` definitions;
- `navigation/RouteAccessPolicy.kt`;
- `ui/navigation/ProtectedRoute.kt`;
- `ui/navigation/RtcCommunityNavGraph.kt`;
- notification/deep-link route entry points;
- Brand, Marketplace, Community, Service Centre route families.

Acceptance:

- every protected route has one authoritative access-policy classification;
- unknown or malformed protected routes fail closed;
- no UI-only role check is treated as backend authorization;
- authority/MFA loss returns to a safe route;
- deep links cannot bypass route policy.

## Phase 3 — Safe-error and resilience convergence

- centralize user-safe error classification for authentication, media, Community, Support, Marketplace, Service Centre, administration, and connectivity failures;
- prevent raw backend/SQL/function/internal messages from becoming resident-facing UI text;
- preserve actionable distinctions such as authorization, validation, offline, timeout, and retryable service failure;
- verify loading/error/retry state cannot become permanently stuck after coroutine failure.

## Phase 4 — Authentication and account lifecycle verification

Verify end-to-end source/runtime contracts for:

- saved-session restoration;
- email sign-in/sign-up;
- Google ID-token sign-in;
- email confirmation state;
- password recovery/update;
- sign-out;
- administrator TOTP enrollment and AAL2 verification;
- immediate role/session revocation behaviour;
- profile/bootstrap/locality/preferences persistence;
- FCM token registration remaining optional/non-blocking.

## Phase 5 — Resident feature reliability

Audit Home, Community, Explore, Support, Account, Alerts, Marketplace, and Service Centre for:

- deterministic loading/empty/error/retry states;
- pagination generation protection and de-duplication;
- pull-to-refresh/retry semantics;
- destructive-action confirmation;
- media upload/playback recovery;
- deep-link cold/warm start handling;
- state restoration after process/lifecycle changes where supported.

## Phase 6 — Offline and reconnection audit

- verify local preference/draft/upload persistence;
- verify pending upload/sync indicators are truthful;
- ensure transactional operations are not falsely represented as offline-complete;
- verify uncertain network outcomes use idempotent/replay-safe server contracts where available;
- verify reconnection refresh does not duplicate state or replay privileged mutations.

## Phase 7 — Performance and structural pass

- inspect Compose recomposition/state collection hot paths;
- bound expensive list/media work and avoid UI-thread file processing;
- verify image/video sizing and signed URL caches remain bounded;
- inspect repository/viewmodel/source-file budgets;
- confirm release minification/resource shrinking and dependency hygiene;
- review database advisor/index findings only against observed workload and canonical migration history.

## Phase 8 — Repository and governance cleanup

Superseded historical PRs must be closed with clear disposition notes rather than merged:

- PR #18 Service Centre donor;
- PR #14 temporary Community reconciliation;
- PR #11 Marketplace donor;
- PR #8 Supabase Security donor;
- PR #7 Brand donor;
- PR #4 historical Supabase hardening donor;
- PR #3 historical Brand donor;
- PR #1 only after confirming its useful design-system behaviour is already represented on current `main`.

Also:

- inventory stale branches and retain only branches with audit/recovery value;
- inspect `main` branch protection/rulesets;
- require current Android Production Verification as a merge gate where repository controls permit.

## Phase 9 — Release configuration and device gate

A signed binary GO requires the controlled production configuration bundle:

- production Supabase URL and publishable key;
- Firebase Android configuration;
- release keystore and passwords/alias;
- any required production payment/service configuration outside Android source control.

Then require:

- signed APK/AAB assembly;
- install/launch on an emulator or physical device;
- authentication smoke;
- Home / Community / Explore / Support navigation;
- Marketplace and Service Centre booking flow smoke;
- notification/deep-link smoke;
- media upload/playback smoke;
- administrator MFA/protected-route smoke;
- TalkBack, 200% font scale, dark/light/system theme, portrait/landscape and compact/expanded layout checks.

## Phase 10 — Production Supabase promotion gate

Production promotion is a separately authorized deployment phase. Before any mutation:

1. compare canonical repository migrations with Production and Non-Production histories;
2. calculate the exact forward-only Production delta;
3. review RLS, grants, SECURITY DEFINER, fixed search paths, functions, triggers, storage policies, scheduler/Vault chains, and Edge Function authentication;
4. verify Service Centre payment/Yoco configuration and webhook authority;
5. rehearse the approved delta against Non-Production;
6. document rollback/forward-fix strategy;
7. obtain explicit owner approval for Production deployment;
8. deploy only the approved forward delta;
9. rerun Production read-only security/runtime checks.

## Final decision

Produce a final report with one of:

- **GO** — signed binary, device QA, repository controls, and explicitly authorized Production backend promotion are all verified;
- **CONDITIONAL GO** — source/CI/backend preparation is green but named external configuration or device/deployment gates remain;
- **NO-GO** — a security, data-integrity, build, runtime, or release-control blocker remains.

The report must separate verified evidence from configuration-gated or not-yet-executed claims.