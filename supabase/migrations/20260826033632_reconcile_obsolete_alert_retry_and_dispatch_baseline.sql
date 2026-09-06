-- Reconstructed non-production baseline reconciliation.
--
-- Source recovery note (2026-08-26): read-only catalogue inspection of the approved
-- isolated project `eqwstpdjoineycrkhpht` found that the historical operations migration
-- still leaves an obsolete retry RPC and an application-owned cron dispatch path present.
-- This migration makes a fresh source replay fail closed. It is NOT authorization to apply
-- DDL to an existing project and MUST NOT be applied to an environment that already recorded
-- its corresponding recovery decision.
--
-- Deliberate non-actions:
--   * Do not drop pg_cron, pg_net, or supabase_vault extensions: object ownership and other
--     project dependencies have not been established.
--   * Do not read or delete Vault records.
--   * Do not alter publish_due_community_alerts or archive_expired_community_alerts.
--   * Do not deploy or invoke any Edge Function, dispatcher, or service credential path.

begin;

-- The historical retry action has no approved client workflow or isolated recovery contract.
-- Drop it rather than merely withholding a client affordance.
drop function if exists public.ops_retry_failed_community_alert(uuid, text, text, uuid);

-- Remove the publicly addressable helper that existed only for the historical dispatcher.
-- The original migration granted this to service_role, so retaining it after dispatch removal
-- would create an unnecessary exposed capability.
drop function if exists public.assert_rtc_alert_dispatch_secret(text);

-- Unschedule only the known RTC application job when pg_cron is installed. Dynamic execution
-- avoids a hard dependency on the cron schema in source replay environments that never enabled it.
do $$
begin
  if exists (select 1 from pg_extension where extname = 'pg_cron') then
    execute $sql$
      select cron.unschedule(jobid)
      from cron.job
      where jobname = 'rtc-community-alert-schedule'
    $sql$;
  end if;
end;
$$;

commit;
