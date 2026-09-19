# Audit Priorities 1–8 Completion Report

**Scope:** RTC Community Daily Post comments, offline recovery, moderation, reporting, Realtime delivery, pagination, and production database boundaries.

## Completion summary

All eight audit priorities have been implemented or covered by executable source/database tests. The Daily Post reporting and visibility-read corrections were deployed to both RTC Community Production and the approved non-production project. The Android source suite passes locally; SDK-backed GitHub CI was also triggered, but the run on `main` evaluated the pre-existing remote revision rather than these uncommitted workspace changes and failed on the two stale schema-version contracts.

## Priority results

| Priority | Result | Evidence |
|---|---|---|
| 1. Community upload ownership recovery | Complete | Owner-bound outbox creation, owner-bound recovery queries, authenticated worker guard, and passing source contracts. |
| 2. Account-scoped local drafts | Complete | Composite `(owner_user_id, area)` key, owner-bound reads/mutations, forward Room migrations, and passing source contracts. |
| 3. Daily Post staging transition tests | Complete | Rolled-back staging matrix executed successfully; all visibility/count cases passed. The checked-in runner also includes the security/performance suite. |
| 4. Android Realtime client validation | Complete at source/lifecycle level | Post-scoped `daily_post_comments` channel, `post_id` filter, debounce, route-owned teardown, and source contracts. SDK-backed CI was triggered separately; the reported failure came from the remote pre-change `main` revision. |
| 5. Daily Post moderation lifecycle | Complete | Existing authenticated author delete, moderator hide, audit event, and visible-count transition paths reviewed; pagination was corrected from `state <> 'DELETED'` to `state = 'VISIBLE'`. |
| 6. Comment pagination and ordering | Complete | Forward/backward cursor functions, `(created_at,id)` ordering, bounded limits, visible-only reads, duplicate-safe client merge, and visible cursor index. |
| 7. Community reporting and moderation | Complete for Daily Post comments | Added `daily_post_comment_reports`, authenticated rate-limited `daily_post_comment_report_v1`, RLS/RPC-only boundary, audit event, Android repository/ViewModel/UI wiring. |
| 8. Production security and performance audit | Complete | Realtime publication verified, SECURITY DEFINER/search-path boundary reviewed, authenticated-only report execution verified, direct table access denied, RPC-only manifest updated to 26 tables, and cursor/report indexes deployed. |

## Database deployment

Applied to RTC Community Production:

- `daily_post_comments_realtime_and_visible_count`
- `daily_post_comment_reporting`
- `daily_post_comment_visible_read_boundary`
- `daily_post_comment_visible_cursor_index`

Applied to RTC Community Non-Production:

- `daily_post_comments_realtime_and_visible_count`
- `daily_post_comment_reporting`
- `daily_post_comment_visible_read_boundary`
- `daily_post_comment_visible_cursor_index`

## Verification results

The local source regression suite passes **216/216 contracts**. The RPC authorization checker validates **10 anonymous allowlisted RPCs and 26 RPC-only tables**. Shell syntax, JSON syntax, whitespace, and migration artifact checks pass.

The staging transition matrix was executed in a rolled-back transaction and returned `passed`. It covered visible/hidden/deleted inserts, all visibility transitions, body-only updates, cross-post moves, physical deletes, and non-visible rows. Staging security checks returned true for RLS, authenticated report execution, denied anonymous execution, Realtime publication membership, visible-only cursor functions, and the visible cursor index.

The Android CI workflow was triggered at [run 35424897993](https://github.com/silvanotitusonline/RTC-New/actions/runs/35424897993). Its failure was on the remote `main` revision before these workspace changes were committed: the workflow reported 210/212 contracts and the two stale Room-version contracts. The current workspace suite has those contracts corrected and passes 216/216.
