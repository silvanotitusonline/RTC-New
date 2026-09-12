# Community Production Integration — Final Report

## Integration boundary

Community was integrated onto the Marketplace-enabled, Security-hardened `main` rather than merging stale donor history.

- Base `main`: `147b0ea86df65369bf6231ab72baadf6110a0336`
- Donor PR: #10 (`optimize/community-production`)
- Donor checkpoint: `e14370125d2712e7c4ce9d59e0ef34eba4178641`
- Clean integration branch: `integration/community-production-v2`
- Implementation head before this report: `7b2d3986cde668cc57bcb86c358d47735aeea946`

The donor branch was used as a semantic source. It was not merged wholesale.

## Integration commits

1. `97a7000672a32a79e58027f9657a16580a3d90fb` — scoped Community production architecture, repository, tests, media preparation, DI, smoke test, design token, migration and Community-specific contracts.
2. `bf863e8a8302f834d96976769e39f4f19586d570` — reconciled current-main navigation so Community feed/detail ownership moves to the scoped feature while Marketplace protected routes remain intact.
3. `7b2d3986cde668cc57bcb86c358d47735aeea946` — reconciled four stale release-regression assertions to the scoped Community architecture without removing Marketplace/Security assertions.

## Architecture outcome

- Community feed/detail state has one scoped owner through `CommunityViewModel -> CommunityRepository -> SupabaseCommunityRepository`.
- Root `RtcViewModel` no longer owns Community feed/detail pagination, reactions, comments, or signed-media refresh orchestration through navigation.
- Root Community post projection remains available where Explore needs cross-surface Community content; this is not feed/detail ownership.
- Feed pagination is bounded and composite-keyset based.
- Reactions use optimistic UI state but reconcile to authoritative server results with rollback/error handling.
- Comment create/update/delete and detail refresh are feature-scoped.
- Signed media URL caching, invalidation and recovery are feature-scoped.
- Composer/upload recovery remains authenticated-owner scoped and file preparation is kept off the UI dispatcher.
- Marketplace route families, Marketplace administration, Security authorization boundaries and CI/release hardening already on `main` were preserved.

## Non-production Supabase verification

Project: RTC Community Non-Production (`eqwstpdjoineycrkhpht`).

Applied the forward migration `community_feed_page_v2` only to non-production. Production Supabase was not changed.

Installed function:

`public.community_post_page_v2(timestamptz, uuid, integer)`

Verified metadata:

- `SECURITY INVOKER` (`prosecdef = false`)
- fixed `search_path = public, pg_temp`
- `anon` EXECUTE: false
- `authenticated` EXECUTE: true
- supporting partial cursor index `community_posts_public_cursor_idx` exists

Authenticated runtime checks:

- an existing authenticated non-production identity could read the first page through RLS;
- a rollback-only synthetic scenario inserted five published posts and verified page 1 count = 3;
- page 2 returned the expected following window;
- page 1 and page 2 had zero overlapping IDs;
- a requested limit of 500 remained bounded to at most 50;
- the transaction was rolled back and a follow-up check confirmed zero synthetic rows remained.

Anonymous denial:

- execution under role `anon` failed with PostgreSQL `42501 permission denied for function community_post_page_v2`, as designed.

Post-DDL advisors:

- no new Community security finding was introduced;
- no missing-index warning was introduced by the Community migration;
- the newly created cursor index appears as unused immediately after creation, which is expected before workload statistics accumulate;
- other Marketplace/UI-configuration advisor findings predate and are outside this Community integration.

## Implementation-head verification

Android Production Verification run #332 / run ID `33166918903` on exact implementation head `7b2d3986cde668cc57bcb86c358d47735aeea946` completed successfully.

- Source regression contracts: 128/128 passed
- Shared Edge authorization tests: 14 passed, 0 failed
- `testDebugUnitTest`: BUILD SUCCESSFUL
- `lintDebug`: BUILD SUCCESSFUL
- `assembleDebug`: BUILD SUCCESSFUL
- `assembleDebugAndroidTest`: BUILD SUCCESSFUL
- Debug verification artifact upload: passed
- Credential cleanup: passed

Artifact:

- name: `rtc-community-debug-verification`
- artifact ID: `9684097379`
- size: `39,763,561` bytes
- SHA-256: `19cf5059af6b8fe4e29a2bbb3def16b2637f1519ba24c6affeb8a9770f7cf8b0`

## Verification limitations

- `assembleDebugAndroidTest` compiles/packages instrumentation and Compose smoke tests; this report does not claim emulator or physical-device execution.
- GitHub Actions does not currently have the complete seven-value production runtime/Firebase/signing secret bundle, so signed `assembleRelease`/`bundleRelease` was skipped by the fail-closed workflow and no release APK/AAB is claimed.
- No production Supabase migration/deployment was performed.

## Merge gate

This report commit changes documentation only. Before merge, the exact report head must pass the complete Android Production Verification workflow again. The PR may be marked ready and merged only if:

1. the report head is exactly the head that passed the final workflow;
2. `main` has not moved unexpectedly;
3. PR #15 remains mergeable;
4. no unresolved review threads exist.

After merge, the resulting `main` push workflow must also pass before the Community integration is considered closed.
