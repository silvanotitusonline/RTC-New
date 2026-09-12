# RTC Community Non-Production UI Configuration Schema Deployment

**Author:** Manus AI
**Date:** 27 August 2026
**Target:** The explicitly authorized isolated RTC Community Supabase project only.
**Excluded:** Production, real credentials, customer records, service-role use, Firebase Test Lab, device/emulator execution, release build/deployment, and Git push.

## Deployment result

The source-controlled Phase 1 configuration lifecycle migration was applied to the isolated non-production database after explicit user authorization. Metadata verification confirmed that `ui_configuration_versions` and `ui_configuration_events` exist and both have row-level security enabled.

The first post-deployment privilege check found an unexpected explicit `EXECUTE` grant to `anon` and `service_role` on all four new public RPCs. No public data was read and no application RPC was invoked. The issue was immediately contained by a narrow follow-up migration that revokes all execution privileges from `PUBLIC`, `anon`, `authenticated`, and `service_role`, then grants execution only to `authenticated`.

The final function metadata check confirms all four public configuration RPCs are `SECURITY DEFINER`, have the pinned `search_path=auth, public, pg_temp`, are executable by `authenticated`, and are **not** executable by either `anon` or `service_role`.

## Verified security state

| Control | Final verified result | Notes |
| --- | --- | --- |
| Configuration tables | Both deployed with RLS enabled. | `ui_configuration_versions`; `ui_configuration_events`. |
| Direct table policies | Zero policies on both tables. | Intentional deny-by-default posture; all use goes through narrow RPCs. |
| Administrator mutation RPCs | `ui_configuration_create_draft`, `ui_configuration_publish_draft`, and `ui_configuration_revert` use the existing server-side System Administrator/MFA assertion. | The assertion requires a signed-in actor, `SYSTEM_ADMIN`, and JWT assurance level `aal2`. |
| Resident effective-layout RPC | `ui_configuration_effective_global_home` is limited to authenticated callers and returns at most one published global configuration. | It does not permit anonymous browsing. |
| Anonymous execution | Denied on all four configuration RPCs after corrective migration. | Explicitly verified by metadata. |
| Service execution | Denied on all four configuration RPCs after corrective migration. | The Android client continues to contain no service-role path. |
| Deployed function bodies | Create, publish, and revert contain the System Administrator/MFA assertion; the effective-layout read contains an explicit signed-in guard. | Confirmed through function metadata/body checks rather than an unverified client call. |
| Published configuration | No configuration payloads, drafts, or event records were created. | The schema deployment did not seed or modify application content. |

## Advisory result

The post-deployment security advisor reports `RLS Enabled No Policy` as an informational result for the two configuration tables. This reflects the deliberate deny-by-default design: direct client access is revoked and both resident reads and administrator mutations use guarded RPCs.

The advisor also reports the generic warning class for authenticated callers of `SECURITY DEFINER` functions. This is expected for the new function surface because `authenticated` callers must reach the RPC endpoint; the substantive security controls are the explicit authorization assertion, input validation, pinned search path, and the corrected anonymous/service denial. Existing pre-existing warning instances were not changed in this focused rollout.

## Source-controlled migrations

| Migration | Purpose | Applied to isolated non-production project |
| --- | --- | --- |
| `20260827190000_add_nonproduction_ui_configuration_catalogue.sql` | Adds the append-only global configuration version/event schema, server validation, lifecycle RPCs, MFA-protected admin assertions, and resident effective-layout RPC. | Yes. |
| `20260827190500_harden_nonproduction_ui_configuration_rpc_grants.sql` | Removes the unexpected `anon`/`service_role` execution grants and preserves authenticated-only execution. | Yes. |

## Evidence boundary and next work

This deployment establishes database structure and privilege boundaries only. It does not prove Android fetch/cache behavior, administrator UI behavior, a real MFA transaction, Home visual output, device navigation, Firebase Test Lab results, or production readiness.

The next native slice is to add typed Supabase repository calls for the effective global Home layout, validate/cache only confirmed responses, and deliver the resolved layout to the already modular Home renderer. A future System Administrator draft/publish/revert screen must use the same protected route and MFA flow and must not permit direct table writes.
