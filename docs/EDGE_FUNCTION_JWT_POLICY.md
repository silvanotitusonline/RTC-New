# Edge Function JWT Verification Policy

Last reviewed: 17 September 2026

## Default rule

`verify_jwt: true` for every Edge Function unless there is an explicit, documented exception below.

## Intentional `verify_jwt: false` functions

| Slug | Reason | Alternative auth |
|------|--------|------------------|
| `rtc-account-privacy-public` | Serves a static public HTML privacy / account-deletion explainer page. No data mutation, no secrets. | None required (public page). |
| `daily-post-scheduler` | Invoked by a trusted scheduler / cron. JWT would be impractical. | Custom header secret verified via `assert_daily_post_scheduler_secret` RPC. |
| `service-centre-payment-webhook` | **Retired.** Returns HTTP 410 only. Previously needed webhook signature verification instead of user JWT. | N/A — endpoint permanently retired. |
| `service-centre-payment-create` | **Retired.** Returns HTTP 410 only. | N/A — endpoint permanently retired. |

## Active functions with `verify_jwt: true`

- `rtc-privacy-requests`
- `rtc-fcm-dispatch`
- `dispatch-community-alerts`
- `community-media-url`
- `report-community-post`
- `rtc-admin-ai`
- `service-centre-notify`
- `civic-report-media-url`
- `community-comment-notify`
- `daily-post-language`
- `rtc-account-privacy-worker`

These functions additionally perform role checks and rate limiting via shared `_shared/auth.ts` helpers where applicable.

## Scheduler / webhook secret pattern

Functions that must be callable without a user JWT (schedulers, future webhooks) MUST:

1. Keep `verify_jwt: false` only when necessary.
2. Validate a high-entropy shared secret via a SECURITY DEFINER RPC (e.g. `assert_*_secret`).
3. Never accept caller-supplied account identifiers for privileged work; always derive identity from the verified secret context or service role.
4. Document the exception in this file when the function is introduced or changed.

## Retired service-centre payment surface

`service-centre-payment-webhook` and `service-centre-payment-create` are intentionally retained as 410 Gone stubs so existing clients or bookmarks receive a clear permanent-removal signal. They perform no database work and hold no credentials. Full slug deletion can be scheduled after a suitable deprecation window.
