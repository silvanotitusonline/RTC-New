# Phase 35 — Community Remediation Plan and Engineering Estimates

**Date:** 2026-08-26
**Environment:** Approved isolated non-production baseline only (`eqwstpdjoineycrkhpht`)
**Purpose:** Repair the verified Community truthfulness defects while preserving RLS, authenticated server authority, MFA/AAL2 boundaries, audit controls, and private Storage rules.

> **Estimate basis:** The figures below are engineering-effort ranges, not release commitments. They assume the current recovered source baseline, an available isolated test runtime, and no new policy/product requirements. They exclude production deployment, Play Console work, and waiting for external approvals.

## Current Design Discovery

The isolated baseline already has two profile-to-Community triggers. One is insert-only; the other runs after profile insert or update and invokes the existing Community-profile reconciliation helper. This is a positive isolated-state finding, but the later trigger is not represented in the recovered source-controlled migrations. Therefore, the required avatar remedy is **source-control reconciliation plus end-to-end proof**, not a blind duplicate trigger.

The isolated baseline does **not** have a post-like RPC or a `viewer_has_liked` feed projection. The Android client likewise lacks like and share actions. The existing `rtc://community/...` scheme is registered, but the activity currently handles only alert links, so a safe Community-post share link needs both handling and navigation work.

## Delivery Sequence

| Workstream | Scope and implementation approach | Security acceptance criteria | Estimated effort |
|---|---|---|---:|
| 1. Avatar synchronization reconciliation | Add an idempotent source migration that records the existing `profiles` → `community_profiles` after-insert/update synchronization contract. It will update only the derived Community display name/avatar projection and revision timestamp. Align both post and comment feed projections to expose the revision used for cache-busting. | Trigger function remains in `private`; it is not Data-API callable. The Android client never gains direct write access to Community profiles. No service-role credential is introduced. | **1.0–1.5 days** |
| 2. Avatar UI and verification | Render the existing Community avatar component in feed cards, preserve initials fallback, and reload/requery only after server-confirmed profile upload/delete. Add tests for current versus stale avatar paths. | A failed upload must not update the display optimistically. User A can never write User B’s private photo path. | **0.5–1.0 day** |
| 3. Guarded post likes | Add an authenticated, atomic `toggle_community_post_like(uuid)` server operation. It will verify `auth.uid()`, a viewable published post, Community eligibility, and the caller’s own Community identity before toggling only that caller’s `LIKE`. Add `viewer_has_liked` to the security-invoker feed projection; direct client writes to the reaction table remain unavailable. | The public function has all default execution revoked, then explicit `authenticated` execution only. It contains an `auth.uid()` check and locked `search_path`; it returns only the caller’s new boolean state. RLS remains enabled. | **2.0–3.0 days** |
| 4. Like UI | Add accessible Like/Unlike actions and a visible pressed state. Do not apply permanent optimistic counts; refresh or reconcile from the server result after the RPC succeeds. Hide neither the count nor the control once the guarded contract exists. | Button is disabled while its own request is in flight; errors leave server-confirmed state intact and are shown honestly. | **1.0–1.5 days** |
| 5. Safe post sharing | Add an Android share chooser that shares only `rtc://community/post/{post-id}` plus neutral app text. Add Community-post deep-link handling to `MainActivity` and controlled navigation to the post route. Do not include signed media URLs, post body, author identity, private metadata, or unpublished content. | Link input is length/character checked. Post route still performs authenticated load and RLS-backed server fetch; receiving the link conveys no permission. | **1.0–1.5 days** |
| 6. Video sound control | Add a labelled in-app mute/unmute control on `SignedVideoPlayer`. Bind it directly to the active ExoPlayer instance (`0f`/`1f`), announce its state to accessibility services, and keep the choice within the active player session. | No background audio, microphone permission, recording, or persistent audio preference is introduced. Player release clears the session state. | **0.75–1.25 days** |
| 7. Honest inert-action cleanup | Convert “Saved items”, “Following”, “Recent Community”, unavailable work-queue actions, and no-op staff controls into either real routes or non-interactive truthful status rows. Remove the Following filter until follow management exists, or implement a separate guarded topic-follow feature. Distinguish failed post loading from genuinely unavailable content. | No card remains tappable with an empty handler. No unavailable route is presented as live functionality. Protected route checks remain fail-closed. | **1.0–1.5 days** |
| 8. Regression and device evidence | Add source contracts for all remediation points. Run source contracts, Kotlin compilation, advisor review after DDL, targeted real-session tests with two synthetic residents, and the documented device media protocol. Reattempt a full debug package in a stable build environment. | Tests prove denial for unauthenticated/unauthorized mutation, like isolation between users, no signed URL disclosure in shares, avatar propagation, playback sound state, and truthful error states. | **2.0–3.0 days** |

**Estimated implementation subtotal:** **9.25–13.25 engineering days**.
**With isolated environment recovery and full device-release validation:** **11–16 engineering days**.

## Detailed Design: Broken Video Sound Toggle

### Problem

`SignedVideoPlayer` delegates visible controls to ExoPlayer `PlayerView`, but the application owns no sound state, mute action, or accessibility label. Therefore the app cannot truthfully promise that users can turn sound on and off.

### Remediation

The player composable will own `isMuted` as active-session state and derive `player.volume` from that state. A clearly visible `IconButton` will toggle mute, exposing exactly one of `Mute video` or `Unmute video` as its content description and a selected state that TalkBack can announce. The existing full-screen player controller remains available for play/pause/seek; the new control does not manipulate media URLs or server state.

### Acceptance Tests

| Test | Expected result |
|---|---|
| Start video playback | Video plays through existing ExoPlayer flow; the sound control is visible and correctly labelled. |
| Tap Mute | `player.volume` becomes `0f`; accessible label changes to “Unmute video”; playback continues. |
| Tap Unmute | `player.volume` returns to `1f`; accessible label changes to “Mute video”. |
| Swipe between media / close gallery | Player is released and no audio continues in the background. |
| URL-refresh retry | Resume behavior preserves playback position; mute state is applied to the re-prepared player. |

## Detailed Design: Unsynchronized Avatar Projections

### Problem

The Android upload flow correctly writes the canonical private avatar path to `profiles.avatar_url`, validates the write, and refreshes the UI. Community posts and comments, however, read the derived `community_profiles.avatar_path`. If source control does not reproduce the isolated after-update sync trigger, a future environment can have stale Community avatars even though the Account screen shows the new photo.

### Remediation

The source migration will reconcile the trigger/function contract currently present in the isolated baseline. The ordinary row trigger fires only on relevant `profiles` writes and calls the existing private helper to update the derived Community profile. The post and comment feed projections must carry the Community profile revision field so Android’s signed avatar URL includes a changing cache-busting revision. The client continues to rehydrate Account, feed, and open detail only after the server confirms the profile update.

### Acceptance Tests

| Test | Expected result |
|---|---|
| Resident A adds a photo | `profiles.avatar_url` and A’s derived Community avatar path match; A’s Account and Community feed show the new image. |
| Resident A replaces a photo | Revision changes; no old cached photo remains in A’s post cards, comment rows, or post detail. |
| Resident A removes a photo | Both canonical and derived avatar fields clear; initials fallback is shown. |
| Resident B views A | B sees the same updated/fallback avatar through RLS-permitted feeds, without gaining access to A’s private object path. |
| Failed upload | Neither canonical nor derived profile record changes; UI retains the last server-confirmed avatar. |

## Implementation Constraints

The work is limited to local source and the approved isolated project. Each DDL change must be formalized through the isolated migration path, reviewed for explicit grants/RLS, and followed by an advisor check. The Android app keeps its existing publishable-key client; it must never contain a database password, service-role credential, Storage signing secret, or privileged bypass. No production migration, trigger, RLS policy, Storage policy, Edge Function, scheduler, or data change is authorized by this plan.

## Immediate Next Actions

1. Capture the actual isolated trigger into source control as an idempotent reconciliation migration.
2. Implement the guarded like API and feed projection in local source, then apply it only to the isolated project after source review.
3. Wire accessible Like, Share, avatar feed, deep-link, and mute controls in the Android client.
4. Remove or make honest all inert controls identified in Phase 34.
5. Execute regression, advisor, and two-resident/device validation; retain **NO-GO** until those results exist.

## References

[1]: https://supabase.com/docs/guides/api/securing-your-api "Supabase — Securing your API"
[2]: https://supabase.com/changelog.md "Supabase Changelog"
