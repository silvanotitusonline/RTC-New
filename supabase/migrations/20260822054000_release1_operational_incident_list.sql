begin;

create or replace function public.ops_list_incidents()
returns table(
  id uuid,
  title text,
  impact_summary text,
  severity text,
  state text,
  opened_at timestamptz,
  resolved_at timestamptz,
  closing_summary text
)
language plpgsql
security definer
set search_path = public, pg_temp
as $$
begin
  perform private.access_assert_system_admin();

  return query
  select
    i.id,
    i.title,
    i.impact_summary,
    i.severity,
    i.state,
    i.opened_at,
    i.resolved_at,
    i.closing_summary
  from public.operational_incidents i
  order by
    case i.state when 'OPEN' then 0 when 'MITIGATING' then 1 else 2 end,
    i.opened_at desc
  limit 100;
end;
$$;

revoke all on function public.ops_list_incidents() from public, anon;
grant execute on function public.ops_list_incidents() to authenticated;

commit;
