begin;

-- Reconcile the existing production credential reader with source history.
-- Provision/rotate the service-account JSON directly in Vault; never store it
-- in migrations, Android resources, or a table accessible to residents.
create or replace function public.get_firebase_fcm_service_account()
returns text
language sql
security definer
set search_path = ''
as $$
  select s.decrypted_secret
  from vault.decrypted_secrets s
  where s.name = 'rtc_firebase_fcm_service_account'
  limit 1;
$$;

revoke all on function public.get_firebase_fcm_service_account() from public, anon, authenticated;
grant execute on function public.get_firebase_fcm_service_account() to service_role;
comment on function public.get_firebase_fcm_service_account() is
  'Service-only Firebase credential lookup for trusted Edge notification and speech providers. Returns the existing JSON text contract; never callable by Android/resident clients.';

commit;
