begin;
set local search_path = extensions, public, pg_catalog;
select plan(8);

select ok(not has_function_privilege('anon', 'public.get_firebase_fcm_service_account()', 'execute'),
  'Anonymous callers cannot obtain the Firebase service account');
select ok(not has_function_privilege('authenticated', 'public.get_firebase_fcm_service_account()', 'execute'),
  'Residents cannot obtain the Firebase service account');
select ok(has_function_privilege('service_role', 'public.get_firebase_fcm_service_account()', 'execute'),
  'Trusted Edge services can obtain the Firebase service account');
select ok((select exists (
    select 1 from unnest(p.proconfig) setting
    where split_part(setting, '=', 1) = 'search_path'
      and btrim(split_part(setting, '=', 2), '"') = ''
  ) from pg_proc p where p.oid = 'public.get_firebase_fcm_service_account()'::regprocedure),
  'Credential reader fixes an empty search path');
select ok((select prosecdef from pg_proc where oid = 'public.get_firebase_fcm_service_account()'::regprocedure),
  'Credential reader uses its restricted owner authority');
select is((select pg_get_function_result(oid) from pg_proc where oid = 'public.get_firebase_fcm_service_account()'::regprocedure),
  'text', 'Credential reader preserves the JSON text contract expected by deployed callers');

-- All fixture writes are rolled back, including any existing credential rotation.
-- No production credential is selected or included in test output.
do $$
declare
  existing_id uuid;
  fixture text := '{"type":"service_account","project_id":"rtc-fixture"}';
begin
  select id into existing_id from vault.secrets where name = 'rtc_firebase_fcm_service_account';
  if existing_id is null then
    perform vault.create_secret(fixture, 'rtc_firebase_fcm_service_account');
  else
    perform vault.update_secret(existing_id, fixture);
  end if;
  perform vault.create_secret('unrelated-fixture', 'rtc_firebase_boundary_' || gen_random_uuid()::text);
end;
$$;

select is(public.get_firebase_fcm_service_account(),
  '{"type":"service_account","project_id":"rtc-fixture"}',
  'Credential reader returns only the Firebase entry when unrelated Vault secrets exist');
delete from vault.secrets where name = 'rtc_firebase_fcm_service_account';
select is(public.get_firebase_fcm_service_account(), null::text,
  'Missing Firebase credential returns null without falling back to another secret');

select * from finish();
rollback;
