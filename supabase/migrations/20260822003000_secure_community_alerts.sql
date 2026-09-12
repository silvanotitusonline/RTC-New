-- Secure staff-to-community alert workflow.
-- Firebase remains a server-side delivery channel; alert content, recipients, preference enforcement,
-- correction history and audit records remain owned by Supabase.

begin;

create table if not exists public.community_alert_preferences (
  user_id uuid primary key references auth.users(id) on delete cascade,
  ordinary_alerts_enabled boolean not null default true,
  updated_at timestamptz not null default now()
);

create table if not exists public.community_alerts (
  id uuid primary key default gen_random_uuid(),
  category text not null check (category in ('COMMUNITY_UPDATE', 'SERVICE_DISRUPTION', 'SAFETY_EMERGENCY', 'EVENT', 'OPPORTUNITY')),
  message_state text not null default 'ORIGINAL' check (message_state in ('ORIGINAL', 'CORRECTION', 'RETRACTION')),
  original_alert_id uuid references public.community_alerts(id),
  title text not null check (char_length(trim(title)) between 3 and 120),
  summary text not null check (char_length(trim(summary)) between 3 and 600),
  body text not null check (char_length(trim(body)) between 3 and 12000),
  linked_notice_id uuid references public.official_notices(id),
  status text not null check (status in ('SCHEDULED', 'PUBLISHED', 'ARCHIVED', 'CANCELLED')),
  scheduled_at timestamptz,
  published_at timestamptz,
  expires_at timestamptz not null,
  created_by uuid not null references auth.users(id),
  correction_reason text,
  emergency_reason text,
  dispatch_state text not null default 'NOT_READY' check (dispatch_state in ('NOT_READY', 'PENDING', 'DISPATCHING', 'COMPLETE', 'PARTIAL', 'NO_DEVICES', 'FAILED')),
  dispatch_attempted_at timestamptz,
  dispatched_at timestamptz,
  intended_recipient_count integer not null default 0 check (intended_recipient_count >= 0),
  eligible_device_count integer not null default 0 check (eligible_device_count >= 0),
  fcm_accepted_count integer not null default 0 check (fcm_accepted_count >= 0),
  fcm_failed_count integer not null default 0 check (fcm_failed_count >= 0),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint community_alert_schedule_check check (
    (status = 'SCHEDULED' and scheduled_at is not null and published_at is null)
    or (status = 'PUBLISHED' and published_at is not null)
    or status in ('ARCHIVED', 'CANCELLED')
  ),
  constraint community_alert_expiry_check check (
    expires_at > coalesce(scheduled_at, published_at, created_at)
  ),
  constraint community_alert_correction_check check (
    (message_state = 'ORIGINAL' and original_alert_id is null)
    or (message_state in ('CORRECTION', 'RETRACTION') and original_alert_id is not null and correction_reason is not null)
  )
);

create index if not exists community_alerts_inbox_index
  on public.community_alerts(status, published_at desc)
  where status = 'PUBLISHED';
create index if not exists community_alerts_creator_index
  on public.community_alerts(created_by, created_at desc);
create index if not exists community_alerts_due_index
  on public.community_alerts(scheduled_at)
  where status = 'SCHEDULED';

alter table public.community_alert_preferences enable row level security;
alter table public.community_alerts enable row level security;
revoke all on public.community_alert_preferences, public.community_alerts from public, anon;
grant select, insert, update on public.community_alert_preferences to authenticated;
grant select on public.community_alerts to authenticated;

create policy community_alert_preferences_select_own on public.community_alert_preferences
  for select to authenticated using (user_id = (select auth.uid()));
create policy community_alert_preferences_insert_own on public.community_alert_preferences
  for insert to authenticated with check (user_id = (select auth.uid()));
create policy community_alert_preferences_update_own on public.community_alert_preferences
  for update to authenticated using (user_id = (select auth.uid())) with check (user_id = (select auth.uid()));

-- Residents may see only alerts which produced one of their own inbox events. Publishers can see
-- their own records; System Administrators can conduct the required cross-publisher audit.
create policy community_alerts_select_recipient_creator_or_admin on public.community_alerts
  for select to authenticated using (
    created_by = (select auth.uid())
    or private.has_role('SYSTEM_ADMIN'::public.app_role)
    or exists (
      select 1 from public.notification_events e
      where e.notification_type = 'COMMUNITY_ALERT'
        and e.recipient_id = (select auth.uid())
        and e.payload ->> 'alert_id' = community_alerts.id::text
    )
  );

-- Current, signed-in recipient inbox. It remains secure through the recipient-only notification
-- event policy and contains the canonical alert record rather than trusting FCM payload content.
create or replace view public.community_alert_inbox
with (security_invoker = true)
as
select
  e.id as notification_id,
  e.read_at,
  e.created_at as inbox_created_at,
  a.id,
  a.category,
  a.message_state,
  a.original_alert_id,
  a.title,
  a.summary,
  a.body,
  a.linked_notice_id,
  a.published_at,
  a.expires_at,
  a.created_at,
  a.updated_at
from public.notification_events e
join public.community_alerts a
  on e.notification_type = 'COMMUNITY_ALERT'
 and e.payload ->> 'alert_id' = a.id::text
where e.recipient_id = (select auth.uid())
  and a.status in ('PUBLISHED', 'ARCHIVED')
  and a.published_at is not null
  and a.published_at <= now()
  and a.expires_at >= now() - interval '90 days';

-- Publisher/admin-only operational summary. No resident names, device tokens or named read data
-- are exposed; counts are aggregate only.
create or replace view public.community_alert_dashboard_feed
with (security_invoker = true)
as
select
  a.id,
  a.category,
  a.message_state,
  a.original_alert_id,
  a.title,
  a.summary,
  a.status,
  a.scheduled_at,
  a.published_at,
  a.expires_at,
  a.created_by,
  a.dispatch_state,
  a.intended_recipient_count,
  a.eligible_device_count,
  a.fcm_accepted_count,
  a.fcm_failed_count,
  count(e.id) filter (where e.read_at is not null)::integer as read_count,
  a.created_at,
  a.updated_at
from public.community_alerts a
left join public.notification_events e
  on e.notification_type = 'COMMUNITY_ALERT'
 and e.payload ->> 'alert_id' = a.id::text
where a.created_by = (select auth.uid())
   or private.has_role('SYSTEM_ADMIN'::public.app_role)
group by a.id;

-- Produces one immutable alert record, recipient inbox events and a server-audited delivery job.
-- Only Content Editors and System Administrators can call it.
create or replace function public.create_community_alert(
  p_category text,
  p_title text,
  p_summary text,
  p_body text,
  p_scheduled_at timestamptz default null,
  p_expires_at timestamptz default null,
  p_linked_notice_id uuid default null,
  p_message_state text default 'ORIGINAL',
  p_original_alert_id uuid default null,
  p_correction_reason text default null,
  p_emergency_confirmation text default null,
  p_emergency_reason text default null
)
returns uuid
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_actor uuid := auth.uid();
  v_category text := upper(trim(coalesce(p_category, '')));
  v_state text := upper(trim(coalesce(p_message_state, 'ORIGINAL')));
  v_title text := trim(coalesce(p_title, ''));
  v_summary text := trim(coalesce(p_summary, ''));
  v_body text := trim(coalesce(p_body, ''));
  v_reason text := trim(coalesce(p_correction_reason, ''));
  v_emergency_reason text := trim(coalesce(p_emergency_reason, ''));
  v_now timestamptz := now();
  v_scheduled timestamptz := p_scheduled_at;
  v_expires timestamptz := p_expires_at;
  v_status text;
  v_alert_id uuid;
  v_recipient_count integer := 0;
  v_limit_count integer := 0;
begin
  if v_actor is null then
    raise exception 'A signed-in account is required.';
  end if;
  if not private.has_any_role(array['CONTENT_EDITOR', 'SYSTEM_ADMIN']::public.app_role[]) then
    raise exception 'This account is not authorised to publish community alerts.';
  end if;
  if v_category not in ('COMMUNITY_UPDATE', 'SERVICE_DISRUPTION', 'SAFETY_EMERGENCY', 'EVENT', 'OPPORTUNITY') then
    raise exception 'Choose a valid alert category.';
  end if;
  if char_length(v_title) not between 3 and 120 or char_length(v_summary) not between 3 and 600 or char_length(v_body) not between 3 and 12000 then
    raise exception 'Alert content is outside the permitted length.';
  end if;
  if v_state not in ('ORIGINAL', 'CORRECTION', 'RETRACTION') then
    raise exception 'Choose a valid alert state.';
  end if;
  if (v_state = 'ORIGINAL' and p_original_alert_id is not null)
     or (v_state in ('CORRECTION', 'RETRACTION') and (p_original_alert_id is null or char_length(v_reason) < 3)) then
    raise exception 'Corrections and retractions require an original alert and reason.';
  end if;
  if p_original_alert_id is not null and not exists (
    select 1 from public.community_alerts a
     where a.id = p_original_alert_id
       and (a.created_by = v_actor or private.has_role('SYSTEM_ADMIN'::public.app_role))
  ) then
    raise exception 'The original alert is unavailable for correction.';
  end if;
  if p_linked_notice_id is not null and not exists (
    select 1 from public.official_notices n where n.id = p_linked_notice_id and n.status = 'published'
  ) then
    raise exception 'Only a published official notice may be linked.';
  end if;
  if v_scheduled is not null and v_scheduled > v_now + interval '1 minute' then
    v_status := 'SCHEDULED';
  else
    v_scheduled := null;
    v_status := 'PUBLISHED';
  end if;
  if v_expires is null then
    v_expires := case v_category
      when 'COMMUNITY_UPDATE' then coalesce(v_scheduled, v_now) + interval '30 days'
      when 'SERVICE_DISRUPTION' then coalesce(v_scheduled, v_now) + interval '7 days'
      when 'SAFETY_EMERGENCY' then coalesce(v_scheduled, v_now) + interval '72 hours'
      when 'EVENT' then coalesce(v_scheduled, v_now) + interval '30 days'
      when 'OPPORTUNITY' then coalesce(v_scheduled, v_now) + interval '30 days'
    end;
  end if;
  if v_expires <= coalesce(v_scheduled, v_now) then
    raise exception 'The alert expiry must be after its publication time.';
  end if;
  if v_category = 'SAFETY_EMERGENCY' then
    if trim(coalesce(p_emergency_confirmation, '')) <> 'SEND SAFETY ALERT' or char_length(v_emergency_reason) < 10 then
      raise exception 'Safety alerts require typed confirmation and an operational reason.';
    end if;
    if v_expires > coalesce(v_scheduled, v_now) + interval '72 hours' then
      raise exception 'Safety alerts may have a maximum initial expiry of 72 hours.';
    end if;
    select count(*) into v_limit_count
      from public.community_alerts
     where created_by = v_actor and category = 'SAFETY_EMERGENCY' and created_at >= v_now - interval '1 hour';
    if v_limit_count >= 2 then raise exception 'Safety alert rate limit reached.'; end if;
  else
    select count(*) into v_limit_count
      from public.community_alerts
     where created_by = v_actor and category <> 'SAFETY_EMERGENCY' and created_at >= v_now - interval '24 hours';
    if v_limit_count >= 5 then raise exception 'Ordinary alert rate limit reached.'; end if;
  end if;

  insert into public.community_alerts(
    category, message_state, original_alert_id, title, summary, body, linked_notice_id,
    status, scheduled_at, published_at, expires_at, created_by, correction_reason,
    emergency_reason, dispatch_state
  ) values (
    v_category, v_state, p_original_alert_id, v_title, v_summary, v_body, p_linked_notice_id,
    v_status, v_scheduled, case when v_status = 'PUBLISHED' then v_now else null end, v_expires, v_actor,
    nullif(v_reason, ''), nullif(v_emergency_reason, ''), case when v_status = 'PUBLISHED' then 'PENDING' else 'NOT_READY' end
  ) returning id into v_alert_id;

  if v_status = 'PUBLISHED' then
    insert into public.notification_events(recipient_id, notification_type, title, body, payload)
    select p.id, 'COMMUNITY_ALERT', v_title, v_summary,
      jsonb_build_object('alert_id', v_alert_id, 'route', 'community_alert', 'category', v_category)
    from public.profiles p
    left join public.community_alert_preferences pref on pref.user_id = p.id
    where v_category = 'SAFETY_EMERGENCY' or coalesce(pref.ordinary_alerts_enabled, true);
    get diagnostics v_recipient_count = row_count;
    update public.community_alerts set intended_recipient_count = v_recipient_count, updated_at = now() where id = v_alert_id;
  end if;

  insert into public.audit_events(actor_id, event_type, entity_type, entity_id, result, metadata, source)
  values (
    v_actor,
    case when v_category = 'SAFETY_EMERGENCY' then 'COMMUNITY_SAFETY_ALERT_CREATED' else 'COMMUNITY_ALERT_CREATED' end,
    'COMMUNITY_ALERT', v_alert_id,
    'SUCCESS',
    jsonb_build_object('category', v_category, 'message_state', v_state, 'status', v_status, 'expires_at', v_expires),
    'supabase-rpc'
  );
  return v_alert_id;
end;
$$;

-- Server-only scheduled publisher: the cron invocation has no client role and this function is
-- not executable by anonymous or authenticated users.
create or replace function public.publish_due_community_alerts()
returns integer
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_count integer := 0;
  v_alert record;
  v_recipients integer;
begin
  for v_alert in
    update public.community_alerts
       set status = 'PUBLISHED', published_at = now(), dispatch_state = 'PENDING', updated_at = now()
     where status = 'SCHEDULED' and scheduled_at <= now() and expires_at > now()
     returning id, category, title, summary
  loop
    insert into public.notification_events(recipient_id, notification_type, title, body, payload)
    select p.id, 'COMMUNITY_ALERT', v_alert.title, v_alert.summary,
      jsonb_build_object('alert_id', v_alert.id, 'route', 'community_alert', 'category', v_alert.category)
    from public.profiles p
    left join public.community_alert_preferences pref on pref.user_id = p.id
    where v_alert.category = 'SAFETY_EMERGENCY' or coalesce(pref.ordinary_alerts_enabled, true);
    get diagnostics v_recipients = row_count;
    update public.community_alerts set intended_recipient_count = v_recipients where id = v_alert.id;
    v_count := v_count + 1;
  end loop;
  return v_count;
end;
$$;
revoke all on function public.publish_due_community_alerts() from public, anon, authenticated;

create or replace function public.archive_expired_community_alerts()
returns integer
language plpgsql
security definer
set search_path = ''
as $$
declare v_count integer;
begin
  update public.community_alerts
     set status = 'ARCHIVED', updated_at = now()
   where status = 'PUBLISHED' and expires_at < now();
  get diagnostics v_count = row_count;
  return v_count;
end;
$$;
revoke all on function public.archive_expired_community_alerts() from public, anon, authenticated;

revoke all on function public.create_community_alert(text, text, text, text, timestamptz, timestamptz, uuid, text, uuid, text, text, text) from public, anon;
grant execute on function public.create_community_alert(text, text, text, text, timestamptz, timestamptz, uuid, text, uuid, text, text, text) to authenticated;

-- Existing `content_media` rules are extended to the signed-in alert target only. Alert media is
-- not publicly readable; RLS requires an authenticated recipient/publisher/admin to view it.
alter table public.content_media drop constraint if exists content_media_target_type_check;
alter table public.content_media add constraint content_media_target_type_check
  check (target_type in ('PROJECT', 'CENTRE', 'OPPORTUNITY', 'NOTICE', 'ALERT'));

create or replace function private.can_view_published_content_target(p_target_type text, p_target_id uuid)
returns boolean
language plpgsql
stable
security definer
set search_path = ''
as $$
begin
  if p_target_type = 'PROJECT' then
    return exists (select 1 from public.directory_projects where id = p_target_id and visibility = 'PUBLISHED');
  elsif p_target_type = 'CENTRE' then
    return exists (select 1 from public.directory_centres where id = p_target_id and visibility = 'PUBLISHED');
  elsif p_target_type = 'OPPORTUNITY' then
    return exists (select 1 from public.directory_opportunities where id = p_target_id and visibility = 'PUBLISHED');
  elsif p_target_type = 'NOTICE' then
    return exists (select 1 from public.official_notices where id = p_target_id and status = 'published');
  elsif p_target_type = 'ALERT' then
    return auth.uid() is not null and exists (
      select 1 from public.community_alerts a
      where a.id = p_target_id and a.status in ('PUBLISHED', 'ARCHIVED')
    );
  end if;
  return false;
end;
$$;

create or replace function private.can_view_staff_content_media_path(p_path text)
returns boolean
language plpgsql
stable
security definer
set search_path = ''
as $$
declare parts text[]; target_uuid uuid;
begin
  parts := storage.foldername(p_path);
  if array_length(parts, 1) < 4
     or parts[2] <> 'staff'
     or parts[3] not in ('PROJECT', 'CENTRE', 'OPPORTUNITY', 'NOTICE', 'ALERT')
     or parts[4] !~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$' then return false; end if;
  target_uuid := parts[4]::uuid;
  return exists (
    select 1 from public.content_media m
     where m.storage_path = p_path and m.deleted_at is null
       and m.target_type = parts[3] and m.target_id = target_uuid
       and private.can_view_published_content_target(m.target_type, m.target_id)
  );
end;
$$;

-- Source used by the alert dispatcher. It exposes only a pending alert with aggregate-friendly
-- fields and is service-role-only; no resident endpoint can call it.
create or replace function public.claim_pending_community_alert_dispatch()
returns table(
  id uuid, category text, title text, summary text, expires_at timestamptz,
  intended_recipient_count integer
)
language plpgsql
security definer
set search_path = ''
as $$
declare v_alert public.community_alerts%rowtype;
begin
  select * into v_alert from public.community_alerts
   where status = 'PUBLISHED' and dispatch_state = 'PENDING' and expires_at > now()
   order by published_at asc
   for update skip locked limit 1;
  if not found then return; end if;
  update public.community_alerts set dispatch_state = 'DISPATCHING', dispatch_attempted_at = now(), updated_at = now() where community_alerts.id = v_alert.id;
  return query select v_alert.id, v_alert.category, v_alert.title, v_alert.summary, v_alert.expires_at, v_alert.intended_recipient_count;
end;
$$;
revoke all on function public.claim_pending_community_alert_dispatch() from public, anon, authenticated;
grant execute on function public.claim_pending_community_alert_dispatch() to service_role;

-- Cron prepares due records, archives expired records, and invokes the Vault-backed Edge Function.
-- The project URL and public API key are not secrets but are stored in Vault to follow the documented
-- scheduled-function invocation pattern. No Firebase private key is placed in this migration.
do $$
begin
  if not exists (select 1 from vault.secrets where name = 'rtc_alert_scheduler_project_url') then
    perform vault.create_secret('https://pbzzfzfgwzwdstvnwzqu.supabase.co', 'rtc_alert_scheduler_project_url', 'RTC scheduled alert dispatcher URL');
  end if;
  if not exists (select 1 from vault.secrets where name = 'rtc_alert_scheduler_publishable_key') then
    perform vault.create_secret('sb_publishable_VKE5NH3l629RriDAlQVomg_NluPswET', 'rtc_alert_scheduler_publishable_key', 'RTC scheduled alert dispatcher public key');
  end if;
end;
$$;

select cron.unschedule(jobid) from cron.job where jobname = 'rtc-community-alert-schedule';
select cron.schedule(
  'rtc-community-alert-schedule',
  '* * * * *',
  $cron$
    select public.publish_due_community_alerts();
    select public.archive_expired_community_alerts();
    select net.http_post(
      url := (select decrypted_secret from vault.decrypted_secrets where name = 'rtc_alert_scheduler_project_url') || '/functions/v1/dispatch-community-alerts',
      headers := jsonb_build_object(
        'Content-Type', 'application/json',
        'apikey', (select decrypted_secret from vault.decrypted_secrets where name = 'rtc_alert_scheduler_publishable_key')
      ),
      body := jsonb_build_object('source', 'supabase-cron')
    );
  $cron$
);

commit;
