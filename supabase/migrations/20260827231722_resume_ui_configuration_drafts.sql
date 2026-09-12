-- Allow the protected customizer to resume its own server-persisted configuration drafts.
-- History remains System Administrator + AAL2 only through access_assert_system_admin().

drop function if exists public.ui_configuration_admin_history(integer);
create function public.ui_configuration_admin_history(p_limit integer default 40)
returns table(
  version_id uuid,
  state text,
  created_reason text,
  created_at timestamptz,
  published_at timestamptz,
  configuration jsonb
)
language plpgsql
security definer
set search_path = auth, public, pg_temp
as $$
begin
  perform private.access_assert_system_admin();
  return query
  select
    v.id,
    v.state,
    v.created_reason,
    v.created_at,
    v.published_at,
    private.ui_configuration_upgrade_to_v2(v.configuration)
  from public.ui_configuration_versions v
  where v.audience_key = 'RESIDENT_GLOBAL'
  order by v.created_at desc
  limit greatest(1, least(coalesce(p_limit, 40), 100));
end;
$$;

revoke all on function public.ui_configuration_admin_history(integer) from public, anon, authenticated, service_role;
grant execute on function public.ui_configuration_admin_history(integer) to authenticated;
