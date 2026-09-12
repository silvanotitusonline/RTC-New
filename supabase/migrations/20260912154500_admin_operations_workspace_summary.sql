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

-- Purpose-limited assignee directory for the reassignment dialog. It exposes only the account id,
-- profile display name and effective role, and only to a verified System Administrator. Eligibility
-- is derived with the same role helper enforced again by ops_reassign_work_item.
create or replace function public.ops_list_eligible_assignees_v1(p_work_item_id uuid)
returns table(
  user_id uuid,
  display_name text,
  role public.app_role
)
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_actor uuid := private.access_assert_system_admin();
  v_target_role public.app_role;
begin
  select w.target_role
    into v_target_role
    from public.operational_work_items w
   where w.id = p_work_item_id
     and w.state not in ('RESOLVED', 'CANCELLED');

  if v_target_role is null then
    raise exception 'This work item is not available for reassignment.';
  end if;

  return query
  select
    p.id,
    coalesce(nullif(trim(p.display_name), ''), 'Staff member') as display_name,
    private.access_current_role(p.id) as role
  from public.profiles p
  where private.access_current_role(p.id) in (v_target_role, 'SYSTEM_ADMIN'::public.app_role)
  order by
    case when p.id = v_actor then 0 else 1 end,
    lower(coalesce(nullif(trim(p.display_name), ''), 'Staff member')),
    p.id
  limit 100;
end;
$$;

revoke all on function public.ops_workspace_summary_v1() from public, anon;
revoke all on function public.ops_list_eligible_assignees_v1(uuid) from public, anon;
grant execute on function public.ops_workspace_summary_v1() to authenticated;
grant execute on function public.ops_list_eligible_assignees_v1(uuid) to authenticated;

commit;
