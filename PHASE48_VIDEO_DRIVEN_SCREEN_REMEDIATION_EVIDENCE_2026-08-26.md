# Phase 48 — Video-Driven Screen Remediation Evidence

**Status:** Local recovery branch only. **No APK is packaged or delivered by this phase.**  
**Production posture:** No production Supabase query, migration, deployment, or configuration change was performed.

## Inputs and scope

This remediation follows the user-supplied application walkthrough, the existing approved graphite/emerald reference specifications, and the current local Android source. The walkthrough identified visual inconsistency and functional Community/Profile defects that were not acceptable for a release candidate. The work therefore follows a one-screen-at-a-time rule recorded in `PHASE47_SCREEN_BY_SCREEN_REMEDIATION_CHECKLIST_2026-08-26.md`.

## Community screen — source remediation completed

| Walkthrough finding | Source-level remediation | Validation status |
|---|---|---|
| Two reaction/comment presentations appeared on a post. | Removed the duplicate non-action summary from `RtcCommunityFeedCard`; one explicit Like / Comment / Share action row remains. | Covered by source contract. |
| Raw ISO timestamps appeared in feed cards. | Feed cards now receive the existing `relativeTimeLabel(post.createdAt)` value. | Covered by source contract. |
| Guideline acknowledgement did not appear to unblock posts/comments. | A pending post or comment is now resumed only after the authoritative guideline action succeeds; cancellation clears the pending intent. | Covered by source contract; needs authenticated device/RPC test. |
| Raw parser text such as `Unexpected JSON token` was displayed. | Community action failures now emit safe, actionable user messages instead of parser or transport details. | Covered by source contract. |
| Posted image/video preview was not visible in the walkthrough. | Verified the authoritative feed maps embedded media to signed URLs and `CommunityMediaPreview` renders a preview whenever returned media exists. No fake media is inserted into authenticated feeds. | Source path confirmed; needs real feed-media device test. |
| Avatars showed initials. | Server avatar remains first priority; development-only synthetic portraits remain restricted to the build-only adapter. Profile write/refresh is addressed in the Account screen. | Needs authenticated device test. |

## Account and profile screen — source remediation completed

| Walkthrough finding | Source-level remediation | Validation status |
|---|---|---|
| Generic light composition. | `SYSTEM` preference now defaults to the approved graphite system; explicit Light remains user-selectable. | Covered by source contract. |
| Selected image could not be read. | Replaced broad `GetContent` selection with the Android visual-media picker and safe user guidance for unsupported images. | Covered by source contract; device test required. |
| Saving profile photo appeared to hang. | Added a 45-second operation bound, explicit success state, safe failure messages, and preserved session/feed/detail rehydration after success. | Covered by source contract; authenticated upload test required. |
| Inert activity rows were presented as actions. | Replaced the rows with one non-clickable truthful availability status. | Covered by source contract. |

## Home, Explore, and Support — source remediation completed

| Screen | Source-level correction |
|---|---|
| Home | Preserves 16dp dashboard geometry, 76dp/6dp progress ring, and exact Projects/Centres/Opportunities routes. Quick Access Projects/Centres now open their exact directories. A Help callback is labelled Help, not Customise. |
| Explore | Retains connected category rows and real published counts; removes the unsupported 100% progress bar and describes the surface as a directory overview. |
| Support | Retains server-backed case state; Find a centre now deep-links to the actual Centres directory instead of switching to a local placeholder. |

## Onboarding and administrator workspace

Both surfaces were already rebuilt in earlier local phases with protected, connected flows. Their existing full-page reference composition now inherits the graphite default when an account previously had `SYSTEM` appearance selected. They still require a real-device check because this environment cannot boot an Android emulator without KVM.

## Validation executed

| Gate | Result |
|---|---|
| Source regression suite | `76/76` contracts passed. |
| Kotlin compilation | `:app:compileDebugKotlin` passed. |
| APK packaging | Intentionally not run in this phase. |
| Emulator/device workflow | Not available in this environment: x86 Android emulator requires unavailable KVM; ARM fallback is unsupported. |
| Authenticated Community/profile workflow | Not claimed: development synthetic credentials and real device session are unavailable. |

## Remaining hard gates before any APK is offered

1. Run the Community guideline acceptance, post/comment, Like/Share, signed media preview, and profile avatar refresh against an authenticated isolated account on an Android device or KVM-enabled emulator.
2. Confirm the selected profile image can be saved and that the refreshed server avatar appears in Account, Community feed, comments, and post detail.
3. Compare all screens against the approved mobile references on a real device, including status-bar-safe layout, dark default appearance, bottom navigation, and back behavior.
4. Verify a real Community post with image and video upload plays/renders from the authoritative feed.
5. Reassess release signing and remaining lint/dependency warning triage separately. This phase does not authorize production or APK delivery.
