-- Reconstructs the database portion of the Community report/moderation workflow in the
-- isolated non-production project. It intentionally does not deploy the Firebase-dependent
-- `report-community-post` Edge Function, configure a service account, or enable a scheduler.
-- The database creates only durable in-app moderation notifications and operational work items.

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

create table if not exists public.moderation_items (
  id uuid primary key default gen_random_uuid(),
  reporter_id uuid not null references auth.users(id) on delete restrict,
  subject_type text not null check (subject_type in ('POST', 'COMMENT', 'MEDIA')),
  subject_id uuid,
  reason text not null check (char_length(reason) between 3 and 3000),
  state text not null default 'OPEN' check (state in ('OPEN', 'UNDER_REVIEW', 'RESOLVED', 'DISMISSED')),
  resolution_note text,
  resolved_by uuid references auth.users(id) on delete set null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  reason_code text not null default 'OTHER' check (reason_code in ('SPAM', 'HARMFUL_CONTENT', 'PRIVACY_CONCERN', 'OTHER'))
);

create table if not exists public.community_moderation_actions (
  id uuid primary key default gen_random_uuid(),
  subject_type text not null check (subject_type in ('POST', 'COMMENT', 'MEDIA')),
  subject_id uuid not null,
  action_type text not null check (action_type in ('HIDE', 'RESTORE', 'LOCK', 'UNLOCK', 'PIN', 'UNPIN', 'REMOVE_MEDIA')),
  reason text not null check (char_length(trim(reason)) between 3 and 1000),
  pinned_until timestamptz,
  actor_id uuid not null references public.community_profiles(id) on delete restrict,
  created_at timestamptz not null default now()
);

create table if not exists public.moderation_appeals (
  id uuid primary key default gen_random_uuid(),
  subject_type text not null check (subject_type in ('POST', 'COMMENT')),
  subject_id uuid not null,
  appellant_id uuid not null references auth.users(id) on delete restrict,
  reason text not null check (char_length(trim(reason)) between 3 and 2000),
  state text not null default 'OPEN' check (state in ('OPEN', 'UPHELD', 'RESTORED', 'DISMISSED')),
  decided_by uuid references auth.users(id) on delete set null,
  decision_reason text,
  created_at timestamptz not null default now(),
  decided_at timestamptz
);

create index if not exists moderation_items_subject_state_idx on public.moderation_items(subject_type, subject_id, state, created_at desc);
create index if not exists moderation_items_queue_idx on public.moderation_items(state, reason_code, created_at);
create index if not exists moderation_appeals_open_idx on public.moderation_appeals(state, created_at);
create index if not exists operational_work_items_queue_idx on public.operational_work_items(state, target_role, priority, due_at);

alter table public.operational_work_items enable row level security;
alter table public.moderation_items enable row level security;
alter table public.community_moderation_actions enable row level security;
alter table public.moderation_appeals enable row level security;

revoke all on public.operational_work_items, public.moderation_items,
  public.community_moderation_actions, public.moderation_appeals from anon, authenticated;

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
  if private.has_role('SYSTEM_ADMIN'::public.app_role) then
    return private.access_assert_system_admin();
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

create or replace function private.touch_moderation_item_updated_at()
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

create or replace function private.enqueue_community_report_notification()
returns trigger
language plpgsql
security definer
set search_path = public, pg_temp
as $$
begin
  if new.subject_type = 'POST' and new.state = 'OPEN' then
    insert into public.notification_events(recipient_id, notification_type, title, body, payload)
    select
      ur.user_id,
      'MODERATION_REPORT',
      'New Community report',
      'A Community post needs moderation review.',
      jsonb_build_object(
        'report_id', new.id,
        'post_id', new.subject_id,
        'reason_code', new.reason_code,
        'route', 'moderation_report'
      )
    from public.user_roles ur
    where ur.role in ('MODERATOR', 'SYSTEM_ADMIN');
  end if;
  return new;
end;
$$;

create or replace function private.release1_moderation_report_after_insert()
returns trigger
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_open_reporter_count integer;
begin
  if new.subject_type <> 'POST' or new.state <> 'OPEN' then
    return new;
  end if;

  select count(distinct reporter_id)::integer into v_open_reporter_count
  from public.moderation_items
  where subject_type = 'POST'
    and subject_id = new.subject_id
    and state in ('OPEN', 'UNDER_REVIEW');

  update public.community_posts
     set report_count = v_open_reporter_count,
         state = case
           when v_open_reporter_count >= 3 and state in ('PUBLISHED', 'LOCKED') then 'LIMITED_PENDING_REVIEW'
           else state
         end,
         updated_at = now()
   where id = new.subject_id;

  perform private.ops_upsert_work_item(
    'MODERATION_REPORT', new.id, 'MODERATOR'::public.app_role,
    'Review Community report',
    'Reported Community content requires a safeguarded moderator decision (' || new.reason_code || ').',
    case when new.reason_code in ('HARMFUL_CONTENT', 'PRIVACY_CONCERN') then 'HIGH' else 'NORMAL' end,
    new.created_at + interval '24 hours'
  );

  if v_open_reporter_count >= 3 then
    insert into public.audit_events(actor_id, event_type, entity_type, entity_id, result, metadata, source)
    values (
      new.reporter_id,
      'COMMUNITY_AUTO_LIMITED_PENDING_REVIEW',
      'community_post',
      new.subject_id,
      'SUCCESS',
      jsonb_build_object('independent_reporter_count', v_open_reporter_count),
      'moderation-threshold'
    );
  end if;
  return new;
end;
$$;

drop trigger if exists moderation_items_set_updated_at on public.moderation_items;
create trigger moderation_items_set_updated_at
before update on public.moderation_items
for each row execute function private.touch_moderation_item_updated_at();

drop trigger if exists moderation_items_community_report_notify_after_insert on public.moderation_items;
create trigger moderation_items_community_report_notify_after_insert
after insert on public.moderation_items
for each row execute function private.enqueue_community_report_notification();

drop trigger if exists release1_moderation_report_after_insert on public.moderation_items;
create trigger release1_moderation_report_after_insert
after insert on public.moderation_items
for each row execute function private.release1_moderation_report_after_insert();

create or replace function public.report_community_post(
  p_post_id uuid,
  p_reason_code text,
  p_detail text default ''
)
returns uuid
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_reporter_id uuid := auth.uid();
  v_post_author uuid;
  v_report_id uuid;
  v_code text := upper(trim(coalesce(p_reason_code, ''));
  v_detail text := trim(coalesce(p_detail, ''));
begin
  if v_reporter_id is null then
    raise exception 'A signed-in account is required to report Community content.';
  end if;
  if v_code not in ('SPAM', 'HARMFUL_CONTENT', 'PRIVACY_CONCERN', 'OTHER') then
    raise exception 'Choose a valid report reason.';
  end if;
  if char_length(v_detail) > 2400 then
    raise exception 'Report details must contain at most 2400 characters.';
  end if;

  select author_id into v_post_author
    from public.community_posts
   where id = p_post_id
     and state in ('PUBLISHED', 'LOCKED')
     and deleted_at is null;
  if not found then
    raise exception 'This Community post is not available for reporting.';
  end if;
  if v_post_author = v_reporter_id then
    raise exception 'You cannot report your own Community post.';
  end if;

  select id into v_report_id
    from public.moderation_items
   where reporter_id = v_reporter_id
     and subject_type = 'POST'
     and subject_id = p_post_id
     and state in ('OPEN', 'UNDER_REVIEW')
   order by created_at desc
   limit 1;
  if v_report_id is not null then
    return v_report_id;
  end if;

  insert into public.moderation_items(reporter_id, subject_type, subject_id, reason, reason_code, state)
  values (
    v_reporter_id,
    'POST',
    p_post_id,
    case when v_detail = '' then 'No additional detail supplied.' else v_detail end,
    v_code,
    'OPEN'
  )
  returning id into v_report_id;
  return v_report_id;
end;
$$;

create or replace function public.moderate_community_item(
  p_subject_type text,
  p_subject_id uuid,
  p_action_type text,
  p_reason text,
  p_pinned_until timestamptz default null
)
returns void
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_actor uuid := private.ops_assert_staff();
begin
  if not private.community_staff() then
    raise exception 'MODERATOR_REQUIRED';
  end if;
  if char_length(trim(coalesce(p_reason, ''))) not between 3 and 1000 then
    raise exception 'MODERATION_REASON_REQUIRED';
  end if;

  if p_subject_type = 'POST' then
    if p_action_type = 'HIDE' then
      update public.community_posts set state = 'HIDDEN_BY_MODERATION', updated_at = now() where id = p_subject_id;
    elsif p_action_type = 'RESTORE' then
      update public.community_posts set state = 'PUBLISHED', deleted_at = null, updated_at = now() where id = p_subject_id;
    elsif p_action_type = 'LOCK' then
      update public.community_posts set is_locked = true, state = 'LOCKED', updated_at = now() where id = p_subject_id;
    elsif p_action_type = 'UNLOCK' then
      update public.community_posts set is_locked = false, state = 'PUBLISHED', updated_at = now() where id = p_subject_id;
    elsif p_action_type = 'PIN' then
      update public.community_posts set pinned_until = p_pinned_until, updated_at = now() where id = p_subject_id and p_pinned_until > now();
    elsif p_action_type = 'UNPIN' then
      update public.community_posts set pinned_until = null, updated_at = now() where id = p_subject_id;
    else
      raise exception 'INVALID_MODERATION_ACTION';
    end if;
  elsif p_subject_type = 'COMMENT' then
    if p_action_type = 'HIDE' then
      update public.community_comments set state = 'HIDDEN_BY_MODERATION', updated_at = now() where id = p_subject_id;
    elsif p_action_type = 'RESTORE' then
      update public.community_comments set state = 'PUBLISHED', deleted_at = null, updated_at = now() where id = p_subject_id;
    else
      raise exception 'INVALID_MODERATION_ACTION';
    end if;
  else
    raise exception 'INVALID_SUBJECT';
  end if;

  if not found then
    raise exception 'SUBJECT_UNAVAILABLE';
  end if;

  perform private.ensure_community_profile_for_account(v_actor);
  insert into public.community_moderation_actions(subject_type, subject_id, action_type, reason, pinned_until, actor_id)
  values (p_subject_type, p_subject_id, p_action_type, trim(p_reason), p_pinned_until, v_actor);
  perform private.ops_log_audit(
    v_actor,
    'COMMUNITY_MODERATION_' || p_action_type,
    lower(p_subject_type),
    p_subject_id,
    'SUCCESS',
    jsonb_build_object('reason', trim(p_reason)),
    'community-rpc'
  );
end;
$$;

create or replace function public.moderation_list_queue(
  p_state text default 'OPEN',
  p_limit integer default 100
)
returns table(
  report_id uuid,
  post_id uuid,
  reason_code text,
  report_detail text,
  report_state text,
  reported_at timestamptz,
  post_body text,
  post_state text,
  report_count integer,
  author_display_name text,
  is_auto_limited boolean
)
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_state text := upper(trim(coalesce(p_state, 'OPEN'));
begin
  perform private.ops_assert_staff();
  if not private.is_moderation_authority() then
    raise exception 'Moderator access is required.';
  end if;
  if v_state not in ('OPEN', 'UNDER_REVIEW', 'ALL') then
    raise exception 'Select a valid moderation queue state.';
  end if;

  return query
  select
    m.id,
    m.subject_id,
    m.reason_code,
    m.reason,
    m.state,
    m.created_at,
    p.body,
    p.state,
    p.report_count,
    coalesce(cp.display_name, profile.display_name, 'Community member'),
    p.state = 'LIMITED_PENDING_REVIEW'
  from public.moderation_items m
  join public.community_posts p on p.id = m.subject_id
  left join public.community_profiles cp on cp.id = p.author_id
  left join public.profiles profile on profile.id = p.author_id
  where m.subject_type = 'POST'
    and (v_state = 'ALL' or m.state = v_state)
  order by
    case when p.state = 'LIMITED_PENDING_REVIEW' then 0 else 1 end,
    case m.reason_code when 'HARMFUL_CONTENT' then 0 when 'PRIVACY_CONCERN' then 1 else 2 end,
    m.created_at
  limit greatest(1, least(coalesce(p_limit, 100), 200));
end;
$$;

create or replace function public.moderation_decide_report(
  p_report_id uuid,
  p_decision text,
  p_reason text
)
returns void
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_actor uuid := private.ops_assert_staff();
  v_report public.moderation_items%rowtype;
  v_decision text := upper(trim(coalesce(p_decision, ''));
  v_reason text := trim(coalesce(p_reason, ''));
  v_active_count integer;
begin
  if not private.is_moderation_authority() then
    raise exception 'Moderator access is required.';
  end if;
  if v_decision not in ('DISMISS', 'HIDE', 'LOCK', 'REMOVE') then
    raise exception 'Select a valid moderation decision.';
  end if;
  if char_length(v_reason) not between 3 and 1000 then
    raise exception 'Provide a decision reason between 3 and 1000 characters.';
  end if;

  select * into v_report
  from public.moderation_items
  where id = p_report_id
  for update;
  if not found or v_report.subject_type <> 'POST' or v_report.state not in ('OPEN', 'UNDER_REVIEW') then
    raise exception 'This moderation report is not available.';
  end if;

  if v_decision = 'DISMISS' then
    update public.moderation_items
       set state = 'DISMISSED', resolution_note = v_reason, resolved_by = v_actor
     where id = v_report.id;

    select count(distinct reporter_id)::integer into v_active_count
    from public.moderation_items
    where subject_type = 'POST'
      and subject_id = v_report.subject_id
      and state in ('OPEN', 'UNDER_REVIEW');

    update public.community_posts
       set report_count = v_active_count,
           state = case when v_active_count < 3 and state = 'LIMITED_PENDING_REVIEW' then 'PUBLISHED' else state end,
           updated_at = now()
     where id = v_report.subject_id;
  else
    perform public.moderate_community_item(
      'POST',
      v_report.subject_id,
      case when v_decision = 'LOCK' then 'LOCK' else 'HIDE' end,
      v_reason,
      null
    );
    update public.moderation_items
       set state = 'RESOLVED', resolution_note = v_reason, resolved_by = v_actor
     where subject_type = 'POST'
       and subject_id = v_report.subject_id
       and state in ('OPEN', 'UNDER_REVIEW');
    update public.community_posts set report_count = 0, updated_at = now() where id = v_report.subject_id;
  end if;

  update public.operational_work_items
     set state = 'RESOLVED', resolved_at = now(), updated_at = now()
   where source_type = 'MODERATION_REPORT'
     and source_id = v_report.id
     and state not in ('RESOLVED', 'CANCELLED');

  perform private.ops_log_audit(
    v_actor,
    'MODERATION_REPORT_' || v_decision,
    'MODERATION_ITEM',
    v_report.id,
    'SUCCESS',
    jsonb_build_object('post_id', v_report.subject_id, 'reason', v_reason)
  );
end;
$$;

create or replace function public.moderation_list_appeals(p_limit integer default 100)
returns table(
  appeal_id uuid,
  subject_type text,
  subject_id uuid,
  reason text,
  state text,
  created_at timestamptz,
  post_body text
)
language plpgsql
security definer
set search_path = public, pg_temp
as $$
begin
  perform private.ops_assert_staff();
  if not private.is_moderation_authority() then
    raise exception 'Moderator access is required.';
  end if;

  return query
  select
    a.id,
    a.subject_type,
    a.subject_id,
    a.reason,
    a.state,
    a.created_at,
    case when a.subject_type = 'POST' then p.body else null end
  from public.moderation_appeals a
  left join public.community_posts p on p.id = a.subject_id and a.subject_type = 'POST'
  where a.state = 'OPEN'
  order by a.created_at
  limit greatest(1, least(coalesce(p_limit, 100), 200));
end;
$$;

create or replace function public.moderation_decide_appeal(
  p_appeal_id uuid,
  p_decision text,
  p_reason text
)
returns void
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_actor uuid := private.ops_assert_staff();
  v_appeal public.moderation_appeals%rowtype;
  v_decision text := upper(trim(coalesce(p_decision, ''));
  v_reason text := trim(coalesce(p_reason, ''));
begin
  if not private.is_moderation_authority() then
    raise exception 'Moderator access is required.';
  end if;
  if v_decision not in ('UPHOLD', 'RESTORE', 'DISMISS') or char_length(v_reason) not between 3 and 2000 then
    raise exception 'Provide a valid appeal decision and reason.';
  end if;

  select * into v_appeal
  from public.moderation_appeals
  where id = p_appeal_id
  for update;
  if not found or v_appeal.state <> 'OPEN' then
    raise exception 'This appeal is not available.';
  end if;

  if v_decision = 'RESTORE' then
    perform public.moderate_community_item(v_appeal.subject_type, v_appeal.subject_id, 'RESTORE', v_reason, null);
  end if;
  update public.moderation_appeals
     set state = case
         when v_decision = 'RESTORE' then 'RESTORED'
         when v_decision = 'UPHOLD' then 'UPHELD'
         else 'DISMISSED'
       end,
       decided_by = v_actor,
       decision_reason = v_reason,
       decided_at = now()
   where id = v_appeal.id;

  perform private.ops_log_audit(
    v_actor,
    'MODERATION_APPEAL_' || v_decision,
    'MODERATION_APPEAL',
    v_appeal.id,
    'SUCCESS',
    jsonb_build_object('reason', v_reason)
  );
end;
$$;

revoke all on function private.ops_assert_staff() from public, anon, authenticated;
revoke all on function private.ops_log_audit(uuid, text, text, uuid, text, jsonb, text) from public, anon, authenticated;
revoke all on function private.ops_upsert_work_item(text, uuid, public.app_role, text, text, text, timestamptz) from public, anon, authenticated;
revoke all on function private.touch_moderation_item_updated_at() from public, anon, authenticated;
revoke all on function private.enqueue_community_report_notification() from public, anon, authenticated;
revoke all on function private.release1_moderation_report_after_insert() from public, anon, authenticated;

revoke all on function public.report_community_post(uuid, text, text) from public, anon;
grant execute on function public.report_community_post(uuid, text, text) to authenticated;
revoke all on function public.moderate_community_item(text, uuid, text, text, timestamptz) from public, anon;
grant execute on function public.moderate_community_item(text, uuid, text, text, timestamptz) to authenticated;
revoke all on function public.moderation_list_queue(text, integer) from public, anon;
grant execute on function public.moderation_list_queue(text, integer) to authenticated;
revoke all on function public.moderation_decide_report(uuid, text, text) from public, anon;
grant execute on function public.moderation_decide_report(uuid, text, text) to authenticated;
revoke all on function public.moderation_list_appeals(integer) from public, anon;
grant execute on function public.moderation_list_appeals(integer) to authenticated;
revoke all on function public.moderation_decide_appeal(uuid, text, text) from public, anon;
grant execute on function public.moderation_decide_appeal(uuid, text, text) to authenticated;
