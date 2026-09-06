begin;

-- Keep the two privileged maintenance helpers internal to the owner/scheduler path.
revoke all on function public.ops_expire_operational_controls() from public, anon, authenticated;
revoke all on function public.ops_refresh_operational_work_items() from public, anon, authenticated;

-- Create a high-entropy scheduler secret once. Its plaintext stays in Vault and is never
-- embedded in the mobile client, Edge Function source, or cron command text.
do $$
begin
  if not exists (select 1 from vault.secrets where name = 'rtc_alert_dispatch_secret') then
    perform vault.create_secret(
      gen_random_uuid()::text || gen_random_uuid()::text,
      'rtc_alert_dispatch_secret',
      'RTC internal secret for the scheduled Community alert dispatcher'
    );
  end if;
end;
$$;

-- The Edge Function uses its service-role client to verify the secret. Public, anonymous,
-- and normal authenticated callers cannot invoke this function through the exposed API.
create or replace function public.assert_rtc_alert_dispatch_secret(p_secret text)
returns boolean
language plpgsql
security definer
set search_path = public, vault, pg_temp
as $$
declare
  v_expected text;
begin
  select decrypted_secret
    into v_expected
    from vault.decrypted_secrets
   where name = 'rtc_alert_dispatch_secret';

  return coalesce(p_secret = v_expected, false);
end;
$$;

revoke all on function public.assert_rtc_alert_dispatch_secret(text) from public, anon, authenticated;
grant execute on function public.assert_rtc_alert_dispatch_secret(text) to service_role;

-- Preserve the existing one-minute publication/expiry cadence while passing the Vault-held
-- secret to the now-authenticated Edge Function invocation.
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
      url := (select decrypted_secret from vault.decrypted_secrets where name = 'rtc_alert_scheduler_project_url') || '/functions/v1/dispatch-community-alerts',
      headers := jsonb_build_object(
        'Content-Type', 'application/json',
        'apikey', (select decrypted_secret from vault.decrypted_secrets where name = 'rtc_alert_scheduler_publishable_key'),
        'x-rtc-alert-dispatch-secret', (select decrypted_secret from vault.decrypted_secrets where name = 'rtc_alert_dispatch_secret')
      ),
      body := jsonb_build_object('source', 'supabase-cron')
    );
  $cron$
);

commit;
