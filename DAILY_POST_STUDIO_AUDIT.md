# RTC Daily Post Studio and Resident Feed Audit

**Audit date:** 18 September 2026  
**Scope:** Administration Dashboard Daily Post Studio, Supabase production publication lifecycle, resident Daily Posts feed, templates, previews, notifications, media, synchronization, and operational robustness.

## Executive assessment

The Daily Post user interface presents a polished editorial concept, but before this change it was not connected to the production Daily Post system. Administration actions wrote only to a device-local Room table, resident Explore rendered that same local cache, and publishing triggered only a local Android notification. Consequently, an administrator could see an article appear immediately on the device used to publish it while other residents, other devices, and fresh installations saw no publication at all. This was the central operational defect.

The linked production Supabase project already contains a substantially more capable Daily Post backend. It has authoritative `daily_posts` records, structured `content_blocks`, media metadata, preview receipts, translations, audit events, publication jobs, scheduler protections, and authenticated RPCs for draft saving, publication, archival, admin listing, and resident paging. The project currently contains no Daily Post rows, which is consistent with the client having bypassed that backend rather than evidence that the backend is unusable.

The client has now been changed to use the genuine production RPC surface. Supabase is authoritative for drafts, publication state, feed paging, and archival; Room is retained only as an offline read cache. Remote refresh reconciles the cache and removes published articles that no longer appear in the authoritative resident page. Mutation failures are surfaced instead of being reported as successful. The Studio now exposes explicit push and in-app preview preferences, and both normal and Live Preview publication paths validate required content.

## Current end-to-end workflow

| Stage | Previous behavior | Current behavior | Assessment |
|---|---|---|---|
| Compose | Compose locally in a large single screen with six visual templates, accent palette, headline, subtitle, body, quote, highlights, and author fields. | Same editor, with stronger publication validation and notification controls. | Good concept; still needs a structured block editor and media picker. |
| Draft save | Inserted into Room only. | Calls `daily_post_save_draft_v1`, then caches the saved result locally. | Correct authoritative boundary. |
| Preview | Feed-card preview and a simplified full-reader preview; no notification preview. | Existing previews remain, and a notification preview is shown when push is enabled. | Improved, but full-reader preview should share the exact resident renderer. |
| Media | `coverImageUrl` existed in the model, but no Studio media picker, upload, signed URL, or media-row persistence was wired. | Production media bucket and `daily_post_media` table remain available for the next media slice. | Still a feature gap; do not describe the current Studio as media-capable. |
| Publish now | Wrote a local published row and showed a local notification. | Saves through `daily_post_save_draft_v1`, then calls `daily_post_publish_v1` with `NOW`. | Correct server lifecycle and audit path. |
| Schedule | No scheduling control existed in the Android Studio. | Production scheduler exists, but the Android Studio does not yet expose schedule time. | High-value next increment. |
| Push notification | Always locally generated from title/subtitle/body; no editorial toggle or server job. | Push and preview-popup preferences are captured and sent to the production publish RPC. | Correct control plane; delivery worker must be monitored independently. |
| Resident feed | Rendered Room rows from the same device. | Hydrates from `daily_post_page_v1` and uses Room only for offline continuity. | Cross-device propagation is now possible. |
| Archive/delete | Destructive local delete. | Calls the production archive RPC and removes the archived item from the local cache. | Safer for auditability and link integrity. |
| Updates | Local Flow updated quickly on the publishing device. | Remote refresh is performed when the Daily Post ViewModel starts; cache reconciliation prevents stale published items. | No realtime subscription or periodic refresh has been added yet. |

## Templates and content presentation

The six available templates are Modern Blog, Canva Hero Graphic, Civic Spotlight, Magazine Feature, Breaking Bulletin, and Minimalist Essay. They provide useful visual variety and are consistently dispatched through `DailyPostTemplateCard`. The resident detail screen also applies template-specific headers and preserves the article’s category, reading time, byline, highlights, pull quote, body, reactions, and share action.

The visual format is suitable for short official updates and editorial announcements. The current body field, however, is a single plain-text `OutlinedTextField`. It does not provide headings, links, emphasis, lists, inline media, captions, call-to-action blocks, or semantic accessibility metadata. The client adapter now converts the legacy fields into genuine production `content_blocks` so current articles can publish, but that conversion is intentionally conservative. A state-of-the-art Studio should edit blocks directly and preview the same block tree used by the resident reader.

The production validator accepts up to 40 blocks and recognizes `HEADLINE`, `PARAGRAPH`, `SUBHEADING`, `IMAGE`, `VIDEO`, `MEDIA_GALLERY`, `PULL_QUOTE`, `INFO_CALLOUT`, `DIVIDER`, `QUOTED_PUBLICATION`, and `CTA`. This is the correct foundation for a richer editor. The Android UI currently emits headline, subheading, paragraph, info callout, and pull quote blocks; the remaining block types are not yet exposed in the Studio.

## Media assessment

Production has a private `daily-post-media` storage bucket with a 50 MiB limit and JPEG, PNG, WebP, MP4, and WebM allow-listing. The `daily_post_media` table records storage path, media type, MIME type, alt text, ordering, dimensions, and duration. This is an appropriate secure design because media is private by default and can be served with signed URLs.

The Android Studio does not currently use that capability. Its `coverImageUrl` property is only a passive model field and is not populated by a picker or upload pipeline. The next implementation should add a bounded image/video picker, client-side size/type validation, upload to a post-scoped storage path, media-row insertion through an authenticated RPC, alt-text enforcement for images, signed URL hydration, retry/cleanup behavior, and a visible upload-progress state. Media should be attached to structured blocks rather than stored as an unvalidated headline decoration.

## Notification and preview assessment

Previously, publication invoked a local notification directly inside the ViewModel. That is not a reliable push system: it cannot deliver to other residents, does not provide delivery telemetry, is suppressed by local permission state, and can claim success even when no server publication occurred.

The production backend has `push_enabled`, `preview_popup_enabled`, publication jobs, a dispatch key, attempt count, error, claimed time, and completion time. `daily_post_publish_v1` creates a publication job when push is enabled, while scheduler execution functions are not callable by ordinary authenticated clients. The updated Studio exposes push and in-app preview choices and shows the administrator the actual headline/summary notification preview before publication. The Android local notification remains a convenience signal after successful publication, not the source of truth.

The remaining improvement is a delivery-status panel in the Administration Dashboard showing pending, claimed, completed, and failed job counts, retry state, and the last error without exposing scheduler secrets.

## Timing and update propagation

Before the change, same-device appearance was effectively immediate after the Room insert and Compose Flow emission, but that apparent speed was misleading because no server propagation occurred. A new install or another resident would not see the article. After the change, the publication transaction is accepted by Supabase immediately, and `daily_post_page_v1` exposes the published record to resident clients. The scheduler separately handles delayed jobs and push dispatch.

The client currently performs an initial authoritative refresh when `DailyPostViewModel` is created. It does not yet subscribe to Supabase Realtime for Daily Post changes and does not schedule periodic refresh while the Explore screen remains open. The recommended next step is a bounded realtime invalidation channel or foreground refresh policy with backoff. The resident feed should show a stale/offline indicator when the cache is older than a defined threshold rather than silently presenting old content.

## Production Supabase verification

The linked production project is `pbzzfzfgwzwdstvnwzqu`. The audit confirmed the following genuine RPCs and their access boundaries:

| RPC | Purpose | Access observed |
|---|---|---|
| `daily_post_page_v1` | Published resident paging | Anonymous/authenticated read path |
| `daily_post_get_v1` | Published article lookup | Anonymous/authenticated read path |
| `daily_post_admin_page_v1` | Editor/admin article library | Authenticated; function checks editor role |
| `daily_post_save_draft_v1` | Create/update draft | Authenticated; function checks editor role |
| `daily_post_publish_v1` | Publish now or schedule | Authenticated; function checks editor role |
| `daily_post_archive_v1` | Preserve history while removing from public feed | Authenticated; function checks editor role |
| `daily_post_claim_due_jobs_v1` | Scheduler job claim | Not executable by ordinary clients |
| `daily_post_execute_job_v1` | Scheduler execution | Not executable by ordinary clients |
| `daily_post_complete_job_v1` | Scheduler completion | Not executable by ordinary clients |
| `daily_post_validate_blocks` | Structured block validation | Publicly callable validator |

The production tables currently contain zero rows across posts, media, jobs, preview receipts, audit events, translations, and comments. The migration registry contains `20260913005802_daily_post_live_feature_activation`. That migration is present in production but not represented in the repository’s `supabase/migrations` directory, which is a source/production drift risk for disaster recovery, local CI, and future environments. The client integration uses the genuine deployed names and payloads, but the migration source should be recovered into version control in a separate database-reconciliation task rather than recreated from memory.

## Robustness findings addressed

The client previously reported success unconditionally after repository calls. The ViewModel now wraps publication, draft save, and archive operations, reports server errors, and avoids triggering the local success path when an RPC fails. The Live Preview `Publish Now` button previously bypassed required headline/body validation; it now applies the same guard as the form publication button. Remote hydration now reconciles archived or otherwise absent publications out of the local published cache. Room was upgraded from version 9 to 10 with a non-destructive migration for notification preferences.

The client also previously treated a device-local insert as a publication. That boundary has been removed: draft save and publish use Supabase RPCs, and Room is populated from authoritative responses. This prevents the administration dashboard and resident feed from presenting a success state that exists only on one device.

## Recommended next increments

First, recover and commit the production Daily Post migration source, then add a disposable Supabase integration test that creates a draft, validates block rejection, publishes it, verifies resident paging, archives it, verifies resident invisibility, and checks that the audit event and publication job states are correct. This should run in the existing non-production database CI workflow.

Second, replace the single body field with a block editor supporting paragraphs, headings, callouts, pull quotes, links, CTAs, image/video blocks, alt text, and reordering. Use the same renderer for Studio preview and resident detail to eliminate preview drift.

Third, add media upload and scheduling. The production schema and private bucket already support the required operational controls; the missing work is the Android picker, signed URL lifecycle, cleanup, schedule picker, timezone display, and job-status UI.

Fourth, add realtime invalidation or a bounded foreground refresh policy. The resident feed should update after publication without requiring navigation away and back, and it should clearly communicate offline cache state.

Finally, add role-aware editorial workflow controls. The current Studio is available to content editors and system administrators through the route policy, while the production RPCs enforce the editor boundary. If review-before-publish is required, introduce a submitted/reviewed state and show audit history in the Studio rather than allowing an editor to mistake a saved draft for a live article.

## Validation record

The repository contract runner completed **200 of 202** contracts. The two failures are pre-existing unrelated contracts concerning community upload recovery ownership and account-scoped local drafts; they were already failing in the baseline run and are not caused by the Daily Post changes. The new Daily Post Supabase contract tests passed under the repository runner. `git diff --check` was included in the validation sequence. The independent Android `compileDebugKotlin` run was started after the contract runner; its final result should be treated as the authoritative compile check when returned.

## Changed implementation areas

The implementation changes are concentrated in the Daily Post repository, ViewModel, Studio, Room entity/database migration, and source contracts. The key files are `DailyPostRepository.kt`, `DailyPostViewModel.kt`, `DailyPostStudioScreen.kt`, `DailyPostEntity.kt`, `RtcDatabase.kt`, `AppModule.kt`, and `test_daily_post_supabase_contract.py`.
