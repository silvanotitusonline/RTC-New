# Repository storage and branch audit — 2026-08-31

Repository: `silvanotitusonline/RTC-Community-Production`

This audit accompanies the resident information architecture implementation. It records exact branch relationships, distinguishes source-tree size from GitHub Actions storage, and preserves unmerged work instead of treating branch deletion as a substitute for semantic integration.

## Current implementation head

| Item | Value |
|---|---|
| Integration branch | `integration/resident-information-architecture-v1` |
| Published head before this audit | `03a17fdd922de0d8b3c13fefc67a341216cdfb46` |
| Direct parent | `integration/resident-navigation-v2-chrome` at `8c84c3c25b4590f9f4d3150c15a3b37e9baf6794` |
| Relationship to `main` | 66 commits ahead, 0 behind before this audit |
| Relationship to navigation V2 | 1 commit ahead, 0 behind before this audit |
| Validation status | Not run by implementation owner; application owner reserved all testing |
| Supabase deployment | Migration committed only; production was not modified |

The published feature head contains the verified-only report metrics/scopes, Home composer handoff, simplified Community and Explore surfaces, and the direct Market tab implementation. It includes the PR #31 and PR #33 ancestry rather than duplicating their RPC client or hard-wiring a second navigation implementation.

## Storage findings

| Surface | Finding | Decision |
|---|---:|---|
| GitHub repository-reported size | Approximately 46,815 KB | Healthy for an Android application; no history rewrite justified |
| Local clone including worktrees | Approximately 25 MiB | No material cleanup target |
| Active feature worktree | Approximately 8 MiB | Retain until owner verification is complete |
| Loose Git objects | 901 objects / 6.93 MiB | Retain; normal development data |
| Largest tracked file | Approximately 512 KB | No large-file migration needed |
| Build and Gradle caches in repository | None found | Nothing to remove |
| Local ignored Python bytecode cache | Approximately 272 KB | Removed; it is generated data and can be regenerated |
| Releases | None | Nothing to prune |

The previously observed artifact-storage exhaustion is not caused by this source checkout. Actions artifacts and Actions caches use separate GitHub quota. The available repository connector can inspect artifacts for a known workflow run, but it cannot enumerate or delete repository-wide Actions artifacts/caches. Those quotas therefore remain an account-level cleanup item in GitHub Actions settings.

No history rewrite is recommended. Rewriting history would invalidate exact-head APK provenance, disrupt all open PRs, and provide little value because the tracked objects are small.

## Branch inventory

At audit start the repository had 43 branches and 8 open pull requests. The recoverable cleanup below reduced the open pull-request count to 5 without deleting any branch or commit.

### Retain: active integration or open-PR ancestry

- `main`
- `integration/resident-information-architecture-v1`
- `integration/resident-modernisation-v1`
- `integration/compose-mvp-v1` — PR #31
- `feature/compose-resident-ui-v1` — PR #32
- `integration/resident-navigation-v2-chrome` — PR #33
- `design/material3-personalization` — PR #34
- `artifact/pr33-apk-recovery-8c84c3c` — PR #35 and exact-head APK provenance

### Safe branch-ref deletion candidates

The following inactive branches are zero commits ahead of `main`. Deleting their refs cannot discard source changes that are not already reachable from `main`. It will reduce branch-list clutter, but it will not materially reduce Git object or Actions storage.

| Branch | Commits behind `main` |
|---|---:|
| `feature/marketplace-build` | 200 |
| `fix/community-migration-version-parity` | 117 |
| `foundation/resident-modernisation-contracts` | 1 |
| `foundation/resident-modernisation-contracts-ready` | 1 |
| `hardening/production-integration-foundation` | 161 |
| `integration/brand-experience-v2` | 102 |
| `integration/community-production-v2` | 121 |
| `integration/marketplace-production-v2` | 126 |
| `integration/service-centre-production-v2-ready` | 92 |
| `integration/service-centre-production-v2` | 92 |
| `integration/supabase-security-v2` | 156 |
| `refactor/split-main-activity` | 170 |
| `release/final-production-readiness` | 42 |

The connector exposed for this audit has no branch-ref deletion operation. These refs were therefore not deleted remotely. Their deletion is optional housekeeping, not a production-readiness gate.

### Review before deletion: unique commits remain

The following inactive branches are still ahead of or diverged from `main`. They must not be bulk-deleted. A semantic comparison is required before closing or archiving them.

| Branch | Ahead | Behind | Relationship |
|---|---:|---:|---|
| `ci/civic-reports-migration-provenance` | 3 | 0 | Ahead |
| `diagnostic/civic-report-query-plan-6cb3` | 18 | 0 | Ahead |
| `feature/broader-brand-experience-customizer` | 8 | 200 | Diverged |
| `feature/broader-brand-experience-customizer-current` | 80 | 197 | Diverged |
| `feature/community-ui-ux-optimisation` | 7 | 188 | Diverged |
| `feature/public-reports-android-integration-v1` | 69 | 0 | Ahead |
| `feature/public-reports-v1` | 14 | 0 | Ahead |
| `feature/resident-shell-events-v1` | 47 | 0 | Ahead |
| `feature/service-centre-mvp` | 33 | 116 | Diverged |
| `feature/public-reports-android-v1` | 54 | 0 | Superseded PR #27 branch; retained for recovery |
| `fix/ui-safe-errors-and-resilience` | 1 | 196 | Diverged |
| `google-signin-main-update` | 4 | 196 | Diverged |
| `hardening/supabase-security-v2` | 34 | 155 | Diverged |
| `integration/resident-modernisation-wiring-v1` | 114 | 0 | Superseded PR #28 branch; retained for recovery |
| `integration/resident-modernisation-v1-ui` | 73 | 0 | Superseded PR #29 branch; retained for recovery |
| `optimize/brand-experience-production` | 55 | 169 | Diverged |
| `optimize/community-production` | 60 | 155 | Diverged |
| `optimize/marketplace-production` | 54 | 169 | Diverged |
| `remediation/pre-marketplace-foundation` | 2 | 201 | Diverged |
| `security/supabase-hardening` | 4 | 188 | Diverged |
| `test/marketplace-demo-data` | 1 | 196 | Diverged |
| `testable/pr29-reconciled-ui` | 50 | 0 | Ahead |

## Pull-request disposition

| PR | Recommended disposition |
|---|---|
| #31 | Retain until the integration head is accepted; it is the canonical Public Reports/Compose foundation |
| #33 | Retain until the integration head is accepted; it is the five-tab navigation foundation |
| #27, #28, #29 | Closed as superseded with replacement notes; branches and commits retained for recovery |
| #32, #34 | Independent active work; do not alter during this cleanup |
| #35 | Retain as exact-head APK provenance until a replacement artifact is published and accepted |

## Cleanup actions completed

- Removed the ignored 272 KB Python bytecode cache. It is generated data and can be recreated by Python tooling.
- Closed draft PRs #27, #28, and #29 as superseded after documenting their canonical replacement paths. This operation is recoverable; no source branch was deleted.
- Preserved all user-owned dirty worktree files and all branches with unique commits.
- Did not rewrite history, delete APK provenance, or remove any security/authentication/signing control.

## Production handoff state

Implementation is committed, but these items are intentionally recorded as owner verification rather than silently marked passed:

- Android build and install
- sign-in, Google sign-in, and password recovery
- Home verified-only count semantics
- Community feed and all four Public Report scopes
- report voting, comments, evidence upload, and admin transitions
- five-tab navigation and Market storefront behavior
- Supabase migration application and post-deployment RPC checks
- release signing and store-distribution checks

Leaked-password protection remains an accepted plan limitation until the project is upgraded to a Supabase tier that provides it. This exception must be documented in the release risk register; it does not justify weakening any other authentication or authorization control.
