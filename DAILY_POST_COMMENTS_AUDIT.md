# Daily Post Comments Audit and Implementation

**Audit date:** 19 September 2026  
**Scope:** Daily Post production schema, resident article detail, comment composition and rendering, ownership controls, replies, moderation, live refresh, publication counts, Room cache, and frontend/backend alignment.

## Findings

The Daily Post resident article screen previously had no comment section, no comment model, no comment repository methods, no ViewModel comment state, and no route wiring. The article could be liked and shared, but residents had no way to participate in a published article conversation. The production Supabase project already had the required comment system, so the defect was an incomplete frontend integration rather than a missing backend capability.

Production contains `daily_post_comments` with UUID identity, post ownership, author identity, optional parent comment, depth, body, visibility state, moderation reason, and created/updated timestamps. The body is constrained to 1–2,000 trimmed characters. The production trigger `daily_post_comment_count_sync` keeps `daily_posts.comment_count` synchronized when comments are inserted or deleted.

The production RPC surface is genuine and now used directly by Android:

| RPC | Purpose | Boundary |
| --- | --- | --- |
| `daily_post_comments_page_v1` | Bounded article comment page | Anonymous and authenticated read; only visible comments on published articles |
| `daily_post_comment_create_v1` | Create a top-level or nested comment | Authenticated; published article required; 12 comments/minute rate limit |
| `daily_post_comment_update_v1` | Edit an author’s visible comment | Authenticated; author-only |
| `daily_post_comment_delete_v1` | Remove an author’s comment or moderator-remove it | Authenticated; author or moderator; soft-delete body/state for auditability |
| `daily_post_comment_moderate_v1` | Hide a comment with reason and audit event | Moderator/system-admin boundary; reason required and audit recorded |

The read RPC excludes deleted comments and requires the related Daily Post to be published. Anonymous reads receive visible comments; authenticated users additionally receive their own comments and authorized moderator views. No service-role credential is embedded or used by the Android client.

## Implemented frontend behavior

The published article now contains a dedicated **Comments** section after the article engagement controls. It displays the authoritative article comment count, shows visible comments in chronological order, presents a friendly empty state, and includes a refresh action. When the article is open, the route refreshes comments every 15 seconds because the production table is not currently included in the `supabase_realtime` publication. This is a bounded live-update fallback that respects cancellation when the route leaves composition.

Authenticated users can compose a comment directly inside the article screen. The composer trims input, enforces the same 2,000-character limit as Supabase, displays a live character counter, disables duplicate submission while the request is active, and reports server failures without clearing successful state incorrectly. Successful creation reloads the authoritative comment page.

Users can reply to a visible comment. The reply uses the production `parent_id` parameter and is visually indented by a bounded depth of two levels to avoid unbounded horizontal growth. The backend remains authoritative for actual depth validation and storage.

A user can edit or remove only their own comment. The Android UI derives ownership from the authenticated session ID and does not rely on display names or client claims. Edit uses the production update RPC. Remove uses the production delete RPC, which preserves the comment row as a deleted audit-safe record while removing its body from the public result.

Moderators and system administrators see a **Hide** action. Hiding requires a 3–500 character reason, calls the production moderation RPC, removes the comment from the public page after refresh, and produces a server-side audit event. Ordinary residents do not receive this control, and the database function independently enforces the role boundary.

## Article feed relationship

The remote Daily Post article row now maps `comment_count` into the article domain model and Room cache. This allows the article feed and detail screen to show the server-maintained count rather than fabricating a local count. Room version 11 adds the count column with a forward migration from version 10. The resident comments themselves are fetched from Supabase rather than stored as an unbounded second local source of truth; this prevents stale or deleted comments from lingering in the article conversation.

The article detail route loads the selected article and its comments together. A comment mutation reloads the authoritative page. The route refreshes comments periodically while visible, so a comment posted by another user becomes visible without requiring the resident to exit and reopen the article. Because the production table is not in the Realtime publication, this is polling rather than websocket delivery; the audit report intentionally describes this distinction rather than claiming true Realtime behavior.

## Usability and accessibility decisions

The composer has a large multiline target, visible character count, explicit action labels, and disabled states during network operations. Edit and remove actions are placed on the author’s own comment, while moderation is separated as a staff-only hide action. Destructive removal requires confirmation. Moderation requires a reason rather than a silent disappearance. Refresh has a content description, and loading state is visible through a progress indicator.

The comment list is intentionally bounded by the production RPC at 100 rows per request in the client and 200 rows per request in the database. The implementation now uses the RPC’s `(created_at, id)` cursor parameters and exposes a **Load older comments** control when a full page is returned. The ViewModel merges older pages by comment ID and prevents concurrent pagination while a mutation is pending.

## Validation

The repository contract runner completed **204 of 206** contracts. All four new Daily Post comment contracts passed. The two failures are pre-existing unrelated contracts concerning community upload ownership and account-scoped local drafts. `git diff --check` passed.

Android compilation remains environment-limited in this sandbox because the Android SDK is not configured. The implementation should therefore be built by the repository’s GitHub Android workflow, which has the required SDK setup. A device or Compose screenshot pass is still recommended for keyboard behavior, long comments, nested replies, TalkBack labels, and tablet-width layout.

## Remaining improvements

The current route polling can be replaced with Realtime after `daily_post_comments` is deliberately added to the Supabase publication and the client subscribes to a post-scoped channel. Production verification also found that the existing count trigger increments on insert and decrements only on physical delete, while the user delete RPC performs an audited soft delete and moderator hiding performs an update. Therefore `daily_posts.comment_count` currently represents historical inserted comments more closely than visible comments. The UI should not label it as a guaranteed visible-comment count until the trigger policy is clarified or the count is changed to account for visibility-state transitions. A user-facing report-comment action is not currently present in the Daily Post-specific production RPC surface; if required, it should be added as a separate authenticated, rate-limited moderation-report RPC rather than reusing an unrelated community-post report payload.
