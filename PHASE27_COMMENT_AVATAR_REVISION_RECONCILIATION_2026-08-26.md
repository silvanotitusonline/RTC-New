# Phase 27 — Comment Avatar Revision Reconciliation

**Date:** 2026-08-26  
**Environment boundary:** Read-only inspection of the approved isolated non-production Supabase project `eqwstpdjoineycrkhpht`; local Android source validation only. **No Supabase migration, Storage mutation, application record query, user-media access, production change, Edge deployment, remote push, or APK distribution occurred.**

## Finding

The restored Android client already refreshes its session and Community content after a confirmed profile-photo upload or removal. Post-avatar URLs are cache-busted with the profile revision value from `community_post_feed`. The isolated `community_comment_feed` view, however, returned `avatar_path` without the corresponding community-profile revision. Comment-avatar URL generation therefore could not reliably invalidate an already cached image immediately after an avatar change.

> The issue was a projection/cache-coherence gap, not a failure of profile-media ownership policy, bounded image preparation, signed URL creation, or post-avatar refresh.

## Verified Contract

Read-only isolated metadata inspection showed that `community_comment_feed` selects `cp.avatar_path` and joins `community_profiles`, but did not expose `cp.updated_at`. The current project grants several table privileges to API roles; this checkpoint did not alter or normalize those grants because no complete ownership/access assessment was performed. No comment rows, user identifiers, profile paths, or signed URLs were retrieved.

## Source Changes

| File | Change |
|---|---|
| `supabase/migrations/20260825240000_reconcile_comment_avatar_revision.sql` | Adds only `cp.updated_at AS avatar_updated_at` to the `security_invoker` comment-feed projection. It intentionally does not change grants, RLS, Storage policy, profile data, or media delivery. The migration is source-only and was **not applied**. |
| `ProductionUxRepository.kt` | Adds `avatar_updated_at` to `CommunityCommentFeedRow` and passes it to `signedAvatarUrl(...)`, matching the existing post-avatar cache-busting behavior. |
| `test_android_compile_contracts.py` | Adds a contract requiring the DTO field, revision-aware comment avatar mapping, `security_invoker` view recreation, and no unrelated grant/policy statements in the new migration. |

## Local Validation

| Validation | Result | Notes |
|---|---:|---|
| Source-contract suite | **50/50 passed** | Includes comment-avatar revision parity and earlier recovery safeguards. |
| `testDebugUnitTest` | **Passed** | Only the known non-blocking Kotlin future-annotation warning appeared. |
| `assembleDebug` | **Passed** | Local debug assembly only; this is not a release artifact or distribution approval. |
| `lintDebug` | **Passed** | No lint baseline or suppression was added. |
| Device/emulator media test | **Not performed** | A physical Android device/emulator and a deliberately migrated isolated backend view are still required to observe post-change avatar refresh end-to-end. |

## Scope and Readiness

This checkpoint makes the restored source contract consistent with the desired comment-avatar cache behavior on a future reconstructed baseline. It does not change the current isolated project, so it does not prove live non-production behavior. The broader recovery remains **NO-GO** until the alert-retry/scheduler drift is remediated through a separately authorized and verified non-production action, source migrations are reconciled with actual applied history, and device-level media lifecycle tests are executed.
