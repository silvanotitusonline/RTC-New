-- Community Events and resident unified Inbox.
-- Coder B-owned migration. It has no dependency on Public Reports / Civic tables.

begin;

create type public.community_event_state as enum ('DRAFT', 'PUBLISHED', 'CANCELLED');

create table public.community_events (
  id uuid primary key default gen_random_uuid(),
  title text not null,
  description text not null,
  starts_at timestamptz not null,
  ends_at timestamptz not null,
  time_zone text not null default 'Africa/Johannesburg',
  locality text null,
  venue_label text not null,
  is_local boolean not null default true,
  state public.community_event_state not null default 'DRAFT',
  cancellation_reason text null,
  created_by uuid not null references public.profiles(id) on delete restrict,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  published_at timestamptz null,
  cancelled_at timestamptz null,
  constraint community_events_title_check
    check (char_length(trim(title)) between 3 and 180),
  constraint community_events_description_check
    check (char_length(trim(description)) between 3 and 10000),
  constraint community_events_ends_after_starts_check
    check (ends_at > starts_at),
  constraint community_events_time_zone_check
    check (char_length(trim(time_zone)) between 3 and 80),
  constraint community_events_locality_check
    check (locality is null or char_length(trim(locality)) between 2 and 120),
  constraint community_events_venue_label_check
    check (char_length(trim(venue_label)) between 2 and 180),
  constraint community_events_cancellation_reason_check
    check (
      (state = 'CANCELLED' and char_length(trim(coalesce(cancellation_reason, ''))) between 3 and 500)
      or (state <> 'CANCELLED' and cancellation_reason is null)
    )
);

create index community_events_published_starts_idx
  on public.community_events (starts_at asc)
  where state = 'PUBLISHED';

create index community_events_published_locality_starts_idx
  on public.community_events (lower(locality), starts_at asc)
  where state = 'PUBLISHED' and locality is not null;

create index community_events_admin_state_updated_idx
  on public.community_events (state, updated_at desc);

alter table public.community_events enable row level security;
revoke all on table public.community_events from public, anon, authenticated;

create policy community_events_rpc_only_deny_direct_client_access
  on public.community_events
  as restrictive
  for all
  to authenticated
  using (false)
  with check (false);

create table public.resident_inbox_read_markers (
  reader_id uuid not null references public.profiles(id) on delete cascade,
  source_type text not null,
  source_id uuid not null,
  read_at timestamptz not null default now(),
  primary key (reader_id, source_type, source_id),
  constraint resident_inbox_read_markers_source_type_check
    check (source_type in ('NOTIFICATION', 'SUPPORT_MESSAGE', 'SERVICE_CENTRE_MESSAGE'))
);

create index resident_inbox_read_markers_reader_read_idx
  on public.resident_inbox_read_markers (reader_id, read_at desc);

alter table public.resident_inbox_read_markers enable row level security;
revoke all on table public.resident_inbox_read_markers from public, anon, authenticated;

create policy resident_inbox_read_markers_rpc_only_deny_direct_client_access
  on public.resident_inbox_read_markers
  as restrictive
  for all
  to authenticated
  using (false)
  with check (false);

create or replace function private.assert_community_event_manager()
returns uuid
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_actor uuid := auth.uid();
begin
  if v_actor is null
    or not private.has_any_role(array['CONTENT_EDITOR', 'SYSTEM_ADMIN']::public.app_role[]) then
    raise exception 'Content Editor or System Administrator access is required.';
  end if;
  return v_actor;
end;
$$;

create or replace function private.validate_community_event_input(
  p_title text,
  p_description text,
  p_starts_at timestamptz,
  p_ends_at timestamptz,
  p_time_zone text,
  p_locality text,
  p_venue_label text
)
returns void
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_title text := trim(coalesce(p_title, ''));
  v_description text := trim(coalesce(p_description, ''));
  v_time_zone text := trim(coalesce(p_time_zone, ''));
  v_locality text := nullif(trim(coalesce(p_locality, '')), '');
  v_venue_label text := trim(coalesce(p_venue_label, ''));
begin
  if char_length(v_title) not between 3 and 180
    or char_length(v_description) not between 3 and 10000
    or p_starts_at is null
    or p_ends_at is null
    or p_ends_at <= p_starts_at
    or char_length(v_venue_label) not between 2 and 180
    or (v_locality is not null and char_length(v_locality) not between 2 and 120)
    or not exists (select 1 from pg_timezone_names where name = v_time_zone) then
    raise exception 'Provide valid Community Event details.';
  end if;
end;
$$;

create or replace function public.community_events_page(
  p_limit integer default 20,
  p_offset integer default 0,
  p_locality text default null
)
returns table (
  id uuid,
  title text,
  description text,
  starts_at timestamptz,
  ends_at timestamptz,
  time_zone text,
  locality text,
  venue_label text,
  is_local boolean
)
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_actor uuid := auth.uid();
  v_limit integer := least(greatest(coalesce(p_limit, 20), 1), 50);
  v_offset integer := least(greatest(coalesce(p_offset, 0), 0), 1000);
  v_locality text := nullif(trim(coalesce(p_locality, '')), '');
begin
  if v_actor is null then
    raise exception 'A signed-in account is required.';
  end if;

  return query
  select
    e.id, e.title, e.description, e.starts_at, e.ends_at, e.time_zone,
    e.locality, e.venue_label, e.is_local
  from public.community_events e
  where e.state = 'PUBLISHED'
    and e.ends_at >= now()
  order by
    case
      when v_locality is not null and lower(e.locality) = lower(v_locality) then 0
      when e.is_local then 1
      else 2
    end,
    e.starts_at asc,
    e.id asc
  limit v_limit
  offset v_offset;
end;
$$;

create or replace function public.community_events_admin_page(
  p_limit integer default 50,
  p_offset integer default 0
)
returns table (
  id uuid,
  title text,
  description text,
  starts_at timestamptz,
  ends_at timestamptz,
  time_zone text,
  locality text,
  venue_label text,
  is_local boolean,
  state public.community_event_state,
  cancellation_reason text,
  created_at timestamptz,
  updated_at timestamptz,
  published_at timestamptz,
  cancelled_at timestamptz
)
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_limit integer := least(greatest(coalesce(p_limit, 50), 1), 100);
  v_offset integer := least(greatest(coalesce(p_offset, 0), 0), 1000);
begin
  perform private.assert_community_event_manager();

  return query
  select
    e.id, e.title, e.description, e.starts_at, e.ends_at, e.time_zone,
    e.locality, e.venue_label, e.is_local, e.state, e.cancellation_reason,
    e.created_at, e.updated_at, e.published_at, e.cancelled_at
  from public.community_events e
  order by e.starts_at asc, e.id asc
  limit v_limit
  offset v_offset;
end;
$$;

create or replace function public.upsert_community_event(
  p_event_id uuid,
  p_title text,
  p_description text,
  p_starts_at timestamptz,
  p_ends_at timestamptz,
  p_time_zone text,
  p_locality text,
  p_venue_label text,
  p_is_local boolean
)
returns uuid
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_actor uuid := private.assert_community_event_manager();
  v_event_id uuid;
  v_existing_state public.community_event_state;
  v_title text := trim(coalesce(p_title, ''));
  v_description text := trim(coalesce(p_description, ''));
  v_time_zone text := trim(coalesce(p_time_zone, ''));
  v_locality text := nullif(trim(coalesce(p_locality, '')), '');
  v_venue_label text := trim(coalesce(p_venue_label, ''));
begin
  perform private.validate_community_event_input(
    v_title, v_description, p_starts_at, p_ends_at, v_time_zone, v_locality, v_venue_label
  );

  if p_event_id is null then
    insert into public.community_events (
      title, description, starts_at, ends_at, time_zone, locality, venue_label, is_local, created_by
    ) values (
      v_title, v_description, p_starts_at, p_ends_at, v_time_zone, v_locality, v_venue_label,
      coalesce(p_is_local, true), v_actor
    )
    returning id into v_event_id;
    return v_event_id;
  end if;

  select state into v_existing_state
  from public.community_events
  where id = p_event_id
  for update;

  if not found then
    raise exception 'This Community Event is not available.';
  end if;
  if v_existing_state = 'CANCELLED' then
    raise exception 'Cancelled Community Events cannot be edited.';
  end if;

  update public.community_events
     set title = v_title,
         description = v_description,
         starts_at = p_starts_at,
         ends_at = p_ends_at,
         time_zone = v_time_zone,
         locality = v_locality,
         venue_label = v_venue_label,
         is_local = coalesce(p_is_local, true),
         updated_at = now()
   where id = p_event_id
   returning id into v_event_id;

  return v_event_id;
end;
$$;

create or replace function public.publish_community_event(p_event_id uuid)
returns void
language plpgsql
security definer
set search_path = public, pg_temp
as $$
begin
  perform private.assert_community_event_manager();

  update public.community_events
     set state = 'PUBLISHED',
         published_at = coalesce(published_at, now()),
         updated_at = now()
   where id = p_event_id
     and state = 'DRAFT';

  if not found then
    raise exception 'Only a draft Community Event can be published.';
  end if;
end;
$$;

create or replace function public.cancel_community_event(
  p_event_id uuid,
  p_reason text
)
returns void
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_reason text := trim(coalesce(p_reason, ''));
begin
  perform private.assert_community_event_manager();

  if char_length(v_reason) not between 3 and 500 then
    raise exception 'Provide a cancellation reason between 3 and 500 characters.';
  end if;

  update public.community_events
     set state = 'CANCELLED',
         cancellation_reason = v_reason,
         cancelled_at = now(),
         updated_at = now()
   where id = p_event_id
     and state in ('DRAFT', 'PUBLISHED');

  if not found then
    raise exception 'This Community Event cannot be cancelled.';
  end if;
end;
$$;

create or replace function private.resident_inbox_source_visible(
  p_source_type text,
  p_source_id uuid,
  p_actor uuid
)
returns boolean
language sql
stable
security definer
set search_path = public, pg_temp
as $$
  select case upper(trim(coalesce(p_source_type, '')))
    when 'NOTIFICATION' then exists (
      select 1
      from public.notification_events n
      where n.id = p_source_id
        and n.recipient_id = p_actor
    )
    when 'SUPPORT_MESSAGE' then exists (
      select 1
      from public.case_messages m
      join public.community_cases c on c.id = m.case_id
      where m.id = p_source_id
        and (c.resident_id = p_actor or c.assigned_staff_id = p_actor)
    )
    when 'SERVICE_CENTRE_MESSAGE' then exists (
      select 1
      from public.service_centre_booking_messages m
      join public.service_centre_bookings b on b.id = m.booking_id
      where m.id = p_source_id
        and (b.customer_user_id = p_actor or b.provider_user_id = p_actor)
    )
    else false
  end;
$$;

create or replace function public.resident_inbox_page(
  p_tab text default 'UPDATES',
  p_limit integer default 30,
  p_offset integer default 0
)
returns table (
  source_type text,
  source_id uuid,
  category text,
  title text,
  body text,
  occurred_at timestamptz,
  route text,
  is_read boolean
)
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_actor uuid := auth.uid();
  v_tab text := upper(trim(coalesce(p_tab, 'UPDATES')));
  v_limit integer := least(greatest(coalesce(p_limit, 30), 1), 50);
  v_offset integer := least(greatest(coalesce(p_offset, 0), 0), 1000);
begin
  if v_actor is null then
    raise exception 'A signed-in account is required.';
  end if;
  if v_tab not in ('UPDATES', 'MESSAGES') then
    raise exception 'Choose UPDATES or MESSAGES.';
  end if;

  return query
  with inbox_items as (
    select
      'NOTIFICATION'::text as source_type,
      n.id as source_id,
      coalesce(nullif(trim(n.notification_type), ''), 'UPDATE') as category,
      n.title,
      n.body,
      n.created_at as occurred_at,
      coalesce(nullif(n.payload ->> 'route', ''), 'inbox?tab=updates') as route
    from public.notification_events n
    where n.recipient_id = v_actor

    union all

    select
      'SUPPORT_MESSAGE'::text,
      m.id,
      'SUPPORT'::text,
      c.title,
      m.body,
      m.created_at,
      'account/support?caseId=' || c.id::text
    from public.case_messages m
    join public.community_cases c on c.id = m.case_id
    where (c.resident_id = v_actor or c.assigned_staff_id = v_actor)
      and m.author_id <> v_actor

    union all

    select
      'SERVICE_CENTRE_MESSAGE'::text,
      m.id,
      'SERVICE_CENTRE'::text,
      coalesce(b.category_name_snapshot, 'Service Centre'),
      m.body,
      m.created_at,
      'services?bookingId=' || b.id::text
    from public.service_centre_booking_messages m
    join public.service_centre_bookings b on b.id = m.booking_id
    where (b.customer_user_id = v_actor or b.provider_user_id = v_actor)
      and m.sender_user_id <> v_actor
  )
  select
    i.source_type,
    i.source_id,
    i.category,
    i.title,
    i.body,
    i.occurred_at,
    i.route,
    exists (
      select 1
      from public.resident_inbox_read_markers marker
      where marker.reader_id = v_actor
        and marker.source_type = i.source_type
        and marker.source_id = i.source_id
    ) as is_read
  from inbox_items i
  where (v_tab = 'UPDATES' and i.source_type = 'NOTIFICATION')
     or (v_tab = 'MESSAGES' and i.source_type in ('SUPPORT_MESSAGE', 'SERVICE_CENTRE_MESSAGE'))
  order by i.occurred_at desc, i.source_id desc
  limit v_limit
  offset v_offset;
end;
$$;

create or replace function public.mark_resident_inbox_item_read(
  p_source_type text,
  p_source_id uuid
)
returns void
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_actor uuid := auth.uid();
  v_source_type text := upper(trim(coalesce(p_source_type, '')));
begin
  if v_actor is null then
    raise exception 'A signed-in account is required.';
  end if;
  if p_source_id is null
    or v_source_type not in ('NOTIFICATION', 'SUPPORT_MESSAGE', 'SERVICE_CENTRE_MESSAGE')
    or not private.resident_inbox_source_visible(v_source_type, p_source_id, v_actor) then
    raise exception 'This Inbox item is not available.';
  end if;

  insert into public.resident_inbox_read_markers(reader_id, source_type, source_id, read_at)
  values (v_actor, v_source_type, p_source_id, now())
  on conflict (reader_id, source_type, source_id)
  do update set read_at = excluded.read_at;
end;
$$;

revoke all on function private.assert_community_event_manager() from public, anon, authenticated;
revoke all on function private.validate_community_event_input(text, text, timestamptz, timestamptz, text, text, text) from public, anon, authenticated;
revoke all on function private.resident_inbox_source_visible(text, uuid, uuid) from public, anon, authenticated;

revoke all on function public.community_events_page(integer, integer, text) from public, anon;
revoke all on function public.community_events_admin_page(integer, integer) from public, anon;
revoke all on function public.upsert_community_event(uuid, text, text, timestamptz, timestamptz, text, text, text, boolean) from public, anon;
revoke all on function public.publish_community_event(uuid) from public, anon;
revoke all on function public.cancel_community_event(uuid, text) from public, anon;
revoke all on function public.resident_inbox_page(text, integer, integer) from public, anon;
revoke all on function public.mark_resident_inbox_item_read(text, uuid) from public, anon;

grant execute on function public.community_events_page(integer, integer, text) to authenticated;
grant execute on function public.community_events_admin_page(integer, integer) to authenticated;
grant execute on function public.upsert_community_event(uuid, text, text, timestamptz, timestamptz, text, text, text, boolean) to authenticated;
grant execute on function public.publish_community_event(uuid) to authenticated;
grant execute on function public.cancel_community_event(uuid, text) to authenticated;
grant execute on function public.resident_inbox_page(text, integer, integer) to authenticated;
grant execute on function public.mark_resident_inbox_item_read(text, uuid) to authenticated;

commit;
