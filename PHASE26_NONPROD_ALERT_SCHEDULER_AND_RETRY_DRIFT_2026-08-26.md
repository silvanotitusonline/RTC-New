# Phase 26 — Isolated Non-Production Alert Scheduler and Retry Drift

**Date:** 2026-08-26  
**Environment boundary:** Read-only inspection of approved isolated non-production project `eqwstpdjoineycrkhpht` only. **No project configuration, database object, scheduler, extension, secret, Edge Function, production system, or remote Git branch was changed.**

## Finding

A read-only catalogue inspection found that the approved isolated non-production project currently contains the obsolete `ops_retry_failed_community_alert(uuid, text, text, uuid)` RPC, together with the historical alert scheduler infrastructure that the present recovery policy prohibits.

> This is a material source/backend and policy drift. The restored Android client correctly has no retry workflow, but the server object remains present. Client removal alone is not a server-side security control.

## Observed Object Metadata

No application records, alert content, credentials, Vault values, scheduled request headers, URLs, or function source bodies were retrieved during this inspection.

| Object class | Observed isolated non-production object | Status against current recovery constraints |
|---|---|---|
| Obsolete RPC | `public.ops_retry_failed_community_alert(uuid, text, text, uuid)` | **Present; must remain unavailable to clients.** |
| Scheduler | `cron.job` named `rtc-community-alert-schedule` on `* * * * *` | **Present; prohibited by the current recovery boundary.** |
| Extension | `pg_cron` | Present; no change performed. |
| Extension | `pg_net` | Present; no change performed. |
| Extension | `supabase_vault` | Present; no change performed. |
| Related helper | `public.assert_rtc_alert_dispatch_secret(text)` | Present; historical service-dispatch path, not used by the restored Android client. |
| Related maintenance functions | `publish_due_community_alerts()` and `archive_expired_community_alerts()` | Present; not assessed as safe to remove in this checkpoint. |

The local recovered source still contains the historical definitions in `20260822050000_release1_operations_hub_foundation.sql`, `20260822053000_harden_operational_maintenance_and_alert_dispatch.sql`, and `20260822053500_enable_pg_net_for_protected_alert_scheduler.sql`. The isolated migration catalogue contains later alert/operations recovery entries, but the matching source-controlled migration files are absent from the restored archive.

## Safety Decision

No Supabase mutation was made. In particular, this checkpoint did not unschedule the job, remove extensions, inspect Vault secrets, invoke any dispatcher, deploy an Edge Function, or restore the retry workflow in Android.

A source-only reconciliation migration may be prepared to ensure a fresh reconstructed baseline explicitly removes the obsolete retry RPC, removes the exposed dispatch-secret helper, and unschedules the known app-owned job. It must be clearly marked **not applied**. Removal of extensions, Vault entries, or the remaining maintenance functions requires a separate dependency and ownership assessment; it is not safe to infer that they are app-exclusive from the available metadata.

## Readiness Impact

This discovery preserves the overall **NO-GO** status. Production remains untouched, but isolated non-production cannot be treated as a fully compliant recovery baseline until the drift is remediated through an authorized, separately verified change and real-session regression testing.
