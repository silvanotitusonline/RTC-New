# Daily Post production findings

Audit date: 2026-09-18.

The linked RTC production Supabase project is `pbzzfzfgwzwdstvnwzqu`.

Production contains a Daily Post backend despite the Android repository currently using only Room local storage. Tables include `daily_posts`, `daily_post_media`, `daily_post_publication_jobs`, `daily_post_preview_receipts`, `daily_post_audit_events`, `daily_post_translations`, and `daily_post_comments`. All seven tables currently contain zero rows.

The only recorded Daily Post migration is `20260913005802 daily_post_live_feature_activation`. The repository source tree does not contain this migration or the Daily Post SQL definitions, creating source/production drift.

Production Daily Post RPCs include:

- `daily_post_page_v1`
- `daily_post_get_v1`
- `daily_post_admin_page_v1`
- `daily_post_save_draft_v1`
- `daily_post_publish_v1`
- `daily_post_archive_v1`
- `daily_post_preview_next_v1`
- `daily_post_preview_mark_v1`
- `daily_post_claim_due_jobs_v1`
- `daily_post_execute_job_v1`
- `daily_post_complete_job_v1`
- `daily_post_comment_*` RPCs
- `daily_post_validate_blocks`
- `daily_post_storage_post_id`

Important production grants: public page/get/comment-read functions are callable by anon/authenticated; save/publish/admin/archive/preview/comment mutation functions are authenticated-only; scheduler claim/execute/complete functions are not callable by anon/authenticated.

Current Android Daily Post implementation uses a Room table `daily_post_articles` and `RoomDailyPostRepository`. Publishing inserts locally, immediately updates local flows, triggers a local Android notification, and never calls Supabase. The `coverImageUrl` field exists in the local model but the Studio has no media picker/upload path. Article content is a single plain-text field. There is no rich-text push-notification editor or notification preview. Publish failure is not represented as a Result/state; the ViewModel reports success after the repository call returns and does not catch/display failures. The Live Preview `Publish Now` path bypasses the title/content validation used by the form button.

Resident Explore renders whatever the local Daily Post ViewModel observes. It supports search/category filters and template cards. Detail renders plain text, template-specific headers, quote/highlight blocks, likes, and sharing. The local feed can appear immediately on the same device, but there is no cross-device or cross-install publication propagation from the admin to residents.
