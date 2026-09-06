-- Purpose: Restore the missing community-alert schedule with a layered machine-to-machine boundary.
-- Affected objects: public.assert_rtc_alert_dispatch_secret(text), cron job rtc-community-alert-schedule.
-- Grants/RLS effect: dispatch-secret assertion is service_role-only; no secret is embedded in source or cron text.
-- Authentication chain: modern publishable key -> apikey header; separate legacy anon JWT -> Edge gateway
--                       Authorization header; independent Vault dispatch secret -> function-body machine authority.
-- Rollback consideration: unschedule via a new forward migration; retain/rotate Vault secrets independently.

begin;

do $scheduler_prerequisites$
declare
  v_missing text[];
  v_project_url text;
begin
  select array_agg(required_name order by required_name)
  into v_missing
  from unnest(array[
    'rtc_alert_dispatch_secret',
    'rtc_alert_scheduler_project_url',
    'rtc_alert_scheduler_publishable_key',
    'rtc_alert_scheduler_legacy_anon_jwt'
  ]::text[]) as required(required_name)
  where not exists (
    select 1 from vault.secrets s where s.name = required.required_name
  );

  if coalesce(array_length(v_missing, 1), 0) > 0 then
    raise exception 'Required alert scheduler Vault material is missing: %', array_to_string(v_missing, ', ');
  end if;

  select decrypted_secret
  into v_project_url
  from vault.decrypted_secrets
  where name = 'rtc_alert_scheduler_project_url';

  if v_project_url is null or v_project_url !~ '^https://[a-z0-9-]+\.supabase\.co/?$' then
    raise exception 'The alert scheduler project URL is invalid.';
  end if;
end
$scheduler_prerequisites$;

create or replace function public.assert_rtc_alert_dispatch_secret(p_secret text)
returns boolean
language plpgsql
security definer
set search_path = ''
as $function$
declare
  v_expected text;
begin
  if p_secret is null or char_length(p_secret) not between 32 and 512 then
    return false;
  end if;

  select decrypted_secret
  into v_expected
  from vault.decrypted_secrets
  where name = 'rtc_alert_dispatch_secret';

  if v_expected is null or char_length(v_expected) not between 32 and 512 then
    return false;
  end if;

  return extensions.digest(p_secret, 'sha256') = extensions.digest(v_expected, 'sha256');
exception
  when others then
    return false;
end
$function$;

revoke all on function public.assert_rtc_alert_dispatch_secret(text)
  from public, anon, authenticated;
grant execute on function public.assert_rtc_alert_dispatch_secret(text)
  to service_role;

comment on function public.assert_rtc_alert_dispatch_secret(text) is
  'Service-role-only scheduler guard. Compares the request machine secret with Vault without returning secret material.';

select cron.unschedule(jobid)
from cron.job
where jobname = 'rtc-community-alert-schedule';

select cron.schedule(
  'rtc-community-alert-schedule',
  '* * * * *',
  $cron$
    select public.publish_due_community_alerts();
    select public.archive_expired_community_alerts();
    select net.http_post(
      url := rtrim((select decrypted_secret from vault.decrypted_secrets where name = 'rtc_alert_scheduler_project_url'), '/') || '/functions/v1/dispatch-community-alerts',
      body := jsonb_build_object('source', 'pg_cron', 'requestedAt', clock_timestamp()),
      params := '{}'::jsonb,
      headers := jsonb_build_object(
        'Content-Type', 'application/json',
        'apikey', (select decrypted_secret from vault.decrypted_secrets where name = 'rtc_alert_scheduler_publishable_key'),
        'Authorization', 'Bearer ' || (select decrypted_secret from vault.decrypted_secrets where name = 'rtc_alert_scheduler_legacy_anon_jwt'),
        'x-rtc-alert-dispatch-secret', (select decrypted_secret from vault.decrypted_secrets where name = 'rtc_alert_dispatch_secret')
      ),
      timeout_milliseconds := 10000
    );
  $cron$
);

comment on extension pg_cron is
  'RTC schedules include rtc-community-alert-schedule. Its command resolves all Edge credentials from Vault at execution time.';

commit;
