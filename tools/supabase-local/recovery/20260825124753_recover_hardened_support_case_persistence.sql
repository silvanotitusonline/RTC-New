-- Canonical isolated support-case persistence contract recovered from the applied
-- RTC Community Non-Production migration ledger (version 20260825124753).
-- No user data or credentials are included.

create type public.case_state as enum ('OPEN', 'IN_REVIEW', 'IN_PROGRESS', 'RESOLVED', 'CLOSED');

create table if not exists public.community_cases (
  id uuid primary key default gen_random_uuid(),
  resident_id uuid not null references auth.users(id) on delete restrict,
  assigned_staff_id uuid references auth.users(id) on delete set null,
  title text not null check (char_length(trim(title)) between 3 and 180),
  category text not null check (char_length(trim(category)) between 3 and 120),
  description text not null check (char_length(trim(description)) between 3 and 10000),
  state public.case_state not null default 'OPEN',
  priority smallint not null default 3 check (priority between 1 and 5),
  location_label text check (location_label is null or char_length(trim(location_label)) between 2 and 180),
  details jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  closed_at timestamptz
);

create table if not exists public.case_messages (
  id uuid primary key default gen_random_uuid(),
  case_id uuid not null references public.community_cases(id) on delete cascade,
  author_id uuid not null references auth.users(id) on delete restrict,
  body text not null check (char_length(trim(body)) between 1 and 10000),
  attachments jsonb not null default '[]'::jsonb,
  created_at timestamptz not null default now()
);

create index if not exists community_cases_resident_updated_idx
  on public.community_cases(resident_id, updated_at desc);
create index if not exists community_cases_assigned_staff_updated_idx
  on public.community_cases(assigned_staff_id, updated_at desc)
  where assigned_staff_id is not null;
create index if not exists case_messages_case_created_idx
  on public.case_messages(case_id, created_at);

alter table public.community_cases enable row level security;
alter table public.case_messages enable row level security;

revoke all on public.community_cases, public.case_messages from public, anon, authenticated;

create policy community_cases_no_direct_client_access
  on public.community_cases for all to authenticated using (false) with check (false);
create policy case_messages_no_direct_client_access
  on public.case_messages for all to authenticated using (false) with check (false);

create or replace function private.has_verified_system_admin()
returns boolean
language sql
stable
security definer
set search_path = auth, public, pg_temp
as $$
  select private.has_role('SYSTEM_ADMIN'::public.app_role)
    and coalesce(auth.jwt() ->> 'aal', 'aal1') = 'aal2';
$$;

create or replace function private.can_access_case(requested_case_id uuid)
returns boolean
language sql
stable
security definer
set search_path = public, pg_temp
as $$
  select exists (
    select 1
    from public.community_cases c
    where c.id = requested_case_id
      and (
        c.resident_id = auth.uid()
        or c.assigned_staff_id = auth.uid()
        or private.has_verified_system_admin()
      )
  );
$$;

create or replace function private.touch_community_case_updated_at()
returns trigger
language plpgsql
security definer
set search_path = public, pg_temp
as $$
begin
  new.updated_at := now();
  return new;
end;
$$;

drop trigger if exists community_cases_set_updated_at on public.community_cases;
create trigger community_cases_set_updated_at
before update on public.community_cases
for each row execute function private.touch_community_case_updated_at();

create or replace function public.submit_support_case(
  p_title text,
  p_description text,
  p_category text default 'GENERAL',
  p_priority smallint default 3,
  p_location_label text default null,
  p_details jsonb default '{}'::jsonb
)
returns uuid
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_resident uuid := auth.uid();
  v_case_id uuid;
  v_title text := trim(coalesce(p_title, ''));
  v_description text := trim(coalesce(p_description, ''));
  v_category text := upper(trim(coalesce(p_category, 'GENERAL')));
  v_location text := nullif(trim(coalesce(p_location_label, '')), '');
begin
  if v_resident is null then
    raise exception 'A signed-in account is required.';
  end if;
  if char_length(v_title) not between 3 and 180
    or char_length(v_description) not between 3 and 10000
    or char_length(v_category) not between 3 and 120
    or coalesce(p_priority, 3) not between 1 and 5
    or (v_location is not null and char_length(v_location) not between 2 and 180)
    or jsonb_typeof(coalesce(p_details, '{}'::jsonb)) <> 'object' then
    raise exception 'Provide valid support-case details.';
  end if;

  insert into public.community_cases(
    resident_id, title, category, description, priority, location_label, details
  ) values (
    v_resident, v_title, v_category, v_description, coalesce(p_priority, 3), v_location, coalesce(p_details, '{}'::jsonb)
  ) returning id into v_case_id;

  perform private.ops_log_audit(
    v_resident,
    'SUPPORT_CASE_SUBMITTED',
    'COMMUNITY_CASE',
    v_case_id,
    'SUCCESS',
    jsonb_build_object('category', v_category, 'priority', coalesce(p_priority, 3)),
    'support_case'
  );
  return v_case_id;
end;
$$;

create or replace function public.list_my_support_cases()
returns table(
  id uuid,
  title text,
  category text,
  state public.case_state,
  priority smallint,
  location_label text,
  updated_at timestamptz,
  action_required boolean
)
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
  return query
  select c.id, c.title, c.category, c.state, c.priority, c.location_label, c.updated_at,
         c.assigned_staff_id is null and c.state in ('OPEN', 'IN_REVIEW')
  from public.community_cases c
  where c.resident_id = v_actor
  order by c.updated_at desc
  limit 100;
end;
$$;

create or replace function public.list_assigned_support_cases()
returns table(
  id uuid,
  resident_id uuid,
  title text,
  category text,
  state public.case_state,
  priority smallint,
  location_label text,
  updated_at timestamptz
)
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_actor uuid := auth.uid();
begin
  if v_actor is null or not private.has_role('CASE_STAFF'::public.app_role) then
    raise exception 'Assigned case staff access is required.';
  end if;
  return query
  select c.id, c.resident_id, c.title, c.category, c.state, c.priority, c.location_label, c.updated_at
  from public.community_cases c
  where c.assigned_staff_id = v_actor
  order by c.updated_at desc
  limit 100;
end;
$$;

create or replace function public.assign_support_case(
  p_case_id uuid,
  p_staff_id uuid,
  p_note text
)
returns void
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_actor uuid := private.access_assert_system_admin();
  v_note text := trim(coalesce(p_note, ''));
  v_case public.community_cases%rowtype;
begin
  if char_length(v_note) not between 3 and 1000 then
    raise exception 'Provide an assignment note between 3 and 1000 characters.';
  end if;
  if private.access_current_role(p_staff_id) <> 'CASE_STAFF'::public.app_role then
    raise exception 'Assign the case only to an active CASE_STAFF account.';
  end if;
  select * into v_case from public.community_cases where id = p_case_id for update;
  if not found or v_case.state in ('RESOLVED', 'CLOSED') then
    raise exception 'This support case is not available for assignment.';
  end if;

  update public.community_cases
     set assigned_staff_id = p_staff_id,
         state = case when state = 'OPEN' then 'IN_REVIEW' else state end
   where id = v_case.id;
  perform private.ops_log_audit(
    v_actor,
    'SUPPORT_CASE_ASSIGNED',
    'COMMUNITY_CASE',
    v_case.id,
    'SUCCESS',
    jsonb_build_object('assigned_staff_id', p_staff_id, 'note', v_note),
    'support_case'
  );
end;
$$;

create or replace function public.update_assigned_support_case_state(
  p_case_id uuid,
  p_state public.case_state,
  p_note text
)
returns void
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_actor uuid := auth.uid();
  v_case public.community_cases%rowtype;
  v_note text := trim(coalesce(p_note, ''));
begin
  if v_actor is null then
    raise exception 'A signed-in account is required.';
  end if;
  if char_length(v_note) not between 3 and 1000 then
    raise exception 'Provide a case update note between 3 and 1000 characters.';
  end if;
  select * into v_case from public.community_cases where id = p_case_id for update;
  if not found then
    raise exception 'This support case is not available.';
  end if;
  if not (v_case.assigned_staff_id = v_actor or private.has_verified_system_admin()) then
    raise exception 'Assigned case staff access is required.';
  end if;
  if p_state not in ('IN_REVIEW', 'IN_PROGRESS', 'RESOLVED', 'CLOSED') then
    raise exception 'Choose a valid staff case state.';
  end if;

  update public.community_cases
     set state = p_state,
         closed_at = case when p_state in ('RESOLVED', 'CLOSED') then now() else null end
   where id = v_case.id;
  insert into public.case_messages(case_id, author_id, body)
  values (v_case.id, v_actor, v_note);
  perform private.ops_log_audit(
    v_actor,
    'SUPPORT_CASE_' || p_state::text,
    'COMMUNITY_CASE',
    v_case.id,
    'SUCCESS',
    jsonb_build_object('note', v_note),
    'support_case'
  );
end;
$$;

create or replace function public.list_support_case_messages(p_case_id uuid)
returns table(
  id uuid,
  author_id uuid,
  body text,
  attachments jsonb,
  created_at timestamptz
)
language plpgsql
security definer
set search_path = public, pg_temp
as $$
begin
  if auth.uid() is null or not private.can_access_case(p_case_id) then
    raise exception 'This support case is not available.';
  end if;
  return query
  select m.id, m.author_id, m.body, m.attachments, m.created_at
  from public.case_messages m
  where m.case_id = p_case_id
  order by m.created_at
  limit 500;
end;
$$;

create or replace function public.add_support_case_message(
  p_case_id uuid,
  p_body text,
  p_attachments jsonb default '[]'::jsonb
)
returns uuid
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_actor uuid := auth.uid();
  v_message_id uuid;
  v_body text := trim(coalesce(p_body, ''));
begin
  if v_actor is null or not private.can_access_case(p_case_id) then
    raise exception 'This support case is not available.';
  end if;
  if char_length(v_body) not between 1 and 10000 or jsonb_typeof(coalesce(p_attachments, '[]'::jsonb)) <> 'array' then
    raise exception 'Provide a valid support-case message.';
  end if;
  insert into public.case_messages(case_id, author_id, body, attachments)
  values (p_case_id, v_actor, v_body, coalesce(p_attachments, '[]'::jsonb))
  returning id into v_message_id;
  update public.community_cases set updated_at = now() where id = p_case_id;
  perform private.ops_log_audit(
    v_actor,
    'SUPPORT_CASE_MESSAGE_ADDED',
    'COMMUNITY_CASE',
    p_case_id,
    'SUCCESS',
    jsonb_build_object('message_id', v_message_id),
    'support_case'
  );
  return v_message_id;
end;
$$;

revoke all on function private.has_verified_system_admin() from public, anon, authenticated;
revoke all on function private.can_access_case(uuid) from public, anon, authenticated;
revoke all on function private.touch_community_case_updated_at() from public, anon, authenticated;

revoke all on function public.submit_support_case(text, text, text, smallint, text, jsonb) from public, anon;
grant execute on function public.submit_support_case(text, text, text, smallint, text, jsonb) to authenticated;
revoke all on function public.list_my_support_cases() from public, anon;
grant execute on function public.list_my_support_cases() to authenticated;
revoke all on function public.list_assigned_support_cases() from public, anon;
grant execute on function public.list_assigned_support_cases() to authenticated;
revoke all on function public.assign_support_case(uuid, uuid, text) from public, anon;
grant execute on function public.assign_support_case(uuid, uuid, text) to authenticated;
revoke all on function public.update_assigned_support_case_state(uuid, public.case_state, text) from public, anon;
grant execute on function public.update_assigned_support_case_state(uuid, public.case_state, text) to authenticated;
revoke all on function public.list_support_case_messages(uuid) from public, anon;
grant execute on function public.list_support_case_messages(uuid) to authenticated;
revoke all on function public.add_support_case_message(uuid, text, jsonb) from public, anon;
grant execute on function public.add_support_case_message(uuid, text, jsonb) to authenticated;
