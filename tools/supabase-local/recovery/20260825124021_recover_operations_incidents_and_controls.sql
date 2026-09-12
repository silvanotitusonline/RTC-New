-- Reconstructs the incident and protected-control portion of the Operations Hub.
-- This migration intentionally does not schedule jobs, inspect cron state, deploy an Edge Function,
-- configure delivery credentials, or enable alert retry. Active-control reads and Community write
-- guards evaluate `expires_at` directly, so resident protection does not depend on a scheduler.

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

create index if not exists operational_incidents_state_index
  on public.operational_incidents(state, severity, opened_at desc);
create index if not exists operational_controls_active_index
  on public.operational_controls(state, control_type, expires_at);

alter table public.operational_incidents enable row level security;
alter table public.operational_controls enable row level security;
alter table public.operational_control_events enable row level security;

revoke all on public.operational_incidents, public.operational_controls, public.operational_control_events
  from public, anon, authenticated;

create policy operational_incidents_no_direct_client_access
  on public.operational_incidents for all to authenticated using (false) with check (false);
create policy operational_controls_no_direct_client_access
  on public.operational_controls for all to authenticated using (false) with check (false);
create policy operational_control_events_no_direct_client_access
  on public.operational_control_events for all to authenticated using (false) with check (false);

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

create or replace function public.ops_create_incident(
  p_title text,
  p_impact_summary text,
  p_severity text,
  p_owner_id uuid default null
)
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
  perform private.ops_upsert_work_item(
    'OPERATIONAL_INCIDENT',
    v_id,
    'SYSTEM_ADMIN'::public.app_role,
    'Operational incident: ' || v_title,
    v_impact,
    case when v_severity in ('HIGH', 'CRITICAL') then 'URGENT' else 'HIGH' end,
    now() + interval '30 minutes'
  );
  perform private.ops_log_audit(
    v_actor,
    'OPERATIONS_INCIDENT_OPENED',
    'OPERATIONAL_INCIDENT',
    v_id,
    'SUCCESS',
    jsonb_build_object('severity', v_severity)
  );
  return v_id;
end;
$$;

create or replace function public.ops_update_incident(
  p_incident_id uuid,
  p_state text,
  p_closing_summary text default null
)
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
  select * into v_incident
  from public.operational_incidents
  where id = p_incident_id
  for update;
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
    update public.operational_work_items
       set state = 'RESOLVED', resolved_at = now(), updated_at = now()
     where source_type = 'OPERATIONAL_INCIDENT'
       and source_id = v_incident.id
       and state not in ('RESOLVED', 'CANCELLED');
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
  v_type text := upper(trim(coalesce(p_control_type, ''));
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
  if p_incident_id is not null and not exists (
    select 1 from public.operational_incidents where id = p_incident_id and state in ('OPEN', 'MITIGATING')
  ) then
    raise exception 'Link the control only to an active incident.';
  end if;

  select * into v_existing
  from public.operational_controls
  where control_type = v_type and state = 'ACTIVE'
  for update;
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
    perform private.ops_log_audit(
      v_actor,
      'OPERATIONS_CONTROL_ACTIVATED',
      'OPERATIONAL_CONTROL',
      v_id,
      'SUCCESS',
      jsonb_build_object('type', v_type, 'expires_at', p_expires_at)
    );
  else
    v_id := v_existing.id;
    perform private.ops_log_audit(
      v_actor,
      'OPERATIONS_CONTROL_ENDED',
      'OPERATIONAL_CONTROL',
      v_id,
      'SUCCESS',
      jsonb_build_object('type', v_type)
    );
  end if;
  return v_id;
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

revoke all on function private.ops_community_paused() from public, anon, authenticated;
revoke all on function private.ops_community_write_guard() from public, anon, authenticated;
revoke all on function public.ops_list_incidents() from public, anon;
grant execute on function public.ops_list_incidents() to authenticated;
revoke all on function public.ops_create_incident(text, text, text, uuid) from public, anon;
grant execute on function public.ops_create_incident(text, text, text, uuid) to authenticated;
revoke all on function public.ops_update_incident(uuid, text, text) from public, anon;
grant execute on function public.ops_update_incident(uuid, text, text) to authenticated;
revoke all on function public.ops_set_operational_control(text, boolean, text, text, timestamptz, uuid, text, text) from public, anon;
grant execute on function public.ops_set_operational_control(text, boolean, text, text, timestamptz, uuid, text, text) to authenticated;
revoke all on function public.ops_get_active_community_controls() from public, anon;
grant execute on function public.ops_get_active_community_controls() to authenticated;
