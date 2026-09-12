# Resident Information Architecture Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver verified-only Public Report metrics and scopes, a safe Home composer handoff, simplified Community and Explore roots, and a Market tab that opens the Marketplace directly.

**Architecture:** Keep the canonical `SupabasePublicReportRepository` and all v1 mutation/detail RPCs. Add narrowly scoped v2 read RPCs backed by the existing safe public projection, thread a typed `PublicReportScope` through repository/state/navigation, and compose the existing feature screens into the revised resident information architecture. Deploy the additive migration before promoting the client, and preserve compatibility routes for older APKs and deep links.

**Tech Stack:** Kotlin, Jetpack Compose, Hilt, AndroidX Navigation, Supabase Postgres/RPC, pgTAP, Python contract tests, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-08-31-resident-information-architecture-design.md`

**Execution note (product-owner direction, 2026-08-31):** The product owner has taken responsibility for all tests and instructed the implementation agent not to add or run further tests. The verification steps below remain an explicit owner handoff checklist; they must stay unchecked until the owner supplies exact-head evidence. This exception does not convert unverified work into a production pass.

## Global Constraints

- Work from exact PR #33 head `8c84c3c25b4590f9f4d3150c15a3b37e9baf6794`; do not merge PR #29's duplicate repository.
- Preserve `SupabasePublicReportRepository`, `PublicReportRpcContract`, auth ownership, evidence privacy, admin verification, and v1 mutation RPCs.
- Preserve the planned red-green-refactor coverage as the owner's verification checklist; do not mark any test green without exact-head evidence.
- Never expose reporter identity, exact private location, storage paths, service-role credentials, or backend error details.
- Do not reinterpret v1 dashboard/status semantics; v2 is additive for old-client compatibility.
- Keep each implementation commit reviewable; record all automated checks as owner-run under the execution note above.
- A gate may be resolved, but it may not be deleted, bypassed, weakened, or reported as passed without evidence.
- Merge only exact, reviewed heads in dependency order: PR #31, PR #33, then this feature PR.

---

## Task 1: Add verified-only Supabase read contracts

**Files:**
- Create: `supabase/migrations/20260831220000_verified_public_report_reads_v2.sql`
- Modify: `supabase/tests/civic_reports_rpc_test.sql`
- Modify: `supabase/tests/security_definer_classification.sql`

- [ ] Add failing pgTAP cases with fixtures for verified, unverified, expired, active, completed, closed, and unresolved reports. Assert the literal dashboard result: total verified, active `IN_PROGRESS`, resolved `COMPLETED|CLOSED`, and unresolved everything else.
- [ ] Add failing cases for `VERIFIED`, `ACTIVE`, `RESOLVED`, and `UNRESOLVED` page scopes; assert unverified/expired rows never appear, cursor pages do not overlap, invalid scope fails closed, and grants are limited to `anon` and `authenticated`.
- [ ] Run `supabase test db` in the isolated Supabase workdir and confirm failures are caused by missing v2 functions.
- [ ] Implement `civic_report_dashboard_v2()` as a count-only stable read over `civic_reports_public` and the existing 24-month visibility rule.
- [ ] Implement `civic_report_page_v2(...) returns setof public.civic_reports_public` with bounded limit 50, deterministic `(created_at, id)` cursor behavior, typed scope validation, urgency/category/sort filters, revoked default execution, and explicit intended grants.
- [ ] Register the functions in the security-definer classification if required by the repository policy, then rerun `supabase test db` to green.
- [ ] Run `python3 tools/tests/run_contract_tests.py`.
- [ ] Commit: `feat: add verified public report read RPCs`.

## Task 2: Thread typed scopes through the Android data boundary

**Files:**
- Modify: `app/src/main/java/za/org/rtc/community/feature/publicreports/domain/PublicReportModels.kt`
- Modify: `app/src/main/java/za/org/rtc/community/feature/publicreports/domain/PublicReportFilters.kt`
- Modify: `app/src/main/java/za/org/rtc/community/feature/publicreports/domain/PublicReportRepository.kt`
- Modify: `app/src/main/java/za/org/rtc/community/feature/publicreports/data/PublicReportRpcContract.kt`
- Modify: `app/src/main/java/za/org/rtc/community/feature/publicreports/data/PublicReportJsonMappers.kt`
- Modify: `app/src/main/java/za/org/rtc/community/feature/publicreports/data/SupabasePublicReportRepository.kt`
- Modify: `app/src/test/java/za/org/rtc/community/feature/publicreports/data/PublicReportRpcContractTest.kt`
- Modify: `app/src/test/java/za/org/rtc/community/feature/publicreports/data/PublicReportJsonMappersTest.kt`
- Modify: `app/src/test/java/za/org/rtc/community/feature/publicreports/presentation/PublicReportFeedReducerTest.kt`

- [ ] Add failing RPC-contract tests asserting `civic_report_dashboard_v2`, `civic_report_page_v2`, the four exact scope wire values, both cursor keys, and max page size 50.
- [ ] Add failing mapper tests using literal JSON for `verified_reports`, `active_reports`, `resolved_reports`, and `unresolved_reports`; assert malformed or missing required counts fail safely.
- [ ] Add failing reducer tests proving a scope change clears prior rows, increments generation, and defaults to `VERIFIED`.
- [ ] Run the focused JVM tests in CI-capable Android tooling and observe the expected failures.
- [ ] Add `PublicReportScope { VERIFIED, ACTIVE, RESOLVED, UNRESOLVED }`, replace the client dashboard model with the four approved counts, and make `PublicReportFilters.scope` default to `VERIFIED`.
- [ ] Extend the existing repository/RPC implementation to call only the v2 read functions while retaining all v1 create/detail/vote/comment/evidence/admin calls.
- [ ] Rerun focused tests and `python3 tools/tests/run_contract_tests.py` to green.
- [ ] Commit: `feat: model verified public report scopes`.

## Task 3: Make scoped Community routes type-safe and backward compatible

**Files:**
- Modify: `app/src/main/java/za/org/rtc/community/navigation/ResidentModernisationRoutes.kt`
- Modify: `app/src/test/java/za/org/rtc/community/navigation/ResidentModernisationRoutesTest.kt`

- [ ] Add failing route tests for each scope round trip, default `VERIFIED`, malformed scope fail-closed behavior, and legacy `bucket` links mapping safely without allowing unverified rows.
- [ ] Run the focused route test and confirm the failure is the absent scope contract.
- [ ] Add a `scope` query parameter and parser/builder mapping. Keep legacy `bucket` parsing as a compatibility input, but normalize report destinations to a verified-only scope.
- [ ] Rerun the focused tests and full Python contracts.
- [ ] Commit: `feat: add verified report scope routes`.

## Task 4: Replace Home metrics and add safe composer handoff

**Files:**
- Create: `app/src/main/java/za/org/rtc/community/feature/home/HomePublicReportState.kt`
- Create: `app/src/main/java/za/org/rtc/community/feature/home/HomePublicReportViewModel.kt`
- Create: `app/src/test/java/za/org/rtc/community/feature/home/HomePublicReportStateTest.kt`
- Create: `app/src/main/java/za/org/rtc/community/ui/navigation/ResidentComposerPrefill.kt`
- Create: `app/src/test/java/za/org/rtc/community/ui/navigation/ResidentComposerPrefillTest.kt`
- Modify: `app/src/main/java/za/org/rtc/community/ui/home/HomeRouteContract.kt`
- Modify: `app/src/main/java/za/org/rtc/community/feature/home/HomeScreen.kt`
- Modify: `app/src/main/java/za/org/rtc/community/feature/home/HomeResidentModernisationCards.kt`
- Modify: `app/src/main/java/za/org/rtc/community/ui/navigation/RtcCommunityNavGraph.kt`
- Modify: `app/src/main/java/za/org/rtc/community/feature/publicreports/presentation/PublicReportComposerViewModel.kt`
- Modify: `app/src/test/java/za/org/rtc/community/feature/publicreports/presentation/PublicReportComposerStateTest.kt`

- [ ] Add failing state tests for loading, success, retained-data refresh failure, retry, and exact scope-to-count mapping.
- [ ] Add failing pure prefill tests proving blank text is ignored, a draft is consumed once, and existing nonblank composer text is never overwritten.
- [ ] Add failing composer tests proving Home text seeds only `What’s happening?` and does not bypass required category, urgency, location/evidence decision, identity, or authentication validation.
- [ ] Run focused tests and verify the intended failures.
- [ ] Implement a Hilt Home view model backed by `PublicReportRepository.dashboard()` and expose the four snapshot states.
- [ ] Replace the old project/service snapshot with exactly Verified, Active, Resolved, and Unresolved metrics; each metric opens Community/Public Reports with the matching scope.
- [ ] Add a Home text field with explicit `Community Post` and `Public Report` actions. Store text in destination `SavedStateHandle`/existing draft state, consume once, and open the full composer without publishing.
- [ ] Add loading, unavailable, retry, empty-text, accessibility-label, and 48dp target handling.
- [ ] Rerun focused tests and the full Python suite.
- [ ] Commit: `feat: add verified report snapshot and home composer`.

## Task 5: Reduce Community to two direct feeds

**Files:**
- Modify: `app/src/main/java/za/org/rtc/community/feature/communityhub/CommunityHubScreen.kt`
- Modify: `app/src/main/java/za/org/rtc/community/feature/communityhub/CommunityModernisationScreen.kt`
- Modify: `app/src/main/java/za/org/rtc/community/feature/publicreports/presentation/PublicReportsScreen.kt`
- Modify: `app/src/main/java/za/org/rtc/community/feature/publicreports/presentation/PublicReportCard.kt`
- Modify: `app/src/main/java/za/org/rtc/community/ui/navigation/RtcCommunityNavGraph.kt`
- Modify: `app/src/androidTest/java/za/org/rtc/community/ResidentSurfaceSmokeTest.kt`

- [ ] Add failing Compose smoke assertions for exactly `Community Feed` and `Public Reports`, direct feed content, four scope controls in approved order, and absence of Marketplace/Services choices.
- [ ] Add failing reducer/state coverage proving a scope switch cannot retain rows from the previous scope.
- [ ] Run test compilation/focused tests and confirm failures identify the old hub UI.
- [ ] Render Community Feed directly for the first section and the canonical `PublicReportsScreen` directly for the second; remove Marketplace callbacks and choice cards from this root.
- [ ] Match community-post spacing/card rhythm while keeping report title, verification, urgency, lifecycle, evidence, votes, comments, detail, pagination, and secondary filters.
- [ ] Make Verified the initial scope and expose scopes in order: Verified, Active, Resolved, Unresolved.
- [ ] Rerun focused tests, Compose test compilation, and Python contracts.
- [ ] Commit: `feat: simplify community feeds`.

## Task 6: Reduce Explore to notices plus projects and opportunities

**Files:**
- Modify: `app/src/main/java/za/org/rtc/community/feature/explore/ExploreScreen.kt`
- Modify: `app/src/main/java/za/org/rtc/community/ui/navigation/RtcCommunityNavGraph.kt`
- Modify: `app/src/androidTest/java/za/org/rtc/community/ResidentSurfaceSmokeTest.kt`

- [ ] Add failing Compose assertions for exactly two root cards, actionable Project and Opportunity rows/counts, and absence of Centres, Help Centre, conversations, and directory overview.
- [ ] Run test compilation and confirm the old cards cause failure.
- [ ] Simplify the root composable and navigation contract while preserving existing deep-link/detail destinations.
- [ ] Rerun Compose test compilation and Python contracts.
- [ ] Commit: `feat: simplify resident explore`.

## Task 7: Make Market the direct fourth destination

**Files:**
- Modify: `app/src/main/java/za/org/rtc/community/ui/navigation/ResidentNavigationPolicy.kt`
- Modify: `app/src/main/java/za/org/rtc/community/ui/navigation/ResidentModernisationBindings.kt`
- Modify: `app/src/main/java/za/org/rtc/community/ui/navigation/RtcCommunityApp.kt`
- Modify: `app/src/test/java/za/org/rtc/community/ui/navigation/ResidentNavigationPolicyTest.kt`
- Modify: `app/src/androidTest/java/za/org/rtc/community/ResidentSurfaceSmokeTest.kt`

- [ ] Add failing policy tests asserting the resident destination order `Home, Community, Explore, Market, Account`, the compatibility route `services`, and the visible label/title `Market`.
- [ ] Add a failing Compose assertion that selecting Market renders Marketplace content immediately and never renders the Service Centre/Marketplace chooser.
- [ ] Run focused tests and test compilation to observe the expected failures.
- [ ] Change the fourth label/title to Market, use a storefront icon, and bind the `services` compatibility route directly to `MarketplaceHomeRoute`.
- [ ] Preserve historical Service Centre routes for explicit deep links and bookings, but remove them from primary entry.
- [ ] Rerun focused tests, Compose test compilation, and Python contracts.
- [ ] Commit: `feat: open marketplace from market tab`.

## Task 8: Verify, deploy, publish, and merge exact heads

**Files:**
- Modify: `docs/superpowers/plans/2026-08-31-resident-information-architecture.md` (checkbox evidence only)
- Create: `docs/RESIDENT_INFORMATION_ARCHITECTURE_RELEASE_EVIDENCE.md`

- [ ] Run `python3 tools/tests/run_contract_tests.py` and record the exact pass count.
- [ ] Run `deno test supabase/functions/_shared/auth_test.ts`.
- [ ] Run isolated `supabase test db`; confirm v1 and v2 RPC, grants, RLS, expiry, and cursor tests pass.
- [ ] Publish a remote feature branch whose parent is exact PR #33 SHA; create a stacked draft PR and verify the GitHub file diff contains only this plan's production changes plus the restored splash asset.
- [ ] Run GitHub Actions `gradle --no-daemon --stacktrace testDebugUnitTest`, `lintDebug`, `assembleDebug`, and `assembleDebugAndroidTest`; resolve any failure without weakening a check.
- [ ] Apply the additive v2 migration to the approved Supabase Production project, verify function signatures/grants and live verified-only counts, then keep old v1 reads available.
- [ ] Build/download the exact-head APK, calculate SHA-256 and byte size, and perform install/launch plus Home scope, Community switch, Explore, Market, Account, password, and Google-auth smoke checks when device infrastructure is available. Record unavailable infrastructure as unverified.
- [ ] Request code review and resolve findings using the receiving-review workflow.
- [ ] Reconfirm PR #31, PR #33, and feature PR heads have not drifted. Merge in dependency order only after each exact head is green and approved; do not use whole-file conflict resolution.
- [ ] Record merge SHAs, migration version, workflow/run links, artifact checksum, and any remaining external operational item in release evidence.
- [ ] Commit: `docs: record resident information architecture release evidence`.

## Task 9: Audit and safely clean repository storage

**Files:**
- Create: `docs/REPOSITORY_STORAGE_AND_BRANCH_AUDIT_2026-08-31.md`
- Modify only files explicitly identified as generated, duplicated, obsolete, or incorrectly tracked after review.

- [ ] Inventory Git objects, tracked file sizes, LFS usage, Actions artifacts/caches, releases, open PR heads, merged branches, protected branches, worktrees, generated APK/build outputs, duplicate assets, archives, and stale phase documents.
- [ ] Classify every candidate as keep, archive, delete-local, delete-remote, or history-rewrite-required, including owner, references, recoverability, byte savings, and risk.
- [ ] Remove ignored local build/cache outputs that are reproducible and outside active worktrees.
- [ ] Delete expired Actions artifacts/caches only after confirming no active PR/release/checkpoint references them; retain the newly published exact-head APK and manifest.
- [ ] Delete remote branches only when merged, not protected, not an open PR head/base, and not required for rollback. Never delete `main`, active integration heads, or release tags.
- [ ] Do not rewrite shared Git history merely to reclaim space. If large historical blobs require rewriting, document a separate coordinated maintenance window and migration procedure.
- [ ] Run all repository contracts after tracked-file cleanup, publish the audit, and commit any safe tracked cleanup as a standalone reviewable PR.

## Completion Definition

- The app shows five resident destinations: Home, Community, Explore, Market, Account.
- Home shows only verified Public Report metrics and safe composer handoffs.
- Community shows only Community Feed and verified-only Public Reports scopes.
- Explore has only Community Notices and Projects/Opportunities.
- Market opens Marketplace immediately.
- Supabase v2 reads are deployed, least-privilege tested, and backward compatible.
- Exact-head Android CI, APK checksum, and available device/auth smokes are recorded.
- Dependency PRs are merged without drift or weakened gates.
- Repository cleanup is evidence-based, recoverable where possible, and isolated from feature delivery.
