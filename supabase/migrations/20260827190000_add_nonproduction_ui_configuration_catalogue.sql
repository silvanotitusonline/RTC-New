-- RTC Community non-production Phase 1 only.
-- This migration is intentionally source-controlled but MUST NOT be applied without a
-- separate explicit authorization for the isolated non-production Supabase project.
-- It never targets or refers to a production project.

create table if not exists public.ui_configuration_versions (
  id uuid primary key default gen_random_uuid(),
  audience_key text not null default 'RESIDENT_GLOBAL'
    check (audience_key = 'RESIDENT_GLOBAL'),
  state text not null default 'DRAFT'
    check (state in ('DRAFT', 'PUBLISHED', 'SUPERSEDED', 'REJECTED')),
  schema_version smallint not null default 1
    check (schema_version = 1),
  configuration jsonb not null,
  created_by uuid not null references auth.users(id) on delete restrict,
  created_reason text not null
    check (char_length(trim(created_reason)) between 3 and 500),
  published_by uuid references auth.users(id) on delete restrict,
  published_reason text
    check (published_reason is null or char_length(trim(published_reason)) between 3 and 500),
  based_on_version_id uuid references public.ui_configuration_versions(id) on delete restrict,
  created_at timestamptz not null default now(),
  published_at timestamptz,
  superseded_at timestamptz,
  constraint ui_configuration_versions_published_fields check (
    (state = 'PUBLISHED' and published_by is not null and published_at is not null and published_reason is not null)
    or state <> 'PUBLISHED'
  )
);

create unique index if not exists ui_configuration_one_published_per_audience_idx
  on public.ui_configuration_versions(audience_key)
  where state = 'PUBLISHED';

create index if not exists ui_configuration_versions_audience_created_idx
  on public.ui_configuration_versions(audience_key, created_at desc);

create table if not exists public.ui_configuration_events (
  id bigint generated always as identity primary key,
  configuration_version_id uuid not null references public.ui_configuration_versions(id) on delete restrict,
  actor_id uuid not null references auth.users(id) on delete restrict,
  event_type text not null check (event_type in ('DRAFT_CREATED', 'PUBLISHED', 'SUPERSEDED', 'REJECTED', 'REVERTED')),
  reason text not null check (char_length(trim(reason)) between 3 and 1000),
  metadata jsonb not null default '{}'::jsonb,
  occurred_at timestamptz not null default now()
);

create index if not exists ui_configuration_events_version_occurred_idx
  on public.ui_configuration_events(configuration_version_id, occurred_at desc);

alter table public.ui_configuration_versions enable row level security;
alter table public.ui_configuration_events enable row level security;

-- There are deliberately no direct client read/write policies. Resident reads and all
-- administrator transitions go through the narrow RPC contracts below.
revoke all on table public.ui_configuration_versions from anon, authenticated;
revoke all on table public.ui_configuration_events from anon, authenticated;

create or replace function private.ui_configuration_validate_payload(p_configuration jsonb)
returns void
language plpgsql
set search_path = public, pg_temp
as $$
declare
  v_sections jsonb;
  v_section text;
  v_section_count integer;
  v_distinct_count integer;
begin
  if jsonb_typeof(p_configuration) <> 'object' then
    raise exception 'UI configuration must be a JSON object.';
  end if;
  if p_configuration ? 'appearance_preset'
     or p_configuration ? 'typography_scale'
     or p_configuration ? 'welcome'
     or p_configuration ? 'external_url'
     or p_configuration ? 'script'
     or p_configuration ? 'asset_url' then
    raise exception 'Phase 1 configuration supports only the approved Home section catalogue.';
  end if;
  if not (p_configuration ? 'home')
     or jsonb_typeof(p_configuration -> 'home') <> 'object'
     or not (p_configuration -> 'home' ? 'sections')
     or jsonb_typeof(p_configuration -> 'home' -> 'sections') <> 'array' then
    raise exception 'A Home section array is required.';
  end if;

  v_sections := p_configuration -> 'home' -> 'sections';
  v_section_count := jsonb_array_length(v_sections);
  if v_section_count not between 3 and 8 then
    raise exception 'Select between 3 and 8 approved Home sections.';
  end if;

  select count(distinct value) into v_distinct_count
  from jsonb_array_elements_text(v_sections) as elements(value);
  if v_distinct_count <> v_section_count then
    raise exception 'Home sections cannot contain duplicates.';
  end if;

  for v_section in select value from jsonb_array_elements_text(v_sections) as elements(value)
  loop
    if v_section not in (
      'WELCOME',
      'COMMUNITY_SNAPSHOT',
      'QUICK_ACCESS',
      'CONTINUE_DRAFT',
      'PENDING_SYNC',
      'NEXT_STEPS',
      'LATEST_UPDATES',
      'HELP'
    ) then
      raise exception 'Unsupported Home section: %', v_section;
    end if;
  end loop;

  if not (v_sections ? 'WELCOME') or not (v_sections ? 'HELP') then
    raise exception 'Welcome and Help are mandatory Home sections.';
  end if;
end;
$$;

create or replace function public.ui_configuration_create_draft(
  p_configuration jsonb,
  p_reason text,
  p_based_on_version_id uuid default null
)
returns uuid
language plpgsql
security definer
set search_path = auth, public, pg_temp
as $$
declare
  v_actor uuid := private.access_assert_system_admin();
  v_reason text := trim(coalesce(p_reason, ''));
  v_id uuid;
begin
  if char_length(v_reason) not between 3 and 500 then
    raise exception 'Provide a draft reason between 3 and 500 characters.';
  end if;
  perform private.ui_configuration_validate_payload(p_configuration);
  if p_based_on_version_id is not null
     and not exists (
       select 1 from public.ui_configuration_versions
       where id = p_based_on_version_id and audience_key = 'RESIDENT_GLOBAL'
     ) then
    raise exception 'The selected base configuration is unavailable.';
  end if;

  insert into public.ui_configuration_versions(
    audience_key, state, schema_version, configuration, created_by, created_reason, based_on_version_id
  ) values (
    'RESIDENT_GLOBAL', 'DRAFT', 1, p_configuration, v_actor, v_reason, p_based_on_version_id
  ) returning id into v_id;

  insert into public.ui_configuration_events(configuration_version_id, actor_id, event_type, reason, metadata)
  values (v_id, v_actor, 'DRAFT_CREATED', v_reason, jsonb_build_object('audience_key', 'RESIDENT_GLOBAL', 'schema_version', 1));
  perform private.ops_log_audit(v_actor, 'UI_CONFIGURATION_DRAFT_CREATED', 'UI_CONFIGURATION', v_id, 'SUCCESS', jsonb_build_object('audience_key', 'RESIDENT_GLOBAL'));
  return v_id;
end;
$$;

create or replace function public.ui_configuration_publish_draft(
  p_draft_id uuid,
  p_reason text,
  p_confirmation text
)
returns uuid
language plpgsql
security definer
set search_path = auth, public, pg_temp
as $$
declare
  v_actor uuid := private.access_assert_system_admin();
  v_reason text := trim(coalesce(p_reason, ''));
  v_draft public.ui_configuration_versions%rowtype;
  v_previous public.ui_configuration_versions%rowtype;
begin
  if trim(coalesce(p_confirmation, '')) <> 'PUBLISH UI CONFIGURATION' then
    raise exception 'Type PUBLISH UI CONFIGURATION before publishing.';
  end if;
  if char_length(v_reason) not between 3 and 500 then
    raise exception 'Provide a publication reason between 3 and 500 characters.';
  end if;

  select * into v_draft from public.ui_configuration_versions
  where id = p_draft_id and state = 'DRAFT' and audience_key = 'RESIDENT_GLOBAL'
  for update;
  if not found then
    raise exception 'Only a current global Home configuration draft can be published.';
  end if;
  perform private.ui_configuration_validate_payload(v_draft.configuration);

  select * into v_previous from public.ui_configuration_versions
  where audience_key = 'RESIDENT_GLOBAL' and state = 'PUBLISHED'
  for update;
  if found then
    update public.ui_configuration_versions
       set state = 'SUPERSEDED', superseded_at = now()
     where id = v_previous.id;
    insert into public.ui_configuration_events(configuration_version_id, actor_id, event_type, reason, metadata)
    values (v_previous.id, v_actor, 'SUPERSEDED', v_reason, jsonb_build_object('superseded_by', v_draft.id));
  end if;

  update public.ui_configuration_versions
     set state = 'PUBLISHED', published_by = v_actor, published_reason = v_reason, published_at = now()
   where id = v_draft.id;
  insert into public.ui_configuration_events(configuration_version_id, actor_id, event_type, reason, metadata)
  values (v_draft.id, v_actor, 'PUBLISHED', v_reason, jsonb_build_object('audience_key', 'RESIDENT_GLOBAL'));
  perform private.ops_log_audit(v_actor, 'UI_CONFIGURATION_PUBLISHED', 'UI_CONFIGURATION', v_draft.id, 'SUCCESS', jsonb_build_object('audience_key', 'RESIDENT_GLOBAL'));
  return v_draft.id;
end;
$$;

create or replace function public.ui_configuration_revert(
  p_version_id uuid,
  p_reason text,
  p_confirmation text
)
returns uuid
language plpgsql
security definer
set search_path = auth, public, pg_temp
as $$
declare
  v_actor uuid := private.access_assert_system_admin();
  v_reason text := trim(coalesce(p_reason, ''));
  v_source public.ui_configuration_versions%rowtype;
  v_new_id uuid;
begin
  if trim(coalesce(p_confirmation, '')) <> 'REVERT UI CONFIGURATION' then
    raise exception 'Type REVERT UI CONFIGURATION before reverting.';
  end if;
  if char_length(v_reason) not between 3 and 500 then
    raise exception 'Provide a reversion reason between 3 and 500 characters.';
  end if;
  select * into v_source from public.ui_configuration_versions
  where id = p_version_id and audience_key = 'RESIDENT_GLOBAL' and state in ('PUBLISHED', 'SUPERSEDED')
  for share;
  if not found then
    raise exception 'Select a published configuration from the global version history.';
  end if;
  perform private.ui_configuration_validate_payload(v_source.configuration);

  insert into public.ui_configuration_versions(
    audience_key, state, schema_version, configuration, created_by, created_reason, published_by, published_reason, based_on_version_id, published_at
  ) values (
    'RESIDENT_GLOBAL', 'DRAFT', 1, v_source.configuration, v_actor, v_reason, null, null, v_source.id, null
  ) returning id into v_new_id;
  perform public.ui_configuration_publish_draft(v_new_id, v_reason, 'PUBLISH UI CONFIGURATION');
  insert into public.ui_configuration_events(configuration_version_id, actor_id, event_type, reason, metadata)
  values (v_new_id, v_actor, 'REVERTED', v_reason, jsonb_build_object('reverted_from', v_source.id));
  perform private.ops_log_audit(v_actor, 'UI_CONFIGURATION_REVERTED', 'UI_CONFIGURATION', v_new_id, 'SUCCESS', jsonb_build_object('reverted_from', v_source.id));
  return v_new_id;
end;
$$;

create or replace function public.ui_configuration_effective_global_home()
returns table(
  version_id uuid,
  schema_version smallint,
  configuration jsonb,
  published_at timestamptz
)
language plpgsql
security definer
set search_path = auth, public, pg_temp
as $$
begin
  if auth.uid() is null then
    raise exception 'A signed-in account is required.';
  end if;
  return query
  select id, schema_version, configuration, published_at
    from public.ui_configuration_versions
   where audience_key = 'RESIDENT_GLOBAL' and state = 'PUBLISHED'
   order by published_at desc
   limit 1;
end;
$$;

revoke all on function private.ui_configuration_validate_payload(jsonb) from public;
revoke all on function public.ui_configuration_create_draft(jsonb, text, uuid) from public, anon, authenticated, service_role;
revoke all on function public.ui_configuration_publish_draft(uuid, text, text) from public, anon, authenticated, service_role;
revoke all on function public.ui_configuration_revert(uuid, text, text) from public, anon, authenticated, service_role;
revoke all on function public.ui_configuration_effective_global_home() from public, anon, authenticated, service_role;
grant execute on function public.ui_configuration_create_draft(jsonb, text, uuid) to authenticated;
grant execute on function public.ui_configuration_publish_draft(uuid, text, text) to authenticated;
grant execute on function public.ui_configuration_revert(uuid, text, text) to authenticated;
grant execute on function public.ui_configuration_effective_global_home() to authenticated;
