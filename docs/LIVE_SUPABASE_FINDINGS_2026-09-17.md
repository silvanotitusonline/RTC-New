# Live Supabase Findings — 2026-09-17

Source: Supabase MCP inspection of project `pbzzfzfgwzwdstvnwzqu` (`RTC Community Production`) at 2026-09-17 21:52 UTC.

- Production project status: `ACTIVE_HEALTHY`, Postgres 17.6.1.155, region `eu-west-1`.
- Production does **not** contain `admin_get_moderation_dashboard_summary_v1()`; a lookup by function name returned no rows.
- Production does contain `admin_civic_report_page_v1(text,text,text,boolean,integer)`, a SECURITY DEFINER staff/MFA-guarded RPC returning bounded civic report review rows.
- Production contains `ui_configuration_admin_history(integer)`, `ui_configuration_create_draft(jsonb,text,uuid)`, `ui_configuration_effective_global()`, `ui_configuration_effective_global_home()`, `ui_configuration_publish_draft(uuid,text,text)`, and `ui_configuration_revert(uuid,text,text)`.
- Production tables relevant to administration/frontend integration include `operational_work_items`, `operational_incidents`, `official_notices`, `community_events`, `app_content`, `content_drafts`, `content_versions`, `ui_configuration_versions`, and `audit_events`.
- Production advisor findings include 25 RLS-enabled tables with no policies and 10 anonymous SECURITY DEFINER functions; the complete advisor output is stored by the MCP tool under `/home/ubuntu/.mcp/tool-results/2026-09-17_21-52-27.348286246_supabase_get_advisors_4e0fe5c1.json`.
- Production performance advisors include 47 unindexed foreign keys, plus unused-index and RLS init-plan findings; the relevant RLS init-plan issues were on daily post policies in the inspected output.
- Production bounded aggregate snapshot at inspection time: `operational_work_items` pending 0 / total 1; `moderation_items` pending 0 / total 0; `official_notices` pending 0 / total 5; `community_events` pending 0 / total 6.

Supabase documentation source checked before implementation: https://supabase.com/changelog.md
Relevant changelog note: Supabase restricted direct SQL changes to `auth`, `storage`, and `realtime` schemas in April 2025; custom objects belong elsewhere. No newer breaking change relevant to this implementation was identified in the selected changelog sections.


## Applied production changes

At 2026-09-17 21:58 UTC, the following migrations were applied successfully to `RTC Community Production` (`pbzzfzfgwzwdstvnwzqu`):

| Migration | Result | Verification |
|---|---|---|
| `production_admin_dashboard_summary` | Applied | Function exists as `SECURITY DEFINER`; `anon` execution is false; `authenticated` execution is true; search path is fixed to `public, pg_temp`. |
| `account_deletion_fk_cascade` | Applied | PostgreSQL catalog confirms `account_deletion_requests.requester_id` references `auth.users(id)` with `ON DELETE CASCADE`. |

The account-deletion Edge Function source has been hardened in the repository, but it still requires deployment through the Edge Function release workflow before the live worker uses the new implementation.


## Edge Function deployment status

Two deployment attempts for `process-account-deletions` were rejected before publication because the deployment tool could not resolve the function-local import-map/runtime path (`deno.json`). No live Edge Function version was changed. Supabase’s current documentation confirms that each function should carry its own `deno.json`; the repository now contains `supabase/functions/process-account-deletions/deno.json`, but the configured deployment tool still needs a corrected bundle/path invocation or CLI deployment workflow.


## Advisor remediation applied

At 2026-09-18 00:03 UTC, migration `close_advisor_rpc_only_table_access` was applied. It explicitly revoked `SELECT`, `INSERT`, `UPDATE`, and `DELETE` from `public`, `anon`, and `authenticated` on the 25 advisor-flagged RPC-only tables. A live privilege query confirmed **0** of those tables retain direct `SELECT` for `anon` or `authenticated`. The repository contract suite remained green at **182/182**.


## Post-remediation advisor results

The advisor was re-run at 2026-09-18 00:03 UTC. The RLS-no-policy INFO finding remains at 25 because the linter reports policy absence even when direct table grants are fully revoked; the live privilege query now confirms zero direct `SELECT` grants for `anon` or `authenticated` on the targeted tables. The anonymous SECURITY DEFINER WARN remains at 10, consisting of civic-report public-read RPCs and the public effective UI configuration RPC; these require endpoint-by-endpoint privacy review before changing grants because they are part of resident-facing reads. The authenticated SECURITY DEFINER WARN is 179 after adding the dashboard RPC; these are the application’s intentional RPC-only command/query boundary and must not be revoked in bulk without reviewing every client flow.

Performance advisors remain informational: 47 unindexed foreign keys and 111 unused indexes. Unused indexes will not be removed blindly. Foreign-key indexes should be added in workload-focused groups after query-plan review. Relevant remediation references are [RLS enabled without policy](https://supabase.com/docs/guides/database/database-linter?lint=0008_rls_enabled_no_policy), [anonymous SECURITY DEFINER execution](https://supabase.com/docs/guides/database/database-linter?lint=0028_anon_security_definer_function_executable), [authenticated SECURITY DEFINER execution](https://supabase.com/docs/guides/database/database-linter?lint=0029_authenticated_security_definer_function_executable), and [unindexed foreign keys](https://supabase.com/docs/guides/database/database-linter?lint=0001_unindexed_foreign_keys).


## Edge Function deployment completed

At 2026-09-18 00:08 UTC, the hardened `process-account-deletions` Edge Function was deployed successfully to RTC Community Production as **version 5**, with `verify_jwt=false` retained because the function implements a separate scheduler secret (`x-rtc-scheduler-secret`). The deployment bundle includes the function-local Deno configuration and shared audit helper. The deployment tool required the shared helper path `./_shared/auth.ts` in the flattened upload bundle; the repository source remains `../_shared/auth.ts` because its checked-in directory layout is nested under `supabase/functions/`.


## Final UI/media production pass

The final source audit found and fixed three high-confidence issues: unsupported media MIME types are no longer silently treated as JPEG; account-deletion storage cleanup now recursively removes nested user-owned objects and was redeployed as Edge Function version 6; and the marketplace save control now uses a 48dp accessible touch target. Final repository contracts pass at **183/183**, Python test compilation passes, and `git diff --check` passes. A detailed audit is available in `docs/FINAL_UI_MEDIA_PRODUCTION_AUDIT.md`.
