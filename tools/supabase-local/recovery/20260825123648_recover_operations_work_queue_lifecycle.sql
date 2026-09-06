-- Reconstructs the staff work-queue lifecycle used by the Android Operations Hub.
-- This isolated non-production slice deliberately excludes scheduled maintenance,
-- system-health cron inspection, alert dispatch, and any external delivery integration.

create table if not exists public.operational_work_assignment_events (
  id uuid primary key default gen_random_uuid(),
  work_item_id uuid not null references public.operational_work_items(id) on delete cascade,
  actor_id uuid not null references auth.users(id) on delete restrict,
  event_type text not null check (event_type in ('CLAIMED', 'RELEASED', 'READY_FOR_REVIEW', 'REASSIGNED', 'RESOLVED', 'CANCELLED')),
  previous_owner_id uuid references auth.users(id) on delete set null,
  new_owner_id uuid references auth.users(id) on delete set null,
  reason text,
  occurred_at timestamptz not null default now()
);

create index if not exists operational_assignment_events_work_index
  on public.operational_work_assignment_events(work_item_id, occurred_at desc);

alter table public.operational_work_assignment_events enable row level security;
revoke all on public.operational_work_assignment_events from public, anon, authenticated;
create policy operational_work_assignment_events_no_direct_client_access
  on public.operational_work_assignment_events
  for all to authenticated using (false) with check (false);

create or replace function private.ops_resolve_work_for_source(
  p_source_type text,
  p_source_id uuid,
  p_actor uuid default null
)
returns void
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_item public.operational_work_items%rowtype;
begin
  select * into v_item
  from public.operational_work_items
  where source_type = p_source_type
    and source_id = p_source_id
    and state not in ('RESOLVED', 'CANCELLED')
  for update;

  if found then
    update public.operational_work_items
       set state = 'RESOLVED', resolved_at = now(), updated_at = now()
     where id = v_item.id;
    if p_actor is not null then
      insert into public.operational_work_assignment_events(
        work_item_id, actor_id, event_type, previous_owner_id, new_owner_id, reason
      ) values (
        v_item.id, p_actor, 'RESOLVED', v_item.assigned_to, v_item.assigned_to, 'Source workflow resolved.'
      );
    end if;
  end if;
end;
$$;

create or replace function public.ops_list_work_queue()
returns table(
  id uuid,
  source_type text,
  source_id uuid,
  title text,
  description text,
  priority text,
  state text,
  assigned_to_me boolean,
  is_unassigned boolean,
  due_at timestamptz,
  created_at timestamptz
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
  select
    w.id,
    w.source_type,
    w.source_id,
    w.title,
    w.description,
    w.priority,
    w.state,
    w.assigned_to = v_actor,
    w.assigned_to is null,
    w.due_at,
    w.created_at
  from public.operational_work_items w
  where w.state in ('OPEN', 'CLAIMED', 'READY_FOR_REVIEW')
    and (
      v_role = 'SYSTEM_ADMIN'::public.app_role
      or w.target_role = v_role
    )
    and (w.assigned_to is null or w.assigned_to = v_actor or v_role = 'SYSTEM_ADMIN'::public.app_role)
  order by
    case w.priority when 'URGENT' then 1 when 'HIGH' then 2 when 'NORMAL' then 3 else 4 end,
    w.due_at nulls last,
    w.created_at;
end;
$$;

create or replace function public.ops_claim_work_item(p_work_item_id uuid)
returns void
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_actor uuid := private.ops_assert_staff();
  v_role public.app_role := private.access_current_role(v_actor);
  v_item public.operational_work_items%rowtype;
begin
  select * into v_item
  from public.operational_work_items
  where id = p_work_item_id
  for update;
  if not found then
    raise exception 'Work item is not available.';
  end if;
  if v_item.state not in ('OPEN', 'CLAIMED') then
    raise exception 'This work item cannot be claimed.';
  end if;
  if v_item.assigned_to is not null and v_item.assigned_to <> v_actor then
    raise exception 'This work item is already assigned.';
  end if;
  if v_role <> 'SYSTEM_ADMIN'::public.app_role and v_item.target_role <> v_role then
    raise exception 'This work item is not available for your role.';
  end if;

  update public.operational_work_items
     set assigned_to = v_actor, state = 'CLAIMED', updated_at = now()
   where id = v_item.id;
  insert into public.operational_work_assignment_events(work_item_id, actor_id, event_type, previous_owner_id, new_owner_id, reason)
  values (v_item.id, v_actor, 'CLAIMED', v_item.assigned_to, v_actor, 'Staff member claimed this work item.');
  perform private.ops_log_audit(
    v_actor,
    'OPERATIONS_WORK_CLAIMED',
    'OPERATIONAL_WORK_ITEM',
    v_item.id,
    'SUCCESS',
    jsonb_build_object('source_type', v_item.source_type)
  );
end;
$$;

create or replace function public.ops_release_work_item(
  p_work_item_id uuid,
  p_reason text
)
returns void
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_actor uuid := private.ops_assert_staff();
  v_item public.operational_work_items%rowtype;
  v_reason text := trim(coalesce(p_reason, ''));
begin
  if char_length(v_reason) not between 3 and 500 then
    raise exception 'Provide a release reason between 3 and 500 characters.';
  end if;
  select * into v_item
  from public.operational_work_items
  where id = p_work_item_id
  for update;
  if not found or v_item.assigned_to <> v_actor then
    raise exception 'Only the current owner can release this work item.';
  end if;
  if v_item.state not in ('CLAIMED', 'READY_FOR_REVIEW') then
    raise exception 'This work item cannot be released.';
  end if;

  update public.operational_work_items
     set assigned_to = null, state = 'OPEN', updated_at = now()
   where id = v_item.id;
  insert into public.operational_work_assignment_events(work_item_id, actor_id, event_type, previous_owner_id, new_owner_id, reason)
  values (v_item.id, v_actor, 'RELEASED', v_actor, null, v_reason);
  perform private.ops_log_audit(
    v_actor,
    'OPERATIONS_WORK_RELEASED',
    'OPERATIONAL_WORK_ITEM',
    v_item.id,
    'SUCCESS',
    jsonb_build_object('reason', v_reason)
  );
end;
$$;

create or replace function public.ops_mark_work_ready_for_review(
  p_work_item_id uuid,
  p_note text
)
returns void
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_actor uuid := private.ops_assert_staff();
  v_item public.operational_work_items%rowtype;
  v_note text := trim(coalesce(p_note, ''));
begin
  if char_length(v_note) not between 3 and 1000 then
    raise exception 'Provide a review note between 3 and 1000 characters.';
  end if;
  select * into v_item
  from public.operational_work_items
  where id = p_work_item_id
  for update;
  if not found or v_item.assigned_to <> v_actor or v_item.state <> 'CLAIMED' then
    raise exception 'Only the current owner can mark this item ready for review.';
  end if;

  update public.operational_work_items
     set state = 'READY_FOR_REVIEW', updated_at = now()
   where id = v_item.id;
  insert into public.operational_work_assignment_events(work_item_id, actor_id, event_type, previous_owner_id, new_owner_id, reason)
  values (v_item.id, v_actor, 'READY_FOR_REVIEW', v_actor, v_actor, v_note);
  perform private.ops_log_audit(
    v_actor,
    'OPERATIONS_WORK_READY_FOR_REVIEW',
    'OPERATIONAL_WORK_ITEM',
    v_item.id,
    'SUCCESS',
    jsonb_build_object('note', v_note)
  );
end;
$$;

create or replace function public.ops_reassign_work_item(
  p_work_item_id uuid,
  p_new_owner uuid,
  p_reason text
)
returns void
language plpgsql
security definer
set search_path = auth, public, pg_temp
as $$
declare
  v_actor uuid := private.access_assert_system_admin();
  v_item public.operational_work_items%rowtype;
  v_reason text := trim(coalesce(p_reason, ''));
  v_new_role public.app_role;
begin
  if char_length(v_reason) not between 3 and 500 then
    raise exception 'Provide a reassignment reason between 3 and 500 characters.';
  end if;
  select * into v_item
  from public.operational_work_items
  where id = p_work_item_id
  for update;
  if not found or v_item.state in ('RESOLVED', 'CANCELLED') then
    raise exception 'This work item is not available for reassignment.';
  end if;
  select private.access_current_role(p_new_owner) into v_new_role;
  if v_new_role is null or (v_new_role <> v_item.target_role and v_new_role <> 'SYSTEM_ADMIN'::public.app_role) then
    raise exception 'Select an active staff account with the required role.';
  end if;

  update public.operational_work_items
     set assigned_to = p_new_owner, state = 'CLAIMED', updated_at = now()
   where id = v_item.id;
  insert into public.operational_work_assignment_events(work_item_id, actor_id, event_type, previous_owner_id, new_owner_id, reason)
  values (v_item.id, v_actor, 'REASSIGNED', v_item.assigned_to, p_new_owner, v_reason);
  perform private.ops_log_audit(
    v_actor,
    'OPERATIONS_WORK_REASSIGNED',
    'OPERATIONAL_WORK_ITEM',
    v_item.id,
    'SUCCESS',
    jsonb_build_object('reason', v_reason)
  );
end;
$$;

revoke all on function private.ops_resolve_work_for_source(text, uuid, uuid) from public, anon, authenticated;
revoke all on function public.ops_list_work_queue() from public, anon;
grant execute on function public.ops_list_work_queue() to authenticated;
revoke all on function public.ops_claim_work_item(uuid) from public, anon;
grant execute on function public.ops_claim_work_item(uuid) to authenticated;
revoke all on function public.ops_release_work_item(uuid, text) from public, anon;
grant execute on function public.ops_release_work_item(uuid, text) to authenticated;
revoke all on function public.ops_mark_work_ready_for_review(uuid, text) from public, anon;
grant execute on function public.ops_mark_work_ready_for_review(uuid, text) to authenticated;
revoke all on function public.ops_reassign_work_item(uuid, uuid, text) from public, anon;
grant execute on function public.ops_reassign_work_item(uuid, uuid, text) to authenticated;
