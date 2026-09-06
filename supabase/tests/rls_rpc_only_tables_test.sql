begin;
set local search_path = extensions, public, pg_catalog;

select plan(10);

select ok(
  (select relrowsecurity from pg_class where oid='public.marketplace_businesses'::regclass),
  'Marketplace business table keeps RLS enabled'
);

select ok(
  exists(
    select 1 from pg_policy
    where polrelid='public.marketplace_businesses'::regclass
      and polname='rpc_only_deny_direct_client_access'
      and polpermissive=false
  ),
  'Marketplace RPC-only table has explicit restrictive client denial'
);

select is(
  has_table_privilege('anon','public.marketplace_businesses','SELECT'),
  false,
  'anon cannot directly SELECT Marketplace businesses'
);

select is(
  has_table_privilege('authenticated','public.marketplace_businesses','SELECT'),
  false,
  'authenticated cannot directly SELECT Marketplace businesses'
);

select ok(
  exists(
    select 1 from pg_policy
    where polrelid='public.ui_configuration_versions'::regclass
      and polname='rpc_only_deny_direct_client_access'
      and polpermissive=false
  ),
  'Brand configuration versions remain RPC-only with explicit denial'
);

select is(
  has_table_privilege('authenticated','public.ui_configuration_versions','UPDATE'),
  false,
  'residents cannot directly mutate Brand configuration versions'
);

select ok(
  to_regclass('public.edge_function_rate_limits') is not null
  and not has_table_privilege('authenticated','public.edge_function_rate_limits','SELECT'),
  'Edge rate-limit state is server-only'
);

select ok(
  not has_function_privilege('authenticated','public.claim_edge_function_rate_limit(text,uuid,integer,integer)','EXECUTE')
  and has_function_privilege('service_role','public.claim_edge_function_rate_limit(text,uuid,integer,integer)','EXECUTE'),
  'Edge rate-limit claim is service-role only'
);

select ok(
  exists(select 1 from cron.job where jobname='rtc-community-alert-schedule' and active),
  'Community alert scheduler is installed and active'
);

select ok(
  exists(
    select 1 from cron.job
    where jobname='rtc-community-alert-schedule'
      and command like '%rtc_alert_scheduler_publishable_key%'
      and command like '%rtc_alert_scheduler_legacy_anon_jwt%'
      and command like '%rtc_alert_dispatch_secret%'
      and command not like '%sb_publishable_%'
      and command not like '%eyJ%'
  ),
  'Scheduler command resolves all credentials from Vault and embeds no literal credential'
);

select * from finish();
rollback;
