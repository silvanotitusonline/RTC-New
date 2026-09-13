# Full RTC logo and live Daily Post verification

Verified 13 September 2026. Work remains in draft PR #11; main is not changed.

## Branding

The supplied transparent PNG is the canonical image, copied byte-for-byte to
`rtc_community_logo_transparent.png` and `rtc_logo_mark_transparent.png`.
SHA-256: `0f870166cf83380e85e821203c16ffc2195215a79dd50c41b1f783dc2dd7d55d`.

Welcome/onboarding use `RtcLogoMark` with `ContentScale.Fit`. Splash and launcher
assets use the complete original canvas with proportional scaling and transparent
padding. No cropping, redrawing, or recoloring is performed. Every foreground
pixel fits within the adaptive icon safe circle. `tools/prepare_brand_assets.py`
reproduces the derivatives, including the alternative launcher icons.
Android applies its own outer icon mask/background; the full artwork is inset
inside that mask. Device screenshots and actual installation remain unverified.

## Hosted backend changes

Applied the reviewed, feature-only transactional bundle rendered by
`tools/daily_post_deployment_bundle.py` to RTC non-production and production.
It creates Daily Post tables, private storage, RLS, grants, MFA-aware editor
checks, RPCs, scheduler secret and minute cron, plus delivery readiness fixes.
This deployment does not imply that the unrelated repository migration history
has been reconciled with either hosted project.

Deployed `daily-post-language` (JWT required) and `daily-post-scheduler`
(dedicated Vault secret required). The scheduler supports an authenticated
`{"action":"check"}` request. It validates FCM without delivering messages,
translates a small synthetic sample, and attempts TTS plus private upload,
signed playback fetch, and cleanup. Provider errors return bounded codes;
credentials, device tokens, audio URLs, and publication content are not exposed.

The new migration also reconstructs the canonical delivery ledger on fresh
databases, with RLS and service-only access. It permits `DAILY_POST_JOB` delivery records, retries failed jobs
after five minutes, limits attempts to five, and reclaims workers after a
15-minute lease. Only new FCM acceptances count as pushed; an HTTP 404 alone
does not delete a device registration without FCM's `UNREGISTERED` reason.
FCM acceptance is not proof of delivery to a device.

## Live evidence

| Check | Result |
| --- | --- |
| Scheduling, publication execution, delayed retries, retry cap, stale-worker recovery | Passed on both hosted projects using the transactional SQL probe. All synthetic records rolled back. |
| Production cron | Active every minute; repeated successful cron runs and HTTP 200 scheduler responses with zero pending jobs. |
| Missing scheduler secret | HTTP 401, including for diagnostic mode. |
| Production Firebase authentication | Successfully reached FCM with the existing Vault service account. |
| Production device validation | Both existing registrations returned `HTTP_404_UNREGISTERED`; zero valid devices. No notification was delivered by this check. |
| Production translation | Blocked: `AI_CONFIGURATION_UNAVAILABLE` (`GEMINI_API_KEY` absent). |
| Production narration | Blocked: `TTS_HTTP_403_SERVICE_DISABLED` (Cloud Text-to-Speech disabled). Storage/playback probe cannot proceed until synthesis works. |
| Non-production providers | Missing FCM, Gemini and TTS configuration. |

Production diagnostic request 31394 returned HTTP 200 at
2026-09-13T01:02:40.944Z with the above provider results. Request 31397 verified
HTTP 401 without the scheduler secret. The check does not claim jobs, broadcast
posts, delete expired registrations, or send resident notifications.

## Remaining external setup

1. Install/open the updated Android app on the intended test device while signed
   in and allow notifications, so it registers a current Firebase token. Then
   validate again and perform a specifically targeted delivery/open check.
2. Configure `GEMINI_API_KEY` in Supabase Edge Function secrets for each intended
   environment. Do not commit keys or paste them into issues or chat.
3. Enable Google Cloud Text-to-Speech in the existing service account's project
   and provide the required billing/API permissions. The language function can
   use the existing Vault credential or `GOOGLE_SERVICE_ACCOUNT_JSON`.
4. Re-run the protected check, then verify translation and narrated playback
   in the Android UI. Production publication/broadcast is still a separate
   editorial action; these checks did not publish community content.

For database checks, run `tools/daily_post_live_scheduler_probe.sql` only against
an environment with no publication jobs; the script refuses otherwise and rolls
back its fixtures. The equivalent pgTAP coverage is
`supabase/tests/daily_post_scheduler_test.sql`.
