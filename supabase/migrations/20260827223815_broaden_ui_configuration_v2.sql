-- RTC Community non-production Brand & Experience Customizer v2.
-- Production deployment is intentionally out of scope for this migration pass.

alter table public.ui_configuration_versions
  drop constraint if exists ui_configuration_versions_schema_version_check;
alter table public.ui_configuration_versions
  add constraint ui_configuration_versions_schema_version_check
  check (schema_version in (1, 2));

create table if not exists public.ui_configuration_assets (
  id uuid primary key default gen_random_uuid(),
  state text not null default 'STAGED' check (state in ('STAGED','FINALIZED','REJECTED','DELETED')),
  object_path text not null unique,
  mime_type text not null check (mime_type in ('image/jpeg','image/png','image/webp')),
  byte_size integer not null check (byte_size between 1 and 8388608),
  width integer not null check (width between 1 and 8192),
  height integer not null check (height between 1 and 8192),
  created_by uuid not null references auth.users(id) on delete restrict,
  created_at timestamptz not null default now(),
  finalized_at timestamptz,
  deleted_at timestamptz
);
alter table public.ui_configuration_assets enable row level security;

insert into storage.buckets(id, name, public, file_size_limit, allowed_mime_types)
values ('rtc-ui-assets','rtc-ui-assets',false,8388608,array['image/jpeg','image/png','image/webp'])
on conflict (id) do update
set public = excluded.public,
    file_size_limit = excluded.file_size_limit,
    allowed_mime_types = excluded.allowed_mime_types;

create or replace function private.ui_configuration_validate_home(p_home jsonb)
returns void
language plpgsql
set search_path = public, pg_temp
as $$
declare
  v_sections jsonb;
  v_section text;
  v_count integer;
  v_distinct integer;
  v_widget jsonb;
  v_asset_id uuid;
begin
  if jsonb_typeof(p_home) <> 'object' or jsonb_typeof(p_home -> 'sections') <> 'array' then
    raise exception 'A Home section array is required.';
  end if;
  if exists(select 1 from jsonb_object_keys(p_home) k where k not in ('sections','imageWidget')) then
    raise exception 'Home configuration contains an unsupported field.';
  end if;
  v_sections := p_home -> 'sections';
  v_count := jsonb_array_length(v_sections);
  if v_count not between 3 and 8 then raise exception 'Select between 3 and 8 approved Home sections.'; end if;
  select count(distinct value) into v_distinct from jsonb_array_elements_text(v_sections) e(value);
  if v_distinct <> v_count then raise exception 'Home sections cannot contain duplicates.'; end if;
  for v_section in select value from jsonb_array_elements_text(v_sections) e(value) loop
    if v_section not in ('WELCOME','COMMUNITY_SNAPSHOT','QUICK_ACCESS','CONTINUE_DRAFT','PENDING_SYNC','NEXT_STEPS','LATEST_UPDATES','HELP') then
      raise exception 'Unsupported Home section: %', v_section;
    end if;
  end loop;
  if not (v_sections ? 'WELCOME') or not (v_sections ? 'HELP') then
    raise exception 'Welcome and Help are mandatory Home sections.';
  end if;

  v_widget := p_home -> 'imageWidget';
  if v_widget is null or v_widget = 'null'::jsonb then return; end if;
  if jsonb_typeof(v_widget) <> 'object' then raise exception 'Home image widget must be an object.'; end if;
  if exists(select 1 from jsonb_object_keys(v_widget) k where k not in ('assetId','anchorSection','placement','aspectPreset','contentScale','altText','caption')) then
    raise exception 'Home image widget contains an unsupported field.';
  end if;
  begin v_asset_id := (v_widget ->> 'assetId')::uuid; exception when others then raise exception 'Home image assetId must be a UUID.'; end;
  if not exists(select 1 from public.ui_configuration_assets where id=v_asset_id and state='FINALIZED') then
    raise exception 'Home image asset must be finalized before it can be saved in configuration.';
  end if;
  if not (v_sections ? coalesce(v_widget ->> 'anchorSection','')) then raise exception 'Home image anchor must reference a visible Home section.'; end if;
  if coalesce(v_widget ->> 'placement','') not in ('BEFORE','AFTER') then raise exception 'Unsupported Home image placement.'; end if;
  if coalesce(v_widget ->> 'aspectPreset','') not in ('WIDE','STANDARD','SQUARE') then raise exception 'Unsupported Home image aspect.'; end if;
  if coalesce(v_widget ->> 'contentScale','') not in ('CROP','FIT') then raise exception 'Unsupported Home image fit.'; end if;
  if char_length(trim(coalesce(v_widget ->> 'altText',''))) not between 3 and 180 then raise exception 'Home image alt text must contain 3 to 180 characters.'; end if;
  if char_length(trim(coalesce(v_widget ->> 'caption',''))) > 240 then raise exception 'Home image caption cannot exceed 240 characters.'; end if;
end;
$$;

create or replace function private.ui_configuration_validate_presentation(p_value jsonb)
returns void
language plpgsql
immutable
set search_path = public, pg_temp
as $$
begin
  if jsonb_typeof(p_value) <> 'object' then raise exception 'Screen presentation must be an object.'; end if;
  if exists(select 1 from jsonb_object_keys(p_value) k where k not in ('headerStyle','density')) then raise exception 'Screen presentation contains an unsupported field.'; end if;
  if coalesce(p_value ->> 'headerStyle','STANDARD') not in ('COMPACT','STANDARD','HERO') then raise exception 'Unsupported screen header style.'; end if;
  if coalesce(p_value ->> 'density','STANDARD') not in ('COMPACT','STANDARD','COMFORTABLE') then raise exception 'Unsupported screen density.'; end if;
end;
$$;

create or replace function private.ui_configuration_validate_payload(p_configuration jsonb)
returns void
language plpgsql
set search_path = public, pg_temp
as $$
declare
  v_schema integer;
  v_appearance jsonb;
  v_typography jsonb;
  v_welcome jsonb;
  v_screens jsonb;
  v_launcher jsonb;
  v_key text;
begin
  if jsonb_typeof(p_configuration) <> 'object' then raise exception 'UI configuration must be a JSON object.'; end if;
  v_schema := coalesce((p_configuration ->> 'schemaVersion')::integer, 1);

  if v_schema = 1 then
    if exists(select 1 from jsonb_object_keys(p_configuration) k where k not in ('schemaVersion','home')) then
      raise exception 'Schema v1 supports only Home layout configuration.';
    end if;
    perform private.ui_configuration_validate_home(p_configuration -> 'home');
    return;
  end if;

  if v_schema <> 2 then raise exception 'Unsupported UI configuration schema version.'; end if;
  if exists(select 1 from jsonb_object_keys(p_configuration) k where k not in ('schemaVersion','appearance','typography','welcome','home','screens','launcher')) then
    raise exception 'UI configuration contains an unsupported top-level field.';
  end if;

  v_appearance := p_configuration -> 'appearance';
  if jsonb_typeof(v_appearance) <> 'object' then raise exception 'Appearance configuration is required.'; end if;
  if exists(select 1 from jsonb_object_keys(v_appearance) k where k not in ('primarySeed','accentSeed','backgroundSeed','shapePreset','densityPreset')) then raise exception 'Appearance configuration contains an unsupported field.'; end if;
  if coalesce(v_appearance->>'primarySeed','') !~ '^#[0-9A-Fa-f]{6}$' or coalesce(v_appearance->>'accentSeed','') !~ '^#[0-9A-Fa-f]{6}$' or coalesce(v_appearance->>'backgroundSeed','') !~ '^#[0-9A-Fa-f]{6}$' then raise exception 'Appearance colours must use #RRGGBB format.'; end if;
  if coalesce(v_appearance->>'shapePreset','') not in ('SOFT','ROUNDED','SHARP') then raise exception 'Unsupported shape preset.'; end if;
  if coalesce(v_appearance->>'densityPreset','') not in ('COMPACT','STANDARD','COMFORTABLE') then raise exception 'Unsupported global density preset.'; end if;

  v_typography := p_configuration -> 'typography';
  if jsonb_typeof(v_typography) <> 'object' then raise exception 'Typography configuration is required.'; end if;
  if exists(select 1 from jsonb_object_keys(v_typography) k where k not in ('fontFamily','scalePreset','weightPreset')) then raise exception 'Typography configuration contains an unsupported field.'; end if;
  if coalesce(v_typography->>'fontFamily','') not in ('SYSTEM_SANS','SYSTEM_SERIF','SYSTEM_MONO') then raise exception 'Unsupported packaged font family.'; end if;
  if coalesce(v_typography->>'scalePreset','') not in ('COMPACT','STANDARD','COMFORTABLE','LARGE') then raise exception 'Unsupported typography scale.'; end if;
  if coalesce(v_typography->>'weightPreset','') not in ('LIGHTER','STANDARD','STRONG') then raise exception 'Unsupported typography weight.'; end if;

  v_welcome := p_configuration -> 'welcome';
  if jsonb_typeof(v_welcome) <> 'object' then raise exception 'Welcome configuration is required.'; end if;
  if exists(select 1 from jsonb_object_keys(v_welcome) k where k not in ('headline','supportingText','primaryActionLabel','secondaryActionLabel','launchTreatment')) then raise exception 'Welcome configuration contains an unsupported field.'; end if;
  if char_length(trim(coalesce(v_welcome->>'headline',''))) not between 3 and 100 then raise exception 'Welcome headline must contain 3 to 100 characters.'; end if;
  if char_length(trim(coalesce(v_welcome->>'supportingText',''))) not between 3 and 280 then raise exception 'Welcome supporting text must contain 3 to 280 characters.'; end if;
  if char_length(trim(coalesce(v_welcome->>'primaryActionLabel',''))) not between 1 and 40 or char_length(trim(coalesce(v_welcome->>'secondaryActionLabel',''))) not between 1 and 40 then raise exception 'Welcome action labels must contain 1 to 40 characters.'; end if;
  if coalesce(v_welcome->>'launchTreatment','') not in ('BRAND_PANEL','MINIMAL','IMMERSIVE') then raise exception 'Unsupported launch treatment.'; end if;

  perform private.ui_configuration_validate_home(p_configuration -> 'home');

  v_screens := p_configuration -> 'screens';
  if jsonb_typeof(v_screens) <> 'object' then raise exception 'Screen presentation configuration is required.'; end if;
  if exists(select 1 from jsonb_object_keys(v_screens) k where k not in ('community','explore','support','account','notifications','search','help','marketplace','marketplaceSearch','marketplaceMap','marketplaceAccount')) then raise exception 'Screens configuration contains an unsupported field.'; end if;
  foreach v_key in array array['community','explore','support','account','notifications','search','help','marketplace','marketplaceSearch','marketplaceMap','marketplaceAccount'] loop
    if not (v_screens ? v_key) then raise exception 'Missing screen presentation: %', v_key; end if;
    perform private.ui_configuration_validate_presentation(v_screens -> v_key);
  end loop;

  v_launcher := p_configuration -> 'launcher';
  if jsonb_typeof(v_launcher) <> 'object' or exists(select 1 from jsonb_object_keys(v_launcher) k where k not in ('recommendedIconId')) then raise exception 'Launcher configuration is invalid.'; end if;
  if coalesce(v_launcher->>'recommendedIconId','') not in ('DEFAULT','GOLD','EMERALD','MONOCHROME') then raise exception 'Unsupported packaged launcher icon.'; end if;
end;
$$;

create or replace function private.ui_configuration_upgrade_to_v2(p_configuration jsonb)
returns jsonb
language plpgsql
stable
set search_path = public, pg_temp
as $$
begin
  if coalesce((p_configuration->>'schemaVersion')::integer,1) = 2 then return p_configuration; end if;
  return jsonb_build_object(
    'schemaVersion',2,
    'appearance',jsonb_build_object('primarySeed','#2EC27E','accentSeed','#D4AF37','backgroundSeed','#0C1013','shapePreset','ROUNDED','densityPreset','STANDARD'),
    'typography',jsonb_build_object('fontFamily','SYSTEM_SANS','scalePreset','STANDARD','weightPreset','STANDARD'),
    'welcome',jsonb_build_object('headline','Rise Tsantsabane Communities','supportingText','Community services, information and participation in one place.','primaryActionLabel','Get started','secondaryActionLabel','Sign in','launchTreatment','BRAND_PANEL'),
    'home',p_configuration->'home',
    'screens',jsonb_build_object(
      'community',jsonb_build_object('headerStyle','HERO','density','STANDARD'),
      'explore',jsonb_build_object('headerStyle','HERO','density','STANDARD'),
      'support',jsonb_build_object('headerStyle','STANDARD','density','STANDARD'),
      'account',jsonb_build_object('headerStyle','STANDARD','density','STANDARD'),
      'notifications',jsonb_build_object('headerStyle','STANDARD','density','STANDARD'),
      'search',jsonb_build_object('headerStyle','STANDARD','density','STANDARD'),
      'help',jsonb_build_object('headerStyle','STANDARD','density','STANDARD'),
      'marketplace',jsonb_build_object('headerStyle','HERO','density','STANDARD'),
      'marketplaceSearch',jsonb_build_object('headerStyle','STANDARD','density','STANDARD'),
      'marketplaceMap',jsonb_build_object('headerStyle','COMPACT','density','STANDARD'),
      'marketplaceAccount',jsonb_build_object('headerStyle','STANDARD','density','STANDARD')
    ),
    'launcher',jsonb_build_object('recommendedIconId','DEFAULT')
  );
end;
$$;

create or replace function public.ui_configuration_create_draft(p_configuration jsonb, p_reason text, p_based_on_version_id uuid default null)
returns uuid
language plpgsql
security definer
set search_path = auth, public, pg_temp
as $$
declare
  v_actor uuid := private.access_assert_system_admin();
  v_reason text := trim(coalesce(p_reason,''));
  v_id uuid;
  v_configuration jsonb := p_configuration;
begin
  if char_length(v_reason) not between 3 and 500 then raise exception 'Provide a draft reason between 3 and 500 characters.'; end if;
  if coalesce((v_configuration->>'schemaVersion')::integer,0) <> 2 then raise exception 'New UI configuration drafts must use schema version 2.'; end if;
  perform private.ui_configuration_validate_payload(v_configuration);
  if p_based_on_version_id is not null and not exists(select 1 from public.ui_configuration_versions where id=p_based_on_version_id and audience_key='RESIDENT_GLOBAL') then raise exception 'The selected base configuration is unavailable.'; end if;
  insert into public.ui_configuration_versions(audience_key,state,schema_version,configuration,created_by,created_reason,based_on_version_id)
  values('RESIDENT_GLOBAL','DRAFT',2,v_configuration,v_actor,v_reason,p_based_on_version_id) returning id into v_id;
  insert into public.ui_configuration_events(configuration_version_id,actor_id,event_type,reason,metadata)
  values(v_id,v_actor,'DRAFT_CREATED',v_reason,jsonb_build_object('audience_key','RESIDENT_GLOBAL','schema_version',2));
  perform private.ops_log_audit(v_actor,'UI_CONFIGURATION_DRAFT_CREATED','UI_CONFIGURATION',v_id,'SUCCESS',jsonb_build_object('audience_key','RESIDENT_GLOBAL','schema_version',2));
  return v_id;
end;
$$;

create or replace function public.ui_configuration_publish_draft(p_draft_id uuid, p_reason text, p_confirmation text)
returns uuid
language plpgsql
security definer
set search_path = auth, public, pg_temp
as $$
declare
  v_actor uuid := private.access_assert_system_admin();
  v_reason text := trim(coalesce(p_reason,''));
  v_draft public.ui_configuration_versions%rowtype;
  v_previous public.ui_configuration_versions%rowtype;
begin
  if trim(coalesce(p_confirmation,'')) <> 'PUBLISH UI CONFIGURATION' then raise exception 'Type PUBLISH UI CONFIGURATION before publishing.'; end if;
  if char_length(v_reason) not between 3 and 500 then raise exception 'Provide a publication reason between 3 and 500 characters.'; end if;
  select * into v_draft from public.ui_configuration_versions where id=p_draft_id and state='DRAFT' and audience_key='RESIDENT_GLOBAL' for update;
  if not found then raise exception 'Only a current global configuration draft can be published.'; end if;
  perform private.ui_configuration_validate_payload(v_draft.configuration);
  select * into v_previous from public.ui_configuration_versions where audience_key='RESIDENT_GLOBAL' and state='PUBLISHED' order by published_at desc limit 1 for update;
  if found then
    update public.ui_configuration_versions set state='SUPERSEDED',superseded_at=now() where id=v_previous.id;
    insert into public.ui_configuration_events(configuration_version_id,actor_id,event_type,reason,metadata) values(v_previous.id,v_actor,'SUPERSEDED',v_reason,jsonb_build_object('superseded_by',v_draft.id));
  end if;
  update public.ui_configuration_versions set state='PUBLISHED',published_by=v_actor,published_reason=v_reason,published_at=now() where id=v_draft.id;
  insert into public.ui_configuration_events(configuration_version_id,actor_id,event_type,reason,metadata) values(v_draft.id,v_actor,'PUBLISHED',v_reason,jsonb_build_object('audience_key','RESIDENT_GLOBAL','schema_version',v_draft.schema_version));
  perform private.ops_log_audit(v_actor,'UI_CONFIGURATION_PUBLISHED','UI_CONFIGURATION',v_draft.id,'SUCCESS',jsonb_build_object('schema_version',v_draft.schema_version));
  return v_draft.id;
end;
$$;

create or replace function public.ui_configuration_revert(p_version_id uuid, p_reason text, p_confirmation text)
returns uuid
language plpgsql
security definer
set search_path = auth, public, pg_temp
as $$
declare
  v_actor uuid := private.access_assert_system_admin();
  v_reason text := trim(coalesce(p_reason,''));
  v_source public.ui_configuration_versions%rowtype;
  v_configuration jsonb;
  v_new_id uuid;
begin
  if trim(coalesce(p_confirmation,'')) <> 'PUBLISH UI CONFIGURATION' then raise exception 'Type PUBLISH UI CONFIGURATION before restoring.'; end if;
  if char_length(v_reason) not between 3 and 500 then raise exception 'Provide a restore reason between 3 and 500 characters.'; end if;
  select * into v_source from public.ui_configuration_versions where id=p_version_id and audience_key='RESIDENT_GLOBAL' and state in ('PUBLISHED','SUPERSEDED') for share;
  if not found then raise exception 'Select a published configuration from version history.'; end if;
  v_configuration := private.ui_configuration_upgrade_to_v2(v_source.configuration);
  perform private.ui_configuration_validate_payload(v_configuration);
  insert into public.ui_configuration_versions(audience_key,state,schema_version,configuration,created_by,created_reason,based_on_version_id)
  values('RESIDENT_GLOBAL','DRAFT',2,v_configuration,v_actor,v_reason,v_source.id) returning id into v_new_id;
  perform public.ui_configuration_publish_draft(v_new_id,v_reason,'PUBLISH UI CONFIGURATION');
  insert into public.ui_configuration_events(configuration_version_id,actor_id,event_type,reason,metadata) values(v_new_id,v_actor,'REVERTED',v_reason,jsonb_build_object('reverted_from',v_source.id));
  perform private.ops_log_audit(v_actor,'UI_CONFIGURATION_REVERTED','UI_CONFIGURATION',v_new_id,'SUCCESS',jsonb_build_object('reverted_from',v_source.id));
  return v_new_id;
end;
$$;

create or replace function public.ui_configuration_effective_global()
returns table(version_id uuid, schema_version smallint, configuration jsonb, published_at timestamptz)
language sql
security definer
set search_path = public, pg_temp
as $$
  select id,schema_version,private.ui_configuration_upgrade_to_v2(configuration),published_at
  from public.ui_configuration_versions
  where audience_key='RESIDENT_GLOBAL' and state='PUBLISHED'
  order by published_at desc
  limit 1;
$$;

create or replace function public.ui_configuration_admin_history(p_limit integer default 40)
returns table(version_id uuid,state text,created_reason text,created_at timestamptz,published_at timestamptz)
language plpgsql
security definer
set search_path = auth, public, pg_temp
as $$
begin
  perform private.access_assert_system_admin();
  return query select id,v.state,v.created_reason,v.created_at,v.published_at from public.ui_configuration_versions v where audience_key='RESIDENT_GLOBAL' order by v.created_at desc limit greatest(1,least(coalesce(p_limit,40),100));
end;
$$;

create or replace function public.ui_configuration_asset_begin(p_mime_type text,p_byte_size integer,p_width integer,p_height integer)
returns table(asset_id uuid,object_path text)
language plpgsql
security definer
set search_path = auth, public, pg_temp
as $$
declare
  v_actor uuid := private.access_assert_system_admin();
  v_id uuid := gen_random_uuid();
  v_path text := v_id::text || '/source';
begin
  if p_mime_type not in ('image/jpeg','image/png','image/webp') then raise exception 'Use JPEG, PNG or WebP.'; end if;
  if p_byte_size not between 1 and 8388608 then raise exception 'UI image must be 8 MiB or smaller.'; end if;
  if p_width not between 1 and 8192 or p_height not between 1 and 8192 then raise exception 'UI image dimensions are not supported.'; end if;
  insert into public.ui_configuration_assets(id,state,object_path,mime_type,byte_size,width,height,created_by)
  values(v_id,'STAGED',v_path,p_mime_type,p_byte_size,p_width,p_height,v_actor);
  return query select v_id,v_path;
end;
$$;

create or replace function public.ui_configuration_asset_finalize(p_asset_id uuid)
returns uuid
language plpgsql
security definer
set search_path = auth, public, storage, pg_temp
as $$
declare
  v_actor uuid := private.access_assert_system_admin();
  v_asset public.ui_configuration_assets%rowtype;
begin
  select * into v_asset from public.ui_configuration_assets where id=p_asset_id and state='STAGED' and created_by=v_actor for update;
  if not found then raise exception 'The staged UI asset is unavailable.'; end if;
  if not exists(select 1 from storage.objects where bucket_id='rtc-ui-assets' and name=v_asset.object_path) then raise exception 'Upload the UI image before finalizing it.'; end if;
  update public.ui_configuration_assets set state='FINALIZED',finalized_at=now() where id=p_asset_id;
  return p_asset_id;
end;
$$;

create or replace function public.ui_configuration_asset_path(p_asset_id uuid)
returns table(object_path text)
language plpgsql
security definer
set search_path = auth, public, pg_temp
as $$
begin
  if auth.uid() is null then raise exception 'A signed-in account is required.'; end if;
  return query
  select a.object_path from public.ui_configuration_assets a
  where a.id=p_asset_id and a.state='FINALIZED'
    and (
      (private.has_role('SYSTEM_ADMIN'::public.app_role) and coalesce(auth.jwt()->>'aal','aal1')='aal2')
      or exists(
        select 1 from public.ui_configuration_versions v
        where v.state='PUBLISHED' and v.audience_key='RESIDENT_GLOBAL'
          and (private.ui_configuration_upgrade_to_v2(v.configuration)->'home'->'imageWidget'->>'assetId')=a.id::text
      )
    );
end;
$$;

create or replace function public.ui_configuration_asset_delete(p_asset_id uuid)
returns uuid
language plpgsql
security definer
set search_path = auth, public, pg_temp
as $$
declare
  v_actor uuid := private.access_assert_system_admin();
begin
  if exists(select 1 from public.ui_configuration_versions where state='PUBLISHED' and (private.ui_configuration_upgrade_to_v2(configuration)->'home'->'imageWidget'->>'assetId')=p_asset_id::text) then raise exception 'A published Home image cannot be deleted. Publish a replacement or remove it first.'; end if;
  update public.ui_configuration_assets set state='DELETED',deleted_at=now() where id=p_asset_id and created_by=v_actor and state in ('STAGED','FINALIZED');
  if not found then raise exception 'The UI asset is unavailable for deletion.'; end if;
  return p_asset_id;
end;
$$;

drop policy if exists rtc_ui_assets_insert on storage.objects;
create policy rtc_ui_assets_insert on storage.objects for insert to authenticated
with check (
  bucket_id='rtc-ui-assets'
  and coalesce(auth.jwt()->>'aal','aal1')='aal2'
  and private.has_role('SYSTEM_ADMIN'::public.app_role)
  and exists(select 1 from public.ui_configuration_assets a where a.object_path=name and a.created_by=auth.uid() and a.state='STAGED')
);

drop policy if exists rtc_ui_assets_select on storage.objects;
create policy rtc_ui_assets_select on storage.objects for select to authenticated
using (
  bucket_id='rtc-ui-assets'
  and exists(
    select 1 from public.ui_configuration_assets a
    where a.object_path=name and a.state='FINALIZED'
      and (
        (private.has_role('SYSTEM_ADMIN'::public.app_role) and coalesce(auth.jwt()->>'aal','aal1')='aal2')
        or exists(select 1 from public.ui_configuration_versions v where v.state='PUBLISHED' and (private.ui_configuration_upgrade_to_v2(v.configuration)->'home'->'imageWidget'->>'assetId')=a.id::text)
      )
  )
);

revoke all on table public.ui_configuration_assets from public,anon,authenticated,service_role;
revoke all on function public.ui_configuration_effective_global() from public,anon,authenticated,service_role;
grant execute on function public.ui_configuration_effective_global() to anon,authenticated;

revoke all on function public.ui_configuration_admin_history(integer) from public,anon,authenticated,service_role;
revoke all on function public.ui_configuration_asset_begin(text,integer,integer,integer) from public,anon,authenticated,service_role;
revoke all on function public.ui_configuration_asset_finalize(uuid) from public,anon,authenticated,service_role;
revoke all on function public.ui_configuration_asset_path(uuid) from public,anon,authenticated,service_role;
revoke all on function public.ui_configuration_asset_delete(uuid) from public,anon,authenticated,service_role;
grant execute on function public.ui_configuration_admin_history(integer) to authenticated;
grant execute on function public.ui_configuration_asset_begin(text,integer,integer,integer) to authenticated;
grant execute on function public.ui_configuration_asset_finalize(uuid) to authenticated;
grant execute on function public.ui_configuration_asset_path(uuid) to authenticated;
grant execute on function public.ui_configuration_asset_delete(uuid) to authenticated;

revoke all on function public.ui_configuration_create_draft(jsonb,text,uuid) from public,anon,authenticated,service_role;
revoke all on function public.ui_configuration_publish_draft(uuid,text,text) from public,anon,authenticated,service_role;
revoke all on function public.ui_configuration_revert(uuid,text,text) from public,anon,authenticated,service_role;
grant execute on function public.ui_configuration_create_draft(jsonb,text,uuid) to authenticated;
grant execute on function public.ui_configuration_publish_draft(uuid,text,text) to authenticated;
grant execute on function public.ui_configuration_revert(uuid,text,text) to authenticated;
