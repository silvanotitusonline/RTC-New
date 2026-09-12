# Phase 36 — Community Truthfulness Remediation Evidence

**Date:** 2026-08-26
**Environment:** Approved isolated non-production baseline only (`eqwstpdjoineycrkhpht`)
**Production:** Not queried, changed, or deployed
**Status:** **NO-GO** for release pending real-session and device evidence

## Purpose

This checkpoint records the implementation of the Community defects identified in Phase 34 and planned in Phase 35. It distinguishes source-confirmed and isolated-schema-confirmed behavior from work that still requires an authenticated two-resident session and physical-device validation.

> The remediation intentionally preserves direct-table denial, RLS-backed reads, guarded server operations, and MFA/AAL2 administrator boundaries. It does not add a service credential, database password, Edge Function, scheduler, Storage-policy change, or production deployment.

## Implemented Remediation

| Area | Implemented outcome | Truthfulness and security property | Evidence status |
|---|---|---|---|
| Community avatars | The `profiles` after-write trigger now formally reconciles the derived `community_profiles` identity record when `display_name` or `avatar_url` changes. Feed and comment projections carry the Community profile revision for signed-URL cache invalidation. Feed cards render the projected avatar and fall back to initials. | The Android client does not write Community profile projections directly. A failed canonical update does not become a permanent local identity claim. | Isolated schema and source contracts verified; real two-resident propagation remains pending. |
| Profile updates | Server-confirmed display-name changes rehydrate the signed-in session, refresh Community feed data, and reload any open post detail. | The UI rebuild happens after the canonical server write, not as a permanent optimistic update. | Kotlin compilation and source contracts verified. |
| Likes | `public.toggle_community_post_like(uuid)` authenticates the caller, enforces Community-guideline acceptance and post visibility, serializes an actor/post pair, and returns only caller-specific Like state plus count. The feed exposes `viewer_has_liked`. | `anon` cannot execute the RPC. `authenticated` has no direct `INSERT`, `UPDATE`, or `DELETE` privilege on `community_reactions`. | Isolated aggregate grant/projection verification passed; real-session isolation test remains pending. |
| Like UI | Post detail has accessible Like/Unlike control, request-in-flight disablement, server-confirmed success/failure feedback, and server refresh instead of a permanent optimistic counter. | A local tap does not claim a successful reaction before the guarded RPC succeeds. | Kotlin compilation and source contracts verified. |
| Sharing | Android shares only neutral text plus a checked `rtc://community/post/{post-id}` URI. `MainActivity` parses that URI and routes it through the existing authenticated post-detail loader. | No post body, author detail, media URL, private signed URL, or entitlement is shared. A received link conveys no permission. | Kotlin compilation and source contracts verified; handset share-target test remains pending. |
| Video sound | The active `SignedVideoPlayer` now has an explicit, labelled mute/unmute control. It maps state to ExoPlayer volume `0f` or `1f`, stays within the active session, and the player continues to be released on disposal. | No microphone permission, recording, background-audio service, or durable sound preference was added. | Kotlin compilation and source contracts verified; physical playback/audio test remains pending. |
| Inert UI | The unavailable Following filter was removed. Saved items, Following, Recent Community, Work Queue, and moderation placeholders now read as non-interactive truthful status content where no working route exists. | The application no longer presents those unavailable controls as tappable features. | Source contracts verified; visual-device review remains pending. |

## Formal Isolated Migration

The formal source migration is:

```text
supabase/migrations/20260826070000_reconcile_community_avatar_sync_and_post_likes.sql
```

It was successfully applied only to `eqwstpdjoineycrkhpht` after two failed dry attempts that rolled back atomically because PostgreSQL forbids reordering existing view columns through `CREATE OR REPLACE VIEW`. The final form appends `viewer_has_liked` and `avatar_updated_at` to the existing view contracts, preserving dependent column order.

The post-application aggregate verification confirmed all of the following without reading accounts, posts, comments, media paths, or other user data:

| Verification | Result |
|---|---:|
| Like RPC exists | Yes |
| `authenticated` can execute the Like RPC | Yes |
| `anon` can execute the Like RPC | No |
| `authenticated` can directly insert/update/delete Community reactions | No / No / No |
| Post feed exposes `viewer_has_liked` | Yes |
| Comment feed exposes `avatar_updated_at` | Yes |
| Canonical-profile avatar-sync trigger exists | Yes |

## Validation Results

| Validation | Result | Limitation |
|---|---|---|
| Lightweight source contracts | **62/62 passed** | Source-level evidence only. |
| `:app:compileDebugKotlin` | **Passed** | Confirms Kotlin/Compose compilation only. |
| Isolated formal migration | **Succeeded** | Aggregate schema/grant checks only; no user data read. |
| Isolated security advisor | Completed | Reports 65 `authenticated_security_definer_function_executable` warnings, including the new Like RPC. Each remains an individual review obligation; this is not release approval. |
| Full debug APK packaging | Not rerun | The prior reset-era dex packaging stage stalled; a stable packaging run is still required. |
| Real authenticated Community session | Not run | Owner-only synthetic credential/TOTP vaults still require safe recovery. |
| Physical-device media validation | Not run | The documented synthetic-only protocol remains required. |

## Effort Plan Status

Phase 35 remains the detailed estimate record. The design-and-source implementation portions of avatar synchronization, Like, sharing, sound control, and inert-action cleanup are complete in this checkpoint. The remaining estimated effort is concentrated in secure synthetic-account runtime recovery, two-resident real-session testing, physical-device validation, stable APK packaging, and individual review of the outstanding security-definer warnings.

> **Release position:** The implemented features must not be described as production-ready yet. The remaining gates are specifically the verified behavior of two different synthetic residents, audio playback on a device, image/video upload and profile-photo propagation on a device, complete packaging, and security-review closure.

## References

[1]: https://supabase.com/docs/guides/api/securing-your-api "Supabase — Securing your API"
[2]: https://supabase.com/docs/guides/database/database-linter?lint=0029_authenticated_security_definer_function_executable "Supabase — SECURITY DEFINER linter guidance"
