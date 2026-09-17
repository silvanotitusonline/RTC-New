# Repository Review: RTC Community Android

## Summary

The repository is a production-oriented Android/Jetpack Compose application backed by Supabase migrations and Edge Functions. The current branch demonstrates strong source-level security intent, extensive regression contracts, and a clear separation between UI, repositories, local persistence, and backend policy layers. However, I recommend **requesting changes before release** because the account-deletion sweep does not actually complete account deletion, reports success despite storage-cleanup failures, and can process an unbounded backlog in one invocation.

The review covered the Android source, Supabase migrations and Edge Functions, CI configuration, local persistence, tests, and the latest commit (`42da870`).

## Verification performed

| Check | Result |
|---|---|
| Source regression contracts | **180/180 passed** via `python3 tools/tests/run_contract_tests.py` |
| Repository status | Clean on `main` after clone |
| Recent commit review | Reviewed latest fix commit and surrounding history |
| Edge authorization tests | Not runnable locally: `deno` is not installed in the sandbox |
| Android unit tests/lint/build | Not runnable locally: repository has no `gradlew`, and system `gradle` is unavailable |
| Supabase local pgTAP suite | Not run; requires the Supabase CLI/container runtime |

Passing the contract suite is valuable, but it is primarily source/structure verification. It does not prove that account deletion, storage cleanup, Android compilation, or live database behavior is correct.

## Critical issues — must fix before release

### 1. Account deletion reports success without deleting the authenticated account

**Location:** `supabase/functions/process-account-deletions/index.ts:61-104`

The sweep selects an account-deletion request, deletes only the corresponding row from `public.profiles`, and then returns `status: "deleted"`. The `profiles.id` foreign key points **to** `auth.users(id) on delete cascade`; deleting the profile does not delete the parent `auth.users` record. The function never calls the Supabase Admin Auth deletion API, such as `supabase.auth.admin.deleteUser(userId)`, nor does it invoke a database function that performs the complete deletion workflow.

The request row itself also remains in `account_deletion_requests`, and the function does not mark it `completed`, `failed`, or `processed_at`. On the next scheduler run, the same request can be selected again.

**Impact:** A user who requested account deletion can remain an active authenticated account with residual personal data and a permanently reprocessable deletion request. This conflicts with the documented hard-delete account-lifetime policy in `docs/ADR/0003-data-retention-and-archival.md:16-17` and can create privacy/compliance exposure.

**Recommended fix:** Make deletion an explicit, idempotent workflow:

1. Claim one bounded batch of requests by atomically moving eligible rows to `processing`.
2. Delete or anonymize all user-owned records according to the retention policy.
3. Delete all user-owned storage objects.
4. Call `auth.admin.deleteUser(userId)` only after dependent cleanup is complete, or use a reviewed server-side deletion RPC with equivalent guarantees.
5. Mark the request `completed` and set `completed_at`; on failure, mark `failed` with a safe internal/audited reason and make retries deliberate.
6. Add an integration test that proves the user cannot authenticate after a successful sweep and that the deletion request is not selected again.

### 2. Storage cleanup failures are ignored and successful deletion is reported anyway

**Location:** `supabase/functions/process-account-deletions/index.ts:81-90`

The function calls storage removal, logs `storageError`, and proceeds to mark the user as `deleted` when the profile delete succeeds. The result status is therefore based only on the profile deletion, not on complete cleanup. In addition, the removal request is the single path ``posts/${userId}``; the data model stores media in `community_post_media.storage_path`, whose paths are not constrained by this function to be descendants of that exact object. A single object path removal is not a complete enumeration of a user's media objects.

**Impact:** User media can remain after the sweep reports success. This is especially serious because the same function is intended to implement account deletion and because retries may not occur after a false success.

**Recommended fix:** Treat storage cleanup as a required step. Enumerate objects using the storage API or, preferably, maintain a server-authoritative media ownership query that returns exact object paths for the user, then remove those paths in bounded chunks. Abort/mark the request failed when any cleanup step fails. Only emit `completed/deleted` after every required cleanup stage succeeds. Add tests for partial storage failure and for multiple nested object paths.

## Major issues — should fix

### 3. The sweep has an unbounded, non-transactional workload

**Location:** `supabase/functions/process-account-deletions/index.ts:61-79`

The query fetches every request older than 30 days without a `limit`, and the function then processes each user sequentially. A large backlog can exceed the Edge Function execution window. A timeout midway through the loop leaves a mixed state: some profiles may be deleted, later users remain untouched, and requests are not transitioned to a state that makes retry behavior explicit.

**Impact:** Backlogs become increasingly difficult to drain, and partial failures are hard to reason about. Combined with the current success semantics, this can produce incomplete deletion with no reliable recovery signal.

**Recommended fix:** Use a bounded batch size, claim rows atomically with `FOR UPDATE SKIP LOCKED` or an equivalent RPC, and make each user deletion idempotent. Return a partial/ retryable outcome when the batch is not fully processed. Add an operational metric for claimed, completed, failed, and retried requests.

### 4. The repository omits the Gradle wrapper, weakening reproducibility

**Location:** repository root; `gradle/wrapper/gradle-wrapper.properties` exists, but `gradlew` and `gradlew.bat` are absent.

CI installs Gradle through `gradle/actions/setup-gradle@v6`, so the hosted workflow can work, but contributors and local automation cannot use the documented/standard `./gradlew` entry point. In this review environment, Android verification could not run because there was no wrapper and no system `gradle` executable.

**Impact:** Local builds are dependent on an externally installed Gradle version and environment configuration. This increases “works in CI, fails locally” risk and makes it harder to reproduce release failures.

**Recommended fix:** Commit the standard Gradle wrapper scripts and wrapper JAR, then update CI and documentation to use `./gradlew`. Keep the Gradle version pinned in `gradle-wrapper.properties` and optionally verify wrapper integrity in CI.

### 5. The outbox DAO exposes an unscoped observer

**Location:** `app/src/main/java/za/org/rtc/community/data/local/RtcDatabase.kt:64-67`

`UploadOutboxDao.observeAll()` selects every row from `community_upload_outbox` without an `owner_user_id` predicate, while the other outbox operations consistently require an owner. The entity even documents legacy rows with a nullable owner at line 32.

No current production call site was found for `observeAll()`, so this is a latent rather than currently demonstrated leak. Nevertheless, any future consumer could expose another account's pending upload metadata after logout/account switching on a shared device.

**Recommended fix:** Remove the method if unused, or replace it with `observeForOwner(ownerUserId)` and require the owner in every query. Add a test asserting that a flow for user A cannot emit user B's rows and that legacy null-owner rows are never resumed or displayed.

## Minor issues

### 1. Edge tests are declared in CI but not locally self-contained

**Location:** `.github/workflows/android-ci.yml:32-35,102-103`

CI runs `deno test`, but the repository does not provide a local toolchain bootstrap for Deno. The shared authorization tests could not be executed in this environment because Deno is not installed.

**Suggestion:** Document the required Deno version and add a simple developer verification command or a dev-container/toolchain setup. This is secondary to the production issues above.

### 2. The review surface is very large for a single migration stream

The repository contains 60 Supabase migrations and a high volume of same-day changes. The migration naming and uniqueness contracts are good safeguards, but the pace makes deployment-order and rollback review important.

**Suggestion:** Require a migration review checklist for destructive/account-lifecycle changes, and add explicit forward-only rollout notes for migrations that alter security-definer functions, grants, or deletion semantics.

## Positive feedback

- The repository has strong fail-closed security intent. The shared Edge authorization module bounds request bodies, validates UUIDs, applies rate limits, writes audit events, and suppresses raw backend errors.
- The migration suite is unusually deliberate about `SECURITY DEFINER`, `search_path`, explicit grants/revokes, and RLS boundaries. The 180 passing regression contracts confirm that many of these invariants are actively protected.
- The latest commit correctly fixes a false-success path in post creation, adds a scheduler secret boundary around account deletion, and repairs invalid migration syntax. These changes show good attention to failure semantics and protected operations, even though the deletion workflow still needs end-to-end completion.
- Android code is organized by feature and layer, with extensive unit tests covering reducers, validation, mappers, navigation, media handling, and state transitions.
- CI has useful release hygiene: restricted workflow permissions, isolated runtime properties, cleanup of reconstructed credentials, explicit Android SDK setup, lint, unit tests, APK assembly, and Compose smoke-test compilation.
- The README clearly distinguishes source-level verification from Android CI and device gates, which is an important and honest limitation.

## Questions for the author

1. Is `process-account-deletions` intended to remove the `auth.users` record, or is this currently only a profile cleanup phase? If it is phased, where is the authoritative final deletion step and how is the request state advanced?
2. What is the canonical storage path format for community post media? If it is represented by `community_post_media.storage_path`, why does the sweep remove only `posts/${userId}` rather than querying exact paths?
3. Should a storage cleanup failure prevent account deletion completion? The current implementation logs the failure but returns success.
4. What is the expected behavior when a deletion batch exceeds the Edge Function runtime limit? Is there a scheduler retry contract and an idempotent claim mechanism?
5. Is `UploadOutboxDao.observeAll()` intentionally unused legacy API, or is it planned for a UI/sync consumer? If unused, removing it would eliminate a future isolation hazard.

## Test coverage assessment

| Area | Assessment |
|---|---|
| Source contracts | Strong: 180 passing contracts |
| Android unit tests | Broad test inventory exists, but not executable in this environment because Gradle tooling is unavailable |
| Android instrumentation | Smoke/navigation coverage exists, but device execution was not performed |
| Edge authorization | CI test exists, but local execution was unavailable because Deno is not installed |
| Database/RLS | pgTAP tests and a local CI workflow exist, but the local Supabase suite was not executed here |
| Account deletion | Insufficient end-to-end coverage for auth-user removal, storage enumeration, retry/idempotency, and request-state transitions |

## Verdict

**Request Changes.** The project has a strong foundation and good security/testing discipline, but account deletion is a release-blocking workflow. Fix the authenticated-user deletion, complete and verify storage cleanup, make the sweep bounded/idempotent, and add end-to-end deletion tests before treating the feature as production-ready.

## Suggested priority order

1. Implement and test the complete account-deletion state machine.
2. Enumerate and verify deletion of all user-owned storage and database data.
3. Add bounded claiming/retry semantics to the sweep.
4. Add the Gradle wrapper and document the complete local verification toolchain.
5. Remove or owner-scope `UploadOutboxDao.observeAll()`.
6. Add an integration test suite covering account deletion from request through authenticated-account removal.

---

**Reviewed repository:** `https://github.com/silvanotitusonline/RTC-New`  
**Reviewed branch/commit:** `main` at `42da870`
