# Phase 34 — Application Truthfulness and Community Media Audit

**Date:** 2026-08-26
**Scope:** Native Android UI and source-controlled Supabase contracts, with emphasis on Community, Account, profile photos, post media, comments, reactions, sharing, and video controls.
**Overall conclusion:** **NO-GO.** The source contains genuine post, comment, image/video upload, and profile-photo mechanisms, but it also contains material features that are missing, inert, or not validated on a fresh authenticated device session.

> This is a source-and-contract audit, not an assertion that every workflow has passed on a phone. The lost owner-only synthetic runtime vaults prevent fresh authenticated real-session validation in the restored environment. No production system was queried or changed.

## Audit Basis

| Evidence source | Result | Meaning |
|---|---:|---|
| Android source: `MainActivity.kt`, `RtcRepository.kt`, `ProductionUxRepository.kt`, shared Compose components | Reviewed | Establishes what the client actually renders and calls. |
| Community migrations | Reviewed | Establishes the recovered Community feed, profile, comment, reaction, media, and policy contracts. |
| Ox Alpha bounded review | Completed | Reviewed non-sensitive implementation facts only; no credentials, identities, service access, or production data were provided. |
| Repository source-contract suite | **55/55 passed** | Confirms source invariants, not live-device success. |
| Kotlin compiler | **Passed** for `compileDebugKotlin` | Confirms current Kotlin source compiles. |
| Full debug APK package | **Not accepted as evidence** | The restored sandbox stalled at `mergeExtDexDebug`; the run was stopped and no new APK was produced. |
| Fresh authenticated device / real-session tests | **Not run** | Cannot claim end-user proof after the environment reset. |

## User-Facing Status

| Capability requested | Source finding | Status | Evidence |
|---|---|---|---|
| Community profile presence | Every user has a Community identity record after the Community helper runs, but a real photo is optional and initials are used as fallback. | **Partially implemented** | `community_profiles` helper and `CommunityAvatar` fallback. |
| Choose/upload profile picture | Account UI exposes gallery and camera selection, preview, save, remove, JPEG normalization, private scoped upload, metadata persistence, and server reread. | **Implemented in source; runtime unverified** | Account/Profile editor and `uploadProfilePhoto`. |
| Photo updates across Community | The client refreshes session, feed, and open detail after upload, but Community feed/comment views read `community_profiles.avatar_path` while upload updates only `profiles.avatar_url`. The synchronizer runs on guidelines acceptance, post draft, or comment creation—not on photo upload. | **Defect: not guaranteed** | Community profile helper, feed projections, and upload flow. |
| Create text posts | Post composer and server draft/finalize flow support text-only posts. | **Implemented in source; runtime unverified** | `PostComposer`, `createCommunityPost`, `resumeCommunityUpload`. |
| Upload Community images/videos | Gallery/camera media selection, bounded preparation, owner/draft-scoped upload, and server finalization are implemented. | **Implemented in source; runtime unverified** | Composer, upload outbox, Storage upload, finalize RPC. |
| View Community images/videos | Image preview, full-screen pager, signed URL handling, and video player are implemented. | **Implemented in source; runtime unverified** | Media preview, gallery, signed URL refresh. |
| Comment on a post | Create, edit, and delete comment calls exist; successful operations reload post detail. Guidelines gate is present. | **Implemented in source; runtime unverified** | Post detail and comment repository methods. |
| Like/react to a post | Counts are rendered and a reaction table exists, but there is no like/reaction control, no Android mutation method, and no recovered write path for the client. | **Missing** | Feed component, source scan, recovered policy contract. |
| Share a post | No share control, Android share intent, share-sheet integration, or share method exists. | **Missing** | Source scan. |
| Play videos | ExoPlayer `PlayerView` is used with standard playback controls and URL-refresh retry after playback error. | **Implemented in source; runtime unverified** | `SignedVideoPlayer`. |
| Explicit sound on/off | No app-level mute/unmute state, button, or `setVolume` control is implemented. Default player controls must not be represented as a verified mute feature. | **Missing** | Targeted audio-control source scan. |

## Material Truthfulness and Design Findings

| Priority | Finding | Why it matters | Required correction |
|---|---|---|---|
| **P0** | **Profile-photo propagation is not guaranteed for existing Community users.** | A successful upload changes `profiles.avatar_url`, but Community cards/comments project `community_profiles.avatar_path`. Reloading the UI cannot repair a stale Community profile record. | Add a guarded, source-controlled server-side synchronization mechanism that updates the Community projection when the canonical avatar changes; then verify with two distinct authenticated accounts. Do not solve this with client-side impersonation or direct privileged database access. |
| **P0** | **Reaction counts are displayed without a user action path.** | The feed states a number of reactions, while the application offers no like/reaction control and no supported mutation. This looks broken or fabricated to residents. | Either remove reaction counts until reactions are genuine end-to-end functionality, or implement a constrained authenticated reaction RPC/policy, client operation, visible control, optimistic-state rules only after server confirmation, and audit coverage. |
| **P0** | **Sharing is absent.** | Residents cannot share a post despite the Community wording and the requested product behavior. | Add an explicit, privacy-safe share workflow. It must share a public/deep link or a redacted preview only; never expose private signed Storage URLs or unpublished content. |
| **P0** | **Fresh device/session evidence is absent.** | Source contracts cannot prove gallery permissions, camera capture, signed URL expiry recovery, audio, profile cache refresh, or Community policy behavior on a real phone. | Recreate the isolated synthetic runtime and run the documented P0 device protocol before release. |
| **P1** | **The app cannot claim a dedicated mute/unmute control.** | Video may start with audible audio and users have no consistent in-app sound control. | Add a clearly labelled mute/unmute control bound to player volume state, persisted only for the active playback session unless a deliberate preference is designed. Verify TalkBack labels and state. |
| **P1** | **Post-load failure can be phrased as removal.** | When post detail is null after a fetch failure, the screen says the post is no longer available; a transient network/policy failure can therefore appear as a deletion. | Separate “not found/removed” from “could not load; retry” using the repository’s actual error state. |
| **P1** | **Following is presented but cannot be managed.** | Community offers a Following filter and the Account page says users can review people/topics/projects they follow, but there is no Android follow/unfollow operation. The filter can lead to an empty state users cannot remedy. | Hide the filter and Account action until following management exists, or implement it end-to-end with a clear topic-follow UI and server-backed mutations. |
| **P1** | **Several Account actions are inert.** | “Saved items”, “Following”, and “Recent Community” are visually presented as navigation cards but use empty handlers. | Remove, disable with an honest “Coming later” label, or wire real destinations. Do not leave tappable no-op cards. |
| **P1** | **Operational/work-queue affordances include inert actions.** | Some work queue cards and “Open queue” controls use no-op handlers. This reduces trust, especially in administration surfaces. | Replace with non-tappable status presentation or wire verified destinations; retain protected routing and approval requirements. |
| **P2** | **Profile photo is described as “cropped on-device,” but source normalizes orientation, downscales, and JPEG-encodes rather than offering a user crop editor.** | The wording overstates the visible experience. | Change copy to “prepared on-device” unless a real crop UI is implemented. |
| **P2** | **Community feed identity hierarchy is weak.** | Shared feed cards render author text and counts, but omit a visible author avatar despite detail/comment views rendering one. | Add the existing `CommunityAvatar` to feed cards after the canonical-to-Community avatar synchronization defect is fixed. |

## What Is Safe to Say Today

The Community source has real technical foundations for posting, media upload, commenting, private profile photo storage, and video playback. It is **not** safe to say that likes, sharing, dedicated sound toggling, follow management, or complete cross-Community profile-photo propagation work for residents. It is also not safe to claim a production-ready build because fresh real-session/device evidence and a full current APK package are missing.

## Required Test Matrix Before Any Release Claim

| Test | Accounts | Pass condition |
|---|---|---|
| Profile photo propagation | Resident A and Resident B | A uploads/replaces/removes a photo; A’s account, A’s existing post, A’s existing comment, and B’s feed/detail all show the correct current avatar or fallback. |
| Image post | Resident A, Resident B | A posts bounded image media; B sees the image in feed and gallery; unauthorized user cannot access private object paths. |
| Video post and audio | Resident A, Resident B | A posts bounded video; B can play/pause, mute/unmute with clear state, and retry a refreshed signed URL after forced expiry. |
| Comments | Resident A, Resident B | B adds a comment to A’s unlocked post; both see it after server refresh; locked posts refuse comment creation honestly. |
| Reactions | Resident A, Resident B | Do not test until a guarded reaction implementation exists. If the feature remains absent, reaction counts must be hidden. |
| Sharing | Resident A | Do not test until an explicit safe sharing design exists. Shared output must not expose private signed URLs. |
| Empty/inert actions | Resident A and staff role | Every tappable card either performs a verified action or is not presented as actionable. |

## Security Boundaries That Must Remain Intact

The remediation must remain within the approved isolated non-production project until expressly authorized otherwise. It must not use a service-role key in the Android client, bypass RLS, expose Storage object paths or signed URLs through sharing, use direct privileged table writes as a permission workaround, weaken MFA/AAL2 requirements, or convert source-only checks into false runtime evidence.

## Recommended Order of Work

1. Correct canonical profile-photo synchronization into Community projections and test it with two residents.
2. Remove or truthfully disable reaction counts, follow filters, saved/following/recent no-op cards, and staff no-op controls until their real workflows exist.
3. Design and implement guarded reactions and safe post sharing as separate backend/client changes, with RLS/RPC/audit review.
4. Add explicit video mute/unmute controls and accessibility state labels.
5. Run fresh isolated authenticated and on-device validation, then reattempt a full debug package in a stable build environment.

**Release status remains NO-GO.**
