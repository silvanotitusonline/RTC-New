begin;

-- Release 1 Operations Hub foundation. Direct table access is intentionally unavailable to
-- application clients: all reads and mutations below are exposed through narrow, audited RPCs.

create table if not exists public.operational_work_items (
  id uuid primary key default gen_random_uuid(),
  source_type text not null check (source_type in ('MODERATION_REPORT', 'NOTICE_REVIEW', 'ALERT_DELIVERY_FAILURE', 'OPERATIONAL_INCIDENT', 'CONTROL_EXPIRY')),
  source_id uuid not null,
  target_role public.app_role not null,
  title text not null check (char_length(trim(title)) between 3 and 180),
  description text not null check (char_length(trim(description)) between 3 and 1000),
  priority text not null check (priority in ('URGENT', 'HIGH', 'NORMAL', 'LOW')),
  state text not null default 'OPEN' check (state in ('OPEN', 'CLAIMED', 'READY_FOR_REVIEW', 'RESOLVED', 'CANCELLED')),
  assigned_to uuid references auth.users(id) on delete set null,
  due_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  resolved_at timestamptz,
  unique (source_type, source_id)
);

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

create table if not exists public.operational_incidents (
  id uuid primary key default gen_random_uuid(),
  title text not null check (char_length(trim(title)) between 3 and 180),
  impact_summary text not null check (char_length(trim(impact_summary)) between 3 and 2000),
  severity text not null check (severity in ('LOW', 'MODERATE', 'HIGH', 'CRITICAL')),
  state text not null default 'OPEN' check (state in ('OPEN', 'MITIGATING', 'RESOLVED', 'CANCELLED')),
  owner_id uuid references auth.users(id) on delete set null,
  opened_by uuid not null references auth.users(id) on delete restrict,
  opened_at timestamptz not null default now(),
  resolved_by uuid references auth.users(id) on delete restrict,
  resolved_at timestamptz,
  closing_summary text,
  updated_at timestamptz not null default now(),
  constraint operational_incident_close_check check (
    (state in ('OPEN', 'MITIGATING') and resolved_by is null and resolved_at is null and closing_summary is null)
    or (state in ('RESOLVED', 'CANCELLED') and resolved_by is not null and resolved_at is not null and char_length(trim(coalesce(closing_summary, ''))) between 3 and 2000)
  )
);

create table if not exists public.operational_controls (
  id uuid primary key default gen_random_uuid(),
  control_type text not null check (control_type in ('MAINTENANCE_BANNER', 'COMMUNITY_PAUSE')),
  state text not null default 'ACTIVE' check (state in ('ACTIVE', 'ENDED', 'EXPIRED', 'CANCELLED')),
  reason text not null check (char_length(trim(reason)) between 3 and 500),
  display_message text check (display_message is null or char_length(trim(display_message)) between 3 and 500),
  expires_at timestamptz not null,
  incident_id uuid references public.operational_incidents(id) on delete set null,
  opened_by uuid not null references auth.users(id) on delete restrict,
  opened_at timestamptz not null default now(),
  ended_by uuid references auth.users(id) on delete restrict,
  ended_at timestamptz,
  audit_note text not null check (char_length(trim(audit_note)) between 3 and 1000),
  constraint operational_control_end_check check (
    (state = 'ACTIVE' and ended_by is null and ended_at is null)
    or (state in ('ENDED', 'EXPIRED', 'CANCELLED') and ended_at is not null)
  )
);

create unique index if not exists operational_controls_one_active_per_type
  on public.operational_controls(control_type)
  where state = 'ACTIVE';

create table if not exists public.operational_control_events (
  id uuid primary key default gen_random_uuid(),
  control_id uuid not null references public.operational_controls(id) on delete cascade,
  actor_id uuid references auth.users(id) on delete set null,
  event_type text not null check (event_type in ('ACTIVATED', 'ENDED', 'EXPIRED', 'RETRY_REQUESTED')),
  reason text not null check (char_length(trim(reason)) between 3 and 1000),
  occurred_at timestamptz not null default now()
);

create table if not exists public.operational_service_events (
  id uuid primary key default gen_random_uuid(),
  service_key text not null check (service_key in ('ALERT_DELIVERY', 'NOTIFICATION_DELIVERY', 'SCHEDULED_JOBS', 'COMMUNITY_AVAILABILITY', 'PROTECTED_CONTROLS')),
  status text not null check (status in ('GREEN', 'AMBER', 'RED')),
  category text not null check (char_length(trim(category)) between 3 and 120),
  affected_count integer not null default 0 check (affected_count >= 0),
  last_successful_at timestamptz,
  source text not null default 'system',
  occurred_at timestamptz not null default now()
);

create table if not exists public.official_notice_lifecycle_events (
  id uuid primary key default gen_random_uuid(),
  notice_id uuid not null references public.official_notices(id) on delete cascade,
  actor_id uuid references auth.users(id) on delete set null,
  event_type text not null check (event_type in ('DRAFT_CREATED', 'SUBMITTED', 'REVIEW_STARTED', 'APPROVED', 'CHANGES_REQUESTED', 'REJECTED', 'SCHEDULED', 'PUBLISHED', 'RETIRED', 'CORRECTION_CREATED')),
  note text,
  occurred_at timestamptz not null default now()
);

create index if not exists operational_work_items_visible_index
  on public.operational_work_items(target_role, state, priority, due_at, created_at desc);
create index if not exists operational_work_items_owner_index
  on public.operational_work_items(assigned_to, state, updated_at desc);
create index if not exists operational_assignment_events_work_index
  on public.operational_work_assignment_events(work_item_id, occurred_at desc);
create index if not exists operational_incidents_state_index
  on public.operational_incidents(state, severity, opened_at desc);
create index if not exists operational_controls_active_index
  on public.operational_controls(state, control_type, expires_at);
create index if not exists official_notice_lifecycle_events_notice_index
  on public.official_notice_lifecycle_events(notice_id, occurred_at desc);

alter table public.operational_work_items enable row level security;
alter table public.operational_work_assignment_events enable row level security;
alter table public.operational_incidents enable row level security;
alter table public.operational_controls enable row level security;
alter table public.operational_control_events enable row level security;
alter table public.operational_service_events enable row level security;
alter table public.official_notice_lifecycle_events enable row level security;

revoke all on public.operational_work_items, public.operational_work_assignment_events,
  public.operational_incidents, public.operational_controls, public.operational_control_events,
  public.operational_service_events, public.official_notice_lifecycle_events
  from public, anon, authenticated;

create or replace function private.ops_assert_staff()
returns uuid
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_actor uuid := auth.uid();
begin
  if v_actor is null then
    raise exception 'A signed-in account is required.';
  end if;
  if not private.is_any_staff() then
    raise exception 'Authorised staff access is required.';
  end if;
  return v_actor;
end;
$$;

create or replace function private.ops_log_audit(
  p_actor uuid,
  p_event_type text,
  p_entity_type text,
  p_entity_id uuid,
  p_result text,
  p_metadata jsonb default '{}'::jsonb,
  p_source text default 'operations_hub'
)
returns void
language plpgsql
security definer
set search_path = public, pg_temp
as $$
begin
  insert into public.audit_events(actor_id, event_type, entity_type, entity_id, result, metadata, source)
  values (p_actor, p_event_type, p_entity_type, p_entity_id, p_result, coalesce(p_metadata, '{}'::jsonb), p_source);
end;
$$;

create or replace function private.ops_upsert_work_item(
  p_source_type text,
  p_source_id uuid,
  p_target_role public.app_role,
  p_title text,
  p_description text,
  p_priority text,
  p_due_at timestamptz default null
)
returns uuid
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_id uuid;
begin
  insert into public.operational_work_items(
    source_type, source_id, target_role, title, description, priority, due_at
  )
  values (
    p_source_type, p_source_id, p_target_role, p_title, p_description, p_priority, p_due_at
  )
  on conflict (source_type, source_id) do update
    set target_role = excluded.target_role,
        title = excluded.title,
        description = excluded.description,
        priority = excluded.priority,
        due_at = excluded.due_at,
        updated_at = now()
  where public.operational_work_items.state not in ('RESOLVED', 'CANCELLED')
  returning id into v_id;

  if v_id is null then
    select id into v_id
    from public.operational_work_items
    where source_type = p_source_type and source_id = p_source_id;
  end if;
  return v_id;
end;
$$;

create or replace function private.ops_resolve_work_for_source(p_source_type text, p_source_id uuid, p_actor uuid default null)
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

-- Current active controls are checked directly at Community write time, so expiry does not depend
-- on a client refresh or scheduler timing.
create or replace function private.ops_community_paused()
returns boolean
language sql
stable
security definer
set search_path = public, pg_temp
as $$
  select exists (
    select 1
    from public.operational_controls c
    where c.control_type = 'COMMUNITY_PAUSE'
      and c.state = 'ACTIVE'
      and c.expires_at > now()
  );
$$;

create or replace function private.ops_community_write_guard()
returns trigger
language plpgsql
security definer
set search_path = public, pg_temp
as $$
begin
  if private.ops_community_paused() then
    if tg_table_name = 'community_comments' and tg_op = 'INSERT' then
      raise exception 'COMMUNITY_PAUSED';
    end if;
    if tg_table_name = 'community_posts'
       and tg_op = 'UPDATE'
       and old.state = 'DRAFT'
       and new.state = 'PUBLISHED' then
      raise exception 'COMMUNITY_PAUSED';
    end if;
  end if;
  return new;
end;
$$;

drop trigger if exists operational_community_comment_pause_guard on public.community_comments;
create trigger operational_community_comment_pause_guard
  before insert on public.community_comments
  for each row execute function private.ops_community_write_guard();

drop trigger if exists operational_community_post_pause_guard on public.community_posts;
create trigger operational_community_post_pause_guard
  before update on public.community_posts
  for each row execute function private.ops_community_write_guard();

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
  select w.id,
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
   order by case w.priority when 'URGENT' then 1 when 'HIGH' then 2 when 'NORMAL' then 3 else 4 end,
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
  select * into v_item from public.operational_work_items where id = p_work_item_id for update;
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
  perform private.ops_log_audit(v_actor, 'OPERATIONS_WORK_CLAIMED', 'OPERATIONAL_WORK_ITEM', v_item.id, 'SUCCESS', jsonb_build_object('source_type', v_item.source_type));
end;
$$;

create or replace function public.ops_release_work_item(p_work_item_id uuid, p_reason text)
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
  select * into v_item from public.operational_work_items where id = p_work_item_id for update;
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
  perform private.ops_log_audit(v_actor, 'OPERATIONS_WORK_RELEASED', 'OPERATIONAL_WORK_ITEM', v_item.id, 'SUCCESS', jsonb_build_object('reason', v_reason));
end;
$$;

create or replace function public.ops_mark_work_ready_for_review(p_work_item_id uuid, p_note text)
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
  select * into v_item from public.operational_work_items where id = p_work_item_id for update;
  if not found or v_item.assigned_to <> v_actor or v_item.state <> 'CLAIMED' then
    raise exception 'Only the current owner can mark this item ready for review.';
  end if;

  update public.operational_work_items set state = 'READY_FOR_REVIEW', updated_at = now() where id = v_item.id;
  insert into public.operational_work_assignment_events(work_item_id, actor_id, event_type, previous_owner_id, new_owner_id, reason)
  values (v_item.id, v_actor, 'READY_FOR_REVIEW', v_actor, v_actor, v_note);
  perform private.ops_log_audit(v_actor, 'OPERATIONS_WORK_READY_FOR_REVIEW', 'OPERATIONAL_WORK_ITEM', v_item.id, 'SUCCESS', jsonb_build_object('note', v_note));
end;
$$;

create or replace function public.ops_reassign_work_item(p_work_item_id uuid, p_new_owner uuid, p_reason text)
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
  select * into v_item from public.operational_work_items where id = p_work_item_id for update;
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
  perform private.ops_log_audit(v_actor, 'OPERATIONS_WORK_REASSIGNED', 'OPERATIONAL_WORK_ITEM', v_item.id, 'SUCCESS', jsonb_build_object('reason', v_reason));
end;
$$;

create or replace function public.ops_create_incident(p_title text, p_impact_summary text, p_severity text, p_owner_id uuid default null)
returns uuid
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_actor uuid := private.access_assert_system_admin();
  v_id uuid;
  v_title text := trim(coalesce(p_title, ''));
  v_impact text := trim(coalesce(p_impact_summary, ''));
  v_severity text := upper(trim(coalesce(p_severity, '')));
begin
  if char_length(v_title) not between 3 and 180 or char_length(v_impact) not between 3 and 2000 then
    raise exception 'Provide a valid incident title and impact summary.';
  end if;
  if v_severity not in ('LOW', 'MODERATE', 'HIGH', 'CRITICAL') then
    raise exception 'Select a valid incident severity.';
  end if;
  if p_owner_id is not null and private.access_current_role(p_owner_id) <> 'SYSTEM_ADMIN'::public.app_role then
    raise exception 'Incident ownership must be assigned to an active System Administrator.';
  end if;

  insert into public.operational_incidents(title, impact_summary, severity, owner_id, opened_by)
  values (v_title, v_impact, v_severity, coalesce(p_owner_id, v_actor), v_actor)
  returning id into v_id;
  perform private.ops_upsert_work_item('OPERATIONAL_INCIDENT', v_id, 'SYSTEM_ADMIN'::public.app_role, 'Operational incident: ' || v_title, v_impact, case when v_severity in ('HIGH', 'CRITICAL') then 'URGENT' else 'HIGH' end, now() + interval '30 minutes');
  perform private.ops_log_audit(v_actor, 'OPERATIONS_INCIDENT_OPENED', 'OPERATIONAL_INCIDENT', v_id, 'SUCCESS', jsonb_build_object('severity', v_severity));
  return v_id;
end;
$$;

create or replace function public.ops_update_incident(p_incident_id uuid, p_state text, p_closing_summary text default null)
returns void
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_actor uuid := private.access_assert_system_admin();
  v_incident public.operational_incidents%rowtype;
  v_state text := upper(trim(coalesce(p_state, '')));
  v_summary text := trim(coalesce(p_closing_summary, ''));
begin
  select * into v_incident from public.operational_incidents where id = p_incident_id for update;
  if not found then
    raise exception 'Incident is not available.';
  end if;
  if v_state not in ('OPEN', 'MITIGATING', 'RESOLVED', 'CANCELLED') then
    raise exception 'Select a valid incident state.';
  end if;
  if v_state in ('RESOLVED', 'CANCELLED') and char_length(v_summary) not between 3 and 2000 then
    raise exception 'A closing summary between 3 and 2000 characters is required.';
  end if;

  update public.operational_incidents
     set state = v_state,
         resolved_by = case when v_state in ('RESOLVED', 'CANCELLED') then v_actor else null end,
         resolved_at = case when v_state in ('RESOLVED', 'CANCELLED') then now() else null end,
         closing_summary = case when v_state in ('RESOLVED', 'CANCELLED') then v_summary else null end,
         updated_at = now()
   where id = v_incident.id;
  if v_state in ('RESOLVED', 'CANCELLED') then
    update public.operational_work_items set state = 'RESOLVED', resolved_at = now(), updated_at = now()
     where source_type = 'OPERATIONAL_INCIDENT' and source_id = v_incident.id and state not in ('RESOLVED', 'CANCELLED');
  end if;
  perform private.ops_log_audit(v_actor, 'OPERATIONS_INCIDENT_' || v_state, 'OPERATIONAL_INCIDENT', v_incident.id, 'SUCCESS');
end;
$$;

create or replace function public.ops_set_operational_control(
  p_control_type text,
  p_enabled boolean,
  p_reason text,
  p_display_message text,
  p_expires_at timestamptz,
  p_incident_id uuid,
  p_confirmation text,
  p_audit_note text
)
returns uuid
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_actor uuid := private.access_assert_system_admin();
  v_type text := upper(trim(coalesce(p_control_type, '')));
  v_reason text := trim(coalesce(p_reason, ''));
  v_message text := trim(coalesce(p_display_message, ''));
  v_note text := trim(coalesce(p_audit_note, ''));
  v_existing public.operational_controls%rowtype;
  v_id uuid;
begin
  if v_type not in ('MAINTENANCE_BANNER', 'COMMUNITY_PAUSE') then
    raise exception 'Select a supported operational control.';
  end if;
  if trim(coalesce(p_confirmation, '')) <> 'CONFIRM OPERATIONAL CONTROL' then
    raise exception 'Type CONFIRM OPERATIONAL CONTROL before applying this control.';
  end if;
  if char_length(v_reason) not between 3 and 500 or char_length(v_note) not between 3 and 1000 then
    raise exception 'A valid reason and audit note are required.';
  end if;
  if p_enabled and (p_expires_at is null or p_expires_at <= now() or p_expires_at > now() + interval '14 days') then
    raise exception 'Set an expiry between now and 14 days from now.';
  end if;
  if p_enabled and v_type = 'MAINTENANCE_BANNER' and char_length(v_message) not between 3 and 500 then
    raise exception 'Provide a resident-facing maintenance message.';
  end if;
  if p_incident_id is not null and not exists (select 1 from public.operational_incidents where id = p_incident_id and state in ('OPEN', 'MITIGATING')) then
    raise exception 'Link the control only to an active incident.';
  end if;

  select * into v_existing from public.operational_controls where control_type = v_type and state = 'ACTIVE' for update;
  if found then
    update public.operational_controls
       set state = case when p_enabled then 'CANCELLED' else 'ENDED' end,
           ended_by = v_actor,
           ended_at = now()
     where id = v_existing.id;
    insert into public.operational_control_events(control_id, actor_id, event_type, reason)
    values (v_existing.id, v_actor, 'ENDED', v_reason);
  end if;

  if p_enabled then
    insert into public.operational_controls(control_type, reason, display_message, expires_at, incident_id, opened_by, audit_note)
    values (v_type, v_reason, nullif(v_message, ''), p_expires_at, p_incident_id, v_actor, v_note)
    returning id into v_id;
    insert into public.operational_control_events(control_id, actor_id, event_type, reason)
    values (v_id, v_actor, 'ACTIVATED', v_reason);
    perform private.ops_log_audit(v_actor, 'OPERATIONS_CONTROL_ACTIVATED', 'OPERATIONAL_CONTROL', v_id, 'SUCCESS', jsonb_build_object('type', v_type, 'expires_at', p_expires_at));
  else
    v_id := v_existing.id;
    perform private.ops_log_audit(v_actor, 'OPERATIONS_CONTROL_ENDED', 'OPERATIONAL_CONTROL', v_id, 'SUCCESS', jsonb_build_object('type', v_type));
  end if;
  return v_id;
end;
$$;

create or replace function public.ops_expire_operational_controls()
returns integer
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_count integer := 0;
  v_control public.operational_controls%rowtype;
begin
  for v_control in
    update public.operational_controls
       set state = 'EXPIRED', ended_at = now()
     where state = 'ACTIVE' and expires_at <= now()
     returning *
  loop
    insert into public.operational_control_events(control_id, actor_id, event_type, reason)
    values (v_control.id, v_control.opened_by, 'EXPIRED', 'The scheduled operational-control expiry was reached.');
    perform private.ops_log_audit(v_control.opened_by, 'OPERATIONS_CONTROL_EXPIRED', 'OPERATIONAL_CONTROL', v_control.id, 'SUCCESS', jsonb_build_object('type', v_control.control_type), 'operations_scheduler');
    v_count := v_count + 1;
  end loop;
  return v_count;
end;
$$;

create or replace function public.ops_get_active_community_controls()
returns table(
  maintenance_message text,
  maintenance_expires_at timestamptz,
  community_paused boolean,
  community_pause_expires_at timestamptz
)
language sql
security definer
set search_path = public, pg_temp
as $$
  select
    (select c.display_message from public.operational_controls c where c.control_type = 'MAINTENANCE_BANNER' and c.state = 'ACTIVE' and c.expires_at > now() limit 1),
    (select c.expires_at from public.operational_controls c where c.control_type = 'MAINTENANCE_BANNER' and c.state = 'ACTIVE' and c.expires_at > now() limit 1),
    exists(select 1 from public.operational_controls c where c.control_type = 'COMMUNITY_PAUSE' and c.state = 'ACTIVE' and c.expires_at > now()),
    (select c.expires_at from public.operational_controls c where c.control_type = 'COMMUNITY_PAUSE' and c.state = 'ACTIVE' and c.expires_at > now() limit 1);
$$;

create or replace function public.ops_retry_failed_community_alert(p_alert_id uuid, p_reason text, p_confirmation text, p_incident_id uuid default null)
returns void
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_actor uuid := private.access_assert_system_admin();
  v_alert public.community_alerts%rowtype;
  v_reason text := trim(coalesce(p_reason, ''));
begin
  if trim(coalesce(p_confirmation, '')) <> 'RETRY FAILED ALERT' then
    raise exception 'Type RETRY FAILED ALERT before retrying delivery.';
  end if;
  if char_length(v_reason) not between 3 and 500 then
    raise exception 'Provide a retry reason between 3 and 500 characters.';
  end if;
  select * into v_alert from public.community_alerts where id = p_alert_id for update;
  if not found or v_alert.status <> 'PUBLISHED' or v_alert.dispatch_state <> 'FAILED' or v_alert.fcm_accepted_count <> 0 then
    raise exception 'Only a fully failed alert delivery can be retried safely.';
  end if;
  if p_incident_id is not null and not exists(select 1 from public.operational_incidents where id = p_incident_id and state in ('OPEN', 'MITIGATING')) then
    raise exception 'Link a retry only to an active incident.';
  end if;

  update public.community_alerts
     set dispatch_state = 'PENDING',
         dispatch_attempted_at = null,
         dispatched_at = null,
         eligible_device_count = 0,
         fcm_accepted_count = 0,
         fcm_failed_count = 0,
         updated_at = now()
   where id = v_alert.id;
  insert into public.operational_service_events(service_key, status, category, affected_count, source)
  values ('ALERT_DELIVERY', 'AMBER', 'Failed alert delivery retry requested', v_alert.intended_recipient_count, 'operations_control');
  perform private.ops_log_audit(v_actor, 'OPERATIONS_ALERT_RETRY_REQUESTED', 'COMMUNITY_ALERT', v_alert.id, 'SUCCESS', jsonb_build_object('reason', v_reason, 'incident_id', p_incident_id));
end;
$$;

create or replace function public.ops_refresh_operational_work_items()
returns integer
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_count integer := 0;
  v_record record;
begin
  perform public.ops_expire_operational_controls();

  for v_record in
    select m.id, m.reason_code, m.created_at
    from public.moderation_items m
    where m.state in ('OPEN', 'UNDER_REVIEW')
  loop
    perform private.ops_upsert_work_item(
      'MODERATION_REPORT', v_record.id, 'MODERATOR'::public.app_role,
      'Review Community report',
      'Reported Community content requires a safeguarded moderator decision (' || v_record.reason_code || ').',
      case when v_record.reason_code in ('HARMFUL_CONTENT', 'PRIVACY_CONCERN') then 'HIGH' else 'NORMAL' end,
      v_record.created_at + interval '24 hours'
    );
    v_count := v_count + 1;
  end loop;

  for v_record in
    select n.id, n.title, n.created_at
    from public.official_notices n
    where n.status in ('submitted', 'under_review')
  loop
    perform private.ops_upsert_work_item(
      'NOTICE_REVIEW', v_record.id, 'CONTENT_EDITOR'::public.app_role,
      'Review notice: ' || left(v_record.title, 120),
      'An editorial submission requires review by a different authorised editor or System Administrator.',
      'NORMAL', v_record.created_at + interval '3 days'
    );
    v_count := v_count + 1;
  end loop;

  for v_record in
    select a.id, a.title, a.intended_recipient_count, a.updated_at
    from public.community_alerts a
    where a.status = 'PUBLISHED' and a.dispatch_state in ('FAILED', 'PARTIAL')
  loop
    perform private.ops_upsert_work_item(
      'ALERT_DELIVERY_FAILURE', v_record.id, 'SYSTEM_ADMIN'::public.app_role,
      'Investigate alert delivery: ' || left(v_record.title, 120),
      'A Community alert delivery needs System Administrator attention. Only fully failed deliveries can be safely retried.',
      'HIGH', v_record.updated_at + interval '1 hour'
    );
    v_count := v_count + 1;
  end loop;

  for v_record in
    select i.id, i.title, i.impact_summary, i.severity, i.opened_at
    from public.operational_incidents i
    where i.state in ('OPEN', 'MITIGATING')
  loop
    perform private.ops_upsert_work_item(
      'OPERATIONAL_INCIDENT', v_record.id, 'SYSTEM_ADMIN'::public.app_role,
      'Operational incident: ' || left(v_record.title, 120),
      v_record.impact_summary,
      case when v_record.severity in ('HIGH', 'CRITICAL') then 'URGENT' else 'HIGH' end,
      v_record.opened_at + interval '30 minutes'
    );
    v_count := v_count + 1;
  end loop;

  update public.operational_work_items w
     set state = 'RESOLVED', resolved_at = now(), updated_at = now()
   where w.state not in ('RESOLVED', 'CANCELLED')
     and (
       (w.source_type = 'MODERATION_REPORT' and not exists (select 1 from public.moderation_items m where m.id = w.source_id and m.state in ('OPEN', 'UNDER_REVIEW')))
       or (w.source_type = 'NOTICE_REVIEW' and not exists (select 1 from public.official_notices n where n.id = w.source_id and n.status in ('submitted', 'under_review')))
       or (w.source_type = 'ALERT_DELIVERY_FAILURE' and not exists (select 1 from public.community_alerts a where a.id = w.source_id and a.status = 'PUBLISHED' and a.dispatch_state in ('FAILED', 'PARTIAL')))
       or (w.source_type = 'OPERATIONAL_INCIDENT' and not exists (select 1 from public.operational_incidents i where i.id = w.source_id and i.state in ('OPEN', 'MITIGATING')))
     );

  return v_count;
end;
$$;

create or replace function public.ops_list_system_health()
returns table(
  service_key text,
  status text,
  category text,
  affected_count integer,
  last_successful_at timestamptz,
  detail text
)
language plpgsql
security definer
set search_path = cron, public, pg_temp
as $$
declare
  v_actor uuid := private.access_assert_system_admin();
begin
  perform private.ops_log_audit(v_actor, 'OPERATIONS_SYSTEM_HEALTH_VIEWED', 'SYSTEM_HEALTH', null, 'VIEWED');
  return query
  select 'ALERT_DELIVERY'::text,
         case when count(*) filter (where a.dispatch_state = 'FAILED') > 0 then 'RED'
              when count(*) filter (where a.dispatch_state = 'PARTIAL') > 0 then 'AMBER'
              else 'GREEN' end,
         'Community alert dispatch'::text,
         count(*) filter (where a.dispatch_state in ('FAILED', 'PARTIAL'))::integer,
         max(a.dispatched_at) filter (where a.dispatch_state = 'COMPLETE'),
         case when count(*) filter (where a.dispatch_state = 'FAILED') > 0 then 'One or more alert deliveries failed.'
              when count(*) filter (where a.dispatch_state = 'PARTIAL') > 0 then 'One or more alert deliveries completed partially.'
              else 'Recent alert delivery is healthy.' end
  from public.community_alerts a;

  return query
  select 'NOTIFICATION_DELIVERY'::text,
         case when coalesce(sum(a.fcm_failed_count), 0) > 0 then 'AMBER' else 'GREEN' end,
         'Accepted versus failed device deliveries'::text,
         coalesce(sum(a.fcm_failed_count), 0)::integer,
         max(a.dispatched_at) filter (where a.fcm_accepted_count > 0),
         case when coalesce(sum(a.fcm_failed_count), 0) > 0 then 'Some device deliveries require investigation.' else 'No failed device deliveries are currently recorded.' end
  from public.community_alerts a
  where a.published_at >= now() - interval '30 days';

  return query
  select 'SCHEDULED_JOBS'::text,
         case when exists(select 1 from cron.job j where j.jobname in ('rtc-publish-due-official-notices', 'rtc-community-alert-schedule') and not j.active) then 'RED' else 'GREEN' end,
         'Deterministic publication and alert jobs'::text,
         count(*) filter (where not j.active)::integer,
         null::timestamptz,
         case when count(*) filter (where not j.active) > 0 then 'A required scheduled job is inactive.' else 'Required scheduled jobs are active.' end
  from cron.job j
  where j.jobname in ('rtc-publish-due-official-notices', 'rtc-community-alert-schedule', 'rtc-release1-operational-maintenance');

  return query
  select 'COMMUNITY_AVAILABILITY'::text,
         case when private.ops_community_paused() then 'AMBER' else 'GREEN' end,
         'Community posting and commenting'::text,
         case when private.ops_community_paused() then 1 else 0 end,
         null::timestamptz,
         case when private.ops_community_paused() then 'Community posting and commenting are temporarily paused.' else 'Community posting and commenting are available.' end;

  return query
  select 'PROTECTED_CONTROLS'::text,
         case when count(*) > 0 then 'AMBER' else 'GREEN' end,
         'Active maintenance or Community controls'::text,
         count(*)::integer,
         null::timestamptz,
         case when count(*) > 0 then 'One or more protected controls are currently active.' else 'No protected controls are currently active.' end
  from public.operational_controls c
  where c.state = 'ACTIVE' and c.expires_at > now();
end;
$$;

create or replace function public.ops_list_administrative_activity(p_limit integer default 100, p_offset integer default 0, p_category text default null)
returns table(
  id uuid,
  actor_email text,
  category text,
  event_type text,
  outcome text,
  occurred_at timestamptz,
  target_label text,
  details text
)
language plpgsql
security definer
set search_path = auth, public, pg_temp
as $$
declare
  v_actor uuid := private.access_assert_system_admin();
  v_limit integer := greatest(1, least(coalesce(p_limit, 100), 200));
  v_offset integer := greatest(0, coalesce(p_offset, 0));
  v_category text := nullif(upper(trim(coalesce(p_category, ''))), '');
begin
  if v_category is not null and v_category not in ('ACCESS', 'PRIVACY', 'OPERATIONS', 'MODERATION', 'EDITORIAL', 'ALERT') then
    raise exception 'Select a valid activity category.';
  end if;
  perform private.ops_log_audit(v_actor, 'OPERATIONS_ADMINISTRATIVE_ACTIVITY_VIEWED', 'ADMINISTRATIVE_ACTIVITY', null, 'VIEWED', jsonb_build_object('category', v_category));

  return query
  with all_events as (
    select a.id,
           coalesce(lower(u.email), 'System') as actor_email,
           case
             when a.event_type like 'OPERATIONS_%' then 'OPERATIONS'
             when a.event_type like 'COMMUNITY_MODERATION_%' then 'MODERATION'
             when a.event_type like 'EDITORIAL_%' then 'EDITORIAL'
             when a.event_type like 'ALERT_%' then 'ALERT'
             when a.event_type like 'PRIVACY_%' then 'PRIVACY'
             else 'OPERATIONS'
           end as category,
           a.event_type,
           a.result as outcome,
           a.occurred_at,
           coalesce(a.metadata ->> 'target_email', a.entity_type) as target_label,
           case
             when a.metadata ? 'reason' then a.metadata ->> 'reason'
             when a.metadata ? 'note' then a.metadata ->> 'note'
             else null
           end as details
      from public.audit_events a
      left join auth.users u on u.id = a.actor_id
     where a.source in ('operations_hub', 'operations_scheduler', 'community-rpc', 'access_management', 'admin_privacy_analytics')

    union all

    select r.id,
           coalesce(lower(u.email), 'System'),
           'ACCESS'::text,
           r.event_type,
           'RECORDED'::text,
           r.occurred_at,
           r.target_email,
           r.reason
      from public.access_role_audit_events r
      left join auth.users u on u.id = r.actor_id
  )
  select e.id, e.actor_email, e.category, e.event_type, e.outcome, e.occurred_at, e.target_label, e.details
  from all_events e
  where v_category is null or e.category = v_category
  order by e.occurred_at desc
  limit v_limit offset v_offset;
end;
$$;

create or replace function public.ops_refresh_operational_maintenance()
returns integer
language plpgsql
security definer
set search_path = public, pg_temp
as $$
begin
  perform public.ops_expire_operational_controls();
  return public.ops_refresh_operational_work_items();
end;
$$;

revoke all on function private.ops_assert_staff() from public, anon, authenticated;
revoke all on function private.ops_log_audit(uuid, text, text, uuid, text, jsonb, text) from public, anon, authenticated;
revoke all on function private.ops_upsert_work_item(text, uuid, public.app_role, text, text, text, timestamptz) from public, anon, authenticated;
revoke all on function private.ops_resolve_work_for_source(text, uuid, uuid) from public, anon, authenticated;
revoke all on function private.ops_community_paused() from public, anon, authenticated;
revoke all on function private.ops_community_write_guard() from public, anon, authenticated;

revoke all on function public.ops_list_work_queue() from public, anon;
revoke all on function public.ops_claim_work_item(uuid) from public, anon;
revoke all on function public.ops_release_work_item(uuid, text) from public, anon;
revoke all on function public.ops_mark_work_ready_for_review(uuid, text) from public, anon;
revoke all on function public.ops_reassign_work_item(uuid, uuid, text) from public, anon;
revoke all on function public.ops_create_incident(text, text, text, uuid) from public, anon;
revoke all on function public.ops_update_incident(uuid, text, text) from public, anon;
revoke all on function public.ops_set_operational_control(text, boolean, text, text, timestamptz, uuid, text, text) from public, anon;
revoke all on function public.ops_get_active_community_controls() from public, anon;
revoke all on function public.ops_retry_failed_community_alert(uuid, text, text, uuid) from public, anon;
revoke all on function public.ops_list_system_health() from public, anon;
revoke all on function public.ops_list_administrative_activity(integer, integer, text) from public, anon;
revoke all on function public.ops_refresh_operational_maintenance() from public, anon, authenticated;

grant execute on function public.ops_list_work_queue() to authenticated;
grant execute on function public.ops_claim_work_item(uuid) to authenticated;
grant execute on function public.ops_release_work_item(uuid, text) to authenticated;
grant execute on function public.ops_mark_work_ready_for_review(uuid, text) to authenticated;
grant execute on function public.ops_reassign_work_item(uuid, uuid, text) to authenticated;
grant execute on function public.ops_create_incident(text, text, text, uuid) to authenticated;
grant execute on function public.ops_update_incident(uuid, text, text) to authenticated;
grant execute on function public.ops_set_operational_control(text, boolean, text, text, timestamptz, uuid, text, text) to authenticated;
grant execute on function public.ops_get_active_community_controls() to authenticated;
grant execute on function public.ops_retry_failed_community_alert(uuid, text, text, uuid) to authenticated;
grant execute on function public.ops_list_system_health() to authenticated;
grant execute on function public.ops_list_administrative_activity(integer, integer, text) to authenticated;

select cron.unschedule(jobid)
from cron.job
where jobname = 'rtc-release1-operational-maintenance';

select cron.schedule(
  'rtc-release1-operational-maintenance',
  '*/5 * * * *',
  $cron$select public.ops_refresh_operational_maintenance();$cron$
);

select public.ops_refresh_operational_work_items();

commit;
