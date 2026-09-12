begin;

-- Role-scoped, aggregate-only Operations Hub summary. This uses the same visibility predicate
-- as ops_list_work_queue so counts never reveal another staff role's workload.
create or replace function public.ops_workspace_summary_v1()
returns table(
  assigned_to_me bigint,
  high_priority bigint,
  unassigned bigint,
  ready_for_review bigint,
  overdue bigint,
  total_visible bigint
)
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_actor uuid := private.ops_assert_staff();
  v_role public.app_role := private.access_current_role(v_actor);
begin
  return query
  with visible_work as (
    select w.*
      from public.operational_work_items w
     where w.state in ('OPEN', 'CLAIMED', 'READY_FOR_REVIEW')
       and (
         v_role = 'SYSTEM_ADMIN'::public.app_role
         or w.target_role = v_role
       )
       and (
         w.assigned_to is null
         or w.assigned_to = v_actor
         or v_role = 'SYSTEM_ADMIN'::public.app_role
       )
  )
  select
    count(*) filter (where assigned_to = v_actor),
    count(*) filter (where priority in ('URGENT', 'HIGH')),
    count(*) filter (where assigned_to is null),
    count(*) filter (where state = 'READY_FOR_REVIEW'),
    count(*) filter (where due_at is not null and due_at < now()),
    count(*)
  from visible_work;
end;
$$;

revoke all on function public.ops_workspace_summary_v1() from public, anon;
grant execute on function public.ops_workspace_summary_v1() to authenticated;

commit;
