# Phase 30 — Synthetic-Only Device Validation Protocol

**Purpose:** Define the remaining device-level evidence required for the restored Android source. This protocol is deliberately **not** an execution record. No device/emulator test has been run under this document.

**Environment boundary:** Approved isolated non-production project `eqwstpdjoineycrkhpht` only, using existing synthetic identities and synthetic media. Production project `pbzzfzfgwzwdstvnwzqu`, production data, personal media, service-role credentials, Firebase service accounts, signed URLs, TOTP values, Edge deployment, scheduler, Vault, and remote Git operations are excluded.

## Unverified Behaviors

| Area | Why local build/static evidence is insufficient | Required result |
|---|---|---|
| Room v1→v2 migration | The database now adds owner identity to durable upload rows without destructive fallback. A real on-device migration must confirm existing data is preserved and remains quarantined. | The application opens after upgrade; legacy rows remain unowned and do not appear as retryable work. |
| Cross-account upload recovery | Static contracts show owner scoping, but only a device workflow can prove sign-out/sign-in and WorkManager timing across accounts. | Resident B never sees or retries Resident A’s pending upload; no B storage mutation is attempted. |
| Text-only Community post | The initial author path legitimately finalizes an empty outbox, while background recovery rejects one. | A text-only post succeeds for its author; an empty background outbox does not create a retry. |
| Image/video preparation and staged recovery | Bounded source code does not prove picker permissions, caching, process interruption, or clean-up on a given Android version. | Synthetic media respects limits, resumes only under the author, and staged files are removed after confirmed finalization. |
| Signed-media playback lifecycle | Local playback UI and signed URL refresh compile, but expiration/refetch behavior requires an actual player session. | Image/video remains viewable after an expired URL refresh without exposing a URL in logs or UI. |
| Profile-comment avatar coherence | The source now projects `avatar_updated_at` for cache busting, but Coil cache and the database view must be observed together. | Changing a synthetic profile photo refreshes both post and comment avatars after a normal content reload. |
| Private feedback screenshot cleanup | Source code deletes an owner-scoped object after feedback-record failure, but upload and error timing are device/network dependent. | A synthetic screenshot is deleted after a deliberately induced record-persistence failure; unauthorized identities cannot read it. |

## Preconditions

The test operator must first confirm the following without printing secrets or identifiers.

| Check | Requirement |
|---|---|
| Project pin | Runtime configuration must identify only `eqwstpdjoineycrkhpht`; the existing runner must fail closed if any other project is configured. |
| Credential handling | Owner-only ignored runtime material must be present. Do not open, print, upload, or copy its values. |
| Device | Use a dedicated emulator or non-production test device. Disable cloud backup and do not load personal content. |
| App build | Use the locally built debug application only. Do not describe it as a release candidate or distribute it. |
| Identities | Use only existing synthetic Resident A, Resident B, and any necessary synthetic staff identity. Do not create production accounts. |
| Media | Use bounded generated fixtures only: a minimal image and a short, non-sensitive synthetic video. Do not use camera/gallery content belonging to a person. |
| Logging | Collect redacted results only: pass/fail, test case name, and timestamp. Never record JWTs, TOTP values, UUIDs, storage paths, signed URLs, full email addresses, or screenshot content. |

## Execution Sequence

### 1. Room Migration and Account Quarantine

Install a prior local debug build that creates an upload outbox row under synthetic Resident A, then install the current local debug build without clearing application data. Open the application online and offline.

The operator must verify that the application starts normally, pending work is not attributed to a later user, and a legacy row with no verified owner is not automatically retried. The test fails if a destructive database reset occurs, if the application crashes on migration, or if a legacy row is retried under any account.

### 2. Interrupted Author Upload and Cross-Account Switch

Using synthetic Resident A and a bounded generated image, begin a Community post with media. Interrupt connectivity only after staging begins and before finalization. Confirm that the app reports retryable work only while A is the active authenticated user. Sign out, then sign in as synthetic Resident B before allowing the worker to run.

The required result is that B’s pending count remains zero, no A draft is submitted by B, and any server-side metadata for A’s object/draft is unchanged by B. Sign back in as A, restore connectivity, and confirm that only A can complete recovery. This is an authorization and persistence test; no test should assert cross-account upload success.

### 3. Text-Only Post and Empty Background Outbox

Using synthetic Resident A, submit a short text-only Community post. The post must finalize successfully without a media outbox row. Restart the application and confirm that background recovery does not manufacture retry work for the empty outbox.

### 4. Synthetic Image/Video and Signed Playback

Submit a bounded synthetic image and a short synthetic video as separate posts. Open each after initial display, then after the signed playback URL is known to have expired or has been invalidated by the test wait period. The application should refresh delivery through the existing guarded media path and continue display/playback without printing a signed URL.

### 5. Profile Avatar Projection Parity

Using a synthetic account with a post and comment, replace the private profile photo with a bounded generated image. Perform the ordinary app content refresh. Verify that the account avatar changes in the profile, its Community post, and its Community comment. The test fails if comments show the earlier cached image after a normal reload.

### 6. Feedback Screenshot Privacy and Failure Cleanup

With synthetic Resident A, select a bounded generated screenshot. Arrange a non-destructive feedback-record failure after the attachment upload boundary, for example through an approved, reversible isolated test condition; do not weaken RLS or alter production. Verify, through permitted owner metadata access only, that the attachment is removed. Confirm synthetic Resident B cannot read or delete the object, and that the intended triage role boundary remains as documented.

## Evidence to Record

For each case, record only the test-case identifier, device/API level, app commit, isolated project confirmation, synthetic role label, expected result, observed result, and a redacted timestamp. Capture screenshots only if they contain no private content, session identifiers, URLs, or credentials. Preserve a brief negative-result note for any failed control and do not make a readiness claim from partial results.

## Completion Criteria

This protocol may be marked executed only after every applicable case has a recorded synthetic-only result and the existing server authorization runner has been rerun against the approved project. Until then, source tests and local builds demonstrate code consistency but not device-level production readiness. The overall status remains **NO-GO**.
