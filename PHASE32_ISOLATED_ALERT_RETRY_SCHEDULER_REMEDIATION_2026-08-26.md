# Phase 32 — Isolated Alert Retry and Scheduler Remediation

**Date:** 2026-08-26  
**Authorization:** User-confirmed, narrow remediation of the approved isolated non-production project only.  
**Target:** `eqwstpdjoineycrkhpht`  
**Production:** `pbzzfzfgwzwdstvnwzqu` was not queried, changed, or deployed.

## Scope

The remediation was limited to three preflight-confirmed historical objects in the approved isolated project:

| Object | Preflight state | Action |
|---|---:|---|
| `public.ops_retry_failed_community_alert(uuid, text, text, uuid)` | Present | Dropped. |
| `public.assert_rtc_alert_dispatch_secret(text)` | Present | Dropped. |
| `cron.job` named `rtc-community-alert-schedule` | Present on `* * * * *` | Unscheduled. |

The transaction did **not** read or delete Vault data, remove `pg_cron`, `pg_net`, or `supabase_vault`, alter Storage policies, modify application records, deploy/invoke Edge Functions, use service credentials, mutate remote GitHub, or touch production.

## Formal Migration Record

The initial narrow transaction succeeded and was immediately verified. Because direct DDL does not create a formal Supabase migration record, the same idempotent SQL was then registered through the migration interface under:

| Field | Value |
|---|---|
| Recorded migration version | `20260826033632` |
| Recorded migration name | `reconcile_obsolete_alert_retry_and_dispatch_baseline` |
| Migration result | Success |

The final verification confirmed all three targeted objects were absent and this record was present.

> This removes the isolated drift source. It does not approve a production deployment or substitute for real-session and device-level evidence.

## Post-Change Security Advisor Baseline

The isolated security advisor still reports **64** authenticated `SECURITY DEFINER` warnings. Those warnings are pre-existing authorization obligations requiring individual review; they are not evidence that the removed retry RPC or schedule remains. The removed retry function name has zero occurrences in the advisor output. The aggregate scan reported zero `rls_disabled` and zero `rls_enabled_no_policy` occurrences.

## Remaining No-Go Gates

1. Restore the owner-only ignored synthetic runtime files and run the focused `support-cases` real-session slice against the isolated project, recording only redacted results. After the environment reset, the runtime properties, synthetic auth vault, test-state file, and TOTP vault are all absent; their contents were not read. The restored runner passed `node --check` and still exposes the focused `support-cases` command.
2. Execute the P0 synthetic device cases documented in `PHASE30_SYNTHETIC_DEVICE_MEDIA_VALIDATION_PROTOCOL_2026-08-26.md`.
3. Review the remaining `SECURITY DEFINER` warning obligations function by function; they must not be treated as blanket approval.
4. Restore a valid GitHub integration credential or import the delivered Git bundle before any remote branch review. No merge or production push is authorized by this checkpoint.

**Overall status remains NO-GO.**
