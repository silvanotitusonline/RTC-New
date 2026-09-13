begin;

-- Key values are provisioned directly into Vault, never migration/source files.
create or replace function public.rtc_tomtom_credentials_v1()
returns jsonb
language sql
security definer
set search_path = pg_catalog
as $$
  select coalesce(jsonb_object_agg(s.name, s.decrypted_secret), '{}'::jsonb)
  from vault.decrypted_secrets s
  where s.name in ('TOMTOM_API_KEY', 'TOMTOM_MAPS_SDK_KEY');
$$;

revoke all on function public.rtc_tomtom_credentials_v1() from public, anon, authenticated;
grant execute on function public.rtc_tomtom_credentials_v1() to service_role;
comment on function public.rtc_tomtom_credentials_v1() is
  'Service-only TomTom credential lookup. The Edge Function releases only the mobile map key after authenticating the resident; the routing/search key stays on the server.';

commit;
