# Marketplace Production Optimisation Design

## Status

Approved on 2026-08-28 for implementation on `optimize/marketplace-production` from `main` commit `ff083d90496632796104f98ebeae65b9c3b97eb2`.

## Goal

Complete the resident-facing and staff-facing Marketplace feature so that discovery, search, business detail, owner workflows, reviews, media, and administration behave consistently with the live RTC Community Non-Production Supabase Marketplace contract while preserving the post-PR-#6 feature architecture and centralized authorization model.

## Non-Negotiable Boundaries

- Do not work on or merge into `main`.
- Treat `feature/marketplace-build` as historical reference only; do not merge it wholesale.
- Do not mutate RTC Community Production Supabase.
- Backend changes are limited to Marketplace-specific forward migrations required by truthful client behavior.
- Do not redesign shared authentication, role assignment, MFA, or global RPC security in this branch.
- Preserve RPC-only table access where it is intentional; do not add table policies merely to silence advisor output.
- Preserve `RtcCommunityNavGraph` and `RouteAccessPolicy` as the centralized Android navigation authorization boundary.
- Server-side Marketplace owner/content-admin/moderator assertions remain authoritative.

## Current-State Findings

### Android

- `MarketplaceScreens.kt` is a large multi-surface presentation file covering home, search, detail, reviews, owner editing, map, status, saved businesses, invitations, and administration.
- `MarketplaceViewModels.kt` combines discovery, owner, reviews, and administration state in one file.
- Search is imperative and button-driven, with no debounce, stale-request cancellation, category/rating/radius state, or bounded pagination.
- `MarketplaceDraftCheckpointStore` persists `{businessId, step}` but the owner wizard never restores the saved step.
- Business detail currently discards weekly opening-hours data returned inside location JSON.
- Business detail does not receive special-date opening-hour exceptions.
- Owner-wizard steps 2, 4, 6, and 8 are substantially descriptive placeholders instead of complete editors.
- Repository mutations create a new UUID for every invocation, so rapid repeated taps do not reuse an operation idempotency key.
- Media preparation re-encodes images correctly but must be moved to an IO dispatcher and surfaced with explicit upload state.
- Marketplace administration is not wrapped by `ProtectedRoute` even though `RouteAccessPolicy` already defines Marketplace publication/moderation access.
- Android repository contracts expose only a subset of Marketplace RPC capabilities already present in non-production.

### RTC Community Non-Production Supabase

The live non-production Marketplace contract already includes RPCs for:

- discovery/home/search/detail/map/saved businesses;
- owner draft identity, locations, hours, offerings, media, submission, status, archive;
- invitations, membership and ownership transfer;
- review create/update/delete/report, owner responses and helpful voting;
- admin queue, submission detail, assignment, request changes, publish, reject, suspend/reinstate;
- review moderation;
- featured-placement scheduling/cancellation.

Two forward backend additions are required:

1. A bounded Marketplace search page contract because the current search RPC has no cursor/offset argument.
2. Additive special-date opening-hour exceptions in the public business-detail response.

## Architectural Direction

Keep `feature/marketplace` as the sole Marketplace feature root. Decompose only where files currently carry materially distinct responsibilities.

```text
feature/marketplace/
├── data/
│   ├── local/
│   │   ├── MarketplaceDraftCheckpointStore.kt
│   │   └── MarketplaceMediaPreparation.kt
│   └── remote/
│       ├── SupabaseMarketplaceRepository.kt
│       └── MarketplaceJsonMappers.kt
├── domain/
│   ├── MarketplaceModels.kt
│   ├── MarketplaceHours.kt
│   └── MarketplaceMutation.kt
└── presentation/
    ├── MarketplaceViewModels.kt
    ├── MarketplaceHomeScreen.kt
    ├── MarketplaceSearchScreen.kt
    ├── MarketplaceBusinessScreen.kt
    ├── MarketplaceOwnerScreen.kt
    ├── MarketplaceReviewScreen.kt
    ├── MarketplaceAdminScreen.kt
    └── MarketplaceComponents.kt
```

This branch does not need a repository-wide package migration. Existing call sites may continue importing `feature.marketplace.presentation.*` while the implementation is decomposed internally.

## Search Design

Search state is ViewModel-owned and preserved through `SavedStateHandle` where practical.

A `MarketplaceSearchUiState` contains:

- query;
- selected category id;
- locality;
- radius metres;
- minimum rating;
- verified-only;
- sort mode;
- accumulated items;
- `isRefreshing`;
- `isLoadingMore`;
- `hasMore`;
- user-facing failure message.

The ViewModel processes criteria changes through an approximately 300 ms debounce and latest-request-wins cancellation. A criteria change resets the page. `loadMore()` appends only when no page request is already running and `hasMore` is true.

The forward RPC `marketplace_search_businesses_page` accepts the current filter set plus `p_offset` and bounded `p_limit`. It returns a JSON object with `items`, `nextOffset`, and `hasMore`. It retrieves `p_limit + 1` rows internally so the client never fabricates pagination state.

The existing `marketplace_search_businesses` contract remains unchanged for compatibility.

## Opening-Hours Design

Introduce domain types for weekly intervals and date exceptions. The Android business-detail mapper must deserialize existing `hours` arrays from each location and additive `hourExceptions` arrays supplied by the forward migration.

Opening-state calculation occurs in domain code and uses each location's declared timezone.

Evaluation precedence:

1. matching special-date exception;
2. weekly schedule for the local weekday;
3. overnight interval carried from the previous weekday where applicable;
4. explicit closed, 24-hour, or appointment-only state;
5. unknown data returns an unavailable state rather than claiming the business is open.

UI labels may include `Open now`, `Closed`, `Open 24 hours`, `By appointment`, `Opens HH:mm`, `Closes HH:mm`, or `Hours unavailable` only when those statements are supported by the schedule.

## Owner Workflow Design

The nine stages remain stable so existing checkpoints remain meaningful:

1. Identity and story.
2. Categories.
3. Locations and visibility.
4. Weekly hours and special-date exceptions.
5. Services/products and pricing.
6. Public contact preferences.
7. Media.
8. Verification/evidence state that the backend actually supports.
9. Preview and submit.

The ViewModel reads the persisted checkpoint and restores the last valid step for the same business. Supabase draft data remains authoritative for persisted fields. `SavedStateHandle` may retain transient form values and the selected step, but stale local values must never overwrite a newer server revision automatically.

Submission navigates to status only after authoritative success. Archive, delete, transfer, revoke, suspend, reject, and equivalent destructive actions require explicit confirmation.

## Mutation and Idempotency Design

Introduce an operation-scoped mutation key policy. One intentional mutation command receives one UUID. Retries of that same in-flight/failed command reuse the same UUID. A new intentional command receives a new UUID after the prior command is closed.

ViewModels also block concurrent duplicate submissions while a mutation is running.

Repository methods that currently create UUIDs internally accept an optional/required operation key from the caller or an internal command abstraction that can preserve identity across retries. Non-idempotency-key RPCs retain their existing signatures.

## Media Design

Keep the existing JPEG/PNG/WebP validation, 10 MB source bound, 5 MB prepared bound, 2048 px maximum dimension, and metadata-stripping re-encode behavior.

Run decode/resize/re-encode on `Dispatchers.IO`.

Owner media state is per item: preparing, uploading, finalizing, succeeded, failed. Failed items expose retry. Draft media deletion uses the existing RPC. Rendered Marketplace media uses authenticated private-storage resolution and Coil; raw object paths are never presented as user-facing URLs.

## Reviews Design

Keep reviews separate from discovery state. Surface existing backend behaviors where appropriate:

- create/update own review;
- delete own review with confirmation;
- structured report reason/details;
- helpful vote;
- owner response when the owner contract permits it;
- refresh only the review slice affected by a successful mutation.

## Administration Design

Wrap publication/admin Marketplace routes in `ProtectedRoute` so Android fails closed consistently with the rest of the staff workspace. Do not add local `isAdmin` booleans.

Publication administration consumes the existing admin queue and submission-detail RPC before consequential decisions. The UI may expose assign, request changes, approve/publish, reject, suspend, and reinstate according to the contract and current route authority.

Review moderation remains a Moderator/System Administrator surface according to `RouteAccessPolicy` and the backend moderator assertion.

## Backend Migration Design

Create forward-only Marketplace migrations in `supabase/migrations` that:

- add `marketplace_search_businesses_page` without removing/changing the legacy search RPC;
- extend `marketplace_business_detail` additively with special-date `hourExceptions` per location;
- add only indexes justified by the actual Marketplace query predicates/orderings introduced or exercised by those functions;
- preserve function `search_path` hardening and existing internal authorization assertions;
- grant `EXECUTE` only to the same intended application role as the corresponding existing Marketplace public API.

Do not deploy to production from this branch.

## Security Handoff

Create `docs/MARKETPLACE_SECURITY_HANDOFF.md` documenting:

- RPCs called from Android;
- which public RPCs are `SECURITY DEFINER`;
- their internal owner/content-admin/moderator assertions;
- Storage bucket/policy assumptions;
- intentionally RPC-only Marketplace tables;
- any advisor warning that requires review by the dedicated security workstream rather than modification here.

## Verification

Required focused tests:

- opening-hours domain evaluation, including overnight and special-date behavior;
- search debounce/latest-request behavior and page append/reset semantics;
- draft checkpoint restoration;
- idempotency-key reuse for retries;
- repository JSON mapping for additive hours/exceptions/page envelopes;
- privileged Marketplace route policy coverage;
- Compose smoke coverage for Home, Search, Business Detail, Business Editor, and Admin Queue where the project test harness supports it.

Final project gates, when a runnable Android environment is available:

```bash
python3 tools/tests/run_contract_tests.py
gradle --no-daemon testDebugUnitTest
gradle --no-daemon lintDebug
gradle --no-daemon assembleDebug
gradle --no-daemon assembleDebugAndroidTest
```

Instrumentation is reported only if actually executed on an emulator/device.

## Completion Criteria

The branch is ready for review when:

- no work is committed directly to `main`;
- resident search is cancellable, filterable, and bounded/paginated;
- detail truthfully renders media and opening-hours state;
- owner checkpoint resume and all nine workflow stages are functional to the extent supported by backend contracts;
- repeated mutation taps cannot create concurrent duplicate commands;
- privileged Marketplace routes use centralized route gating;
- Marketplace-specific forward migrations are committed but production remains untouched;
- focused tests and available build gates are green, or any environment-only verification limitation is explicitly documented;
- the security handoff is present.