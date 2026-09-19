begin;

-- Operational metrics are intentionally append-only and RPC-only.
create table if not exists public.daily_post_rpc_metrics (
  id uuid primary key default gen_random_uuid(),
  rpc_name text not null check (rpc_name in ('comments_page','comment_create','comment_delete','comment_moderate','comment_report')),
  duration_ms integer not null check (duration_ms between 0 and 120000),
  outcome text not null check (outcome in ('SUCCESS','ERROR','TIMEOUT','AUTHORIZATION_FAILURE')),
  error_code text check (error_code is null or char_length(error_code) <= 120),
  actor_id uuid references auth.users(id),
  created_at timestamptz not null default now()
);
create index if not exists daily_post_rpc_metrics_name_time_idx on public.daily_post_rpc_metrics(rpc_name, created_at desc, id desc);
create index if not exists daily_post_rpc_metrics_outcome_time_idx on public.daily_post_rpc_metrics(outcome, created_at desc, id desc);

create table if not exists public.daily_post_count_drift_events (
  id uuid primary key default gen_random_uuid(),
  post_id uuid not null references public.daily_posts(id) on delete cascade,
  stored_count integer not null,
  visible_count integer not null,
  drift integer not null,
  observed_at timestamptz not null default now(),
  resolved_at timestamptz
);
create unique index if not exists daily_post_count_drift_open_idx
  on public.daily_post_count_drift_events(post_id) where resolved_at is null;
create index if not exists daily_post_count_drift_time_idx
  on public.daily_post_count_drift_events(observed_at desc, id desc);

create index if not exists daily_post_comment_reports_reporter_idx
  on public.daily_post_comment_reports(reporter_id, created_at desc, id desc);
create index if not exists daily_post_comment_reports_reviewed_by_idx
  on public.daily_post_comment_reports(reviewed_by, reviewed_at desc, id desc);
create index if not exists daily_post_audit_events_actor_idx
  on public.daily_post_audit_events(actor_id, created_at desc, id desc);

alter table public.daily_post_rpc_metrics enable row level security;
revoke all on table public.daily_post_rpc_metrics from public, anon, authenticated;
drop policy if exists daily_post_rpc_metrics_rpc_only on public.daily_post_rpc_metrics;
create policy daily_post_rpc_metrics_rpc_only on public.daily_post_rpc_metrics as restrictive for all to anon, authenticated using (false) with check (false);

alter table public.daily_post_count_drift_events enable row level security;
revoke all on table public.daily_post_count_drift_events from public, anon, authenticated;
drop policy if exists daily_post_count_drift_events_rpc_only on public.daily_post_count_drift_events;
create policy daily_post_count_drift_events_rpc_only on public.daily_post_count_drift_events as restrictive for all to anon, authenticated using (false) with check (false);

create or replace function public.daily_post_rpc_metric_record_v1(
  p_rpc_name text,
  p_duration_ms integer,
  p_outcome text,
  p_error_code text default null
)
returns uuid
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare metric_id uuid;
begin
  if auth.uid() is null then raise exception 'Authentication required'; end if;
  if p_rpc_name not in ('comments_page','comment_create','comment_delete','comment_moderate','comment_report') then raise exception 'Invalid RPC name'; end if;
  if p_outcome not in ('SUCCESS','ERROR','TIMEOUT','AUTHORIZATION_FAILURE') then raise exception 'Invalid outcome'; end if;
  insert into public.daily_post_rpc_metrics(rpc_name,duration_ms,outcome,error_code,actor_id)
  values (p_rpc_name, greatest(0, least(coalesce(p_duration_ms,0),120000)), p_outcome, nullif(left(btrim(coalesce(p_error_code,'')),120),''), auth.uid())
  returning id into metric_id;
  return metric_id;
end;
$$;
revoke all on function public.daily_post_rpc_metric_record_v1(text,integer,text,text) from public, anon;
grant execute on function public.daily_post_rpc_metric_record_v1(text,integer,text,text) to authenticated;

create or replace function public.daily_post_rpc_health_v1(
  p_window interval default interval '24 hours'
)
returns table(rpc_name text, call_count bigint, p50_ms numeric, p95_ms numeric, p99_ms numeric, timeout_rate numeric, authorization_failure_rate numeric, error_rate numeric)
language plpgsql
security definer
set search_path = public, pg_temp
as $$
begin
  if not public.daily_post_can_moderate() then raise exception 'Moderator access required'; end if;
  return query
  select m.rpc_name,
         count(*)::bigint,
         percentile_cont(0.50) within group (order by m.duration_ms)::numeric,
         percentile_cont(0.95) within group (order by m.duration_ms)::numeric,
         percentile_cont(0.99) within group (order by m.duration_ms)::numeric,
         round((count(*) filter (where m.outcome='TIMEOUT')::numeric / nullif(count(*),0)),4),
         round((count(*) filter (where m.outcome='AUTHORIZATION_FAILURE')::numeric / nullif(count(*),0)),4),
         round((count(*) filter (where m.outcome='ERROR')::numeric / nullif(count(*),0)),4)
    from public.daily_post_rpc_metrics m
   where m.created_at >= now() - least(coalesce(p_window, interval '24 hours'), interval '30 days')
   group by m.rpc_name order by m.rpc_name;
end;
$$;
revoke all on function public.daily_post_rpc_health_v1(interval) from public, anon, authenticated;
grant execute on function public.daily_post_rpc_health_v1(interval) to authenticated;

create or replace function public.daily_post_report_queue_v1(
  p_before_created_at timestamptz default null,
  p_before_id uuid default null,
  p_limit integer default 50
)
returns table(id uuid, comment_id uuid, post_id uuid, reporter_id uuid, reason_code text, detail text, status text, created_at timestamptz, reviewed_at timestamptz, reviewed_by uuid, comment_body text)
language plpgsql
security definer
set search_path = public, pg_temp
as $$
begin
  if not public.daily_post_can_moderate() then raise exception 'Moderator access required'; end if;
  return query
  select r.id,r.comment_id,c.post_id,r.reporter_id,r.reason_code,r.detail,r.status,r.created_at,r.reviewed_at,r.reviewed_by,c.body
    from public.daily_post_comment_reports r join public.daily_post_comments c on c.id=r.comment_id
   where r.status='OPEN'
     and (p_before_created_at is null or (r.created_at,r.id)<(p_before_created_at,coalesce(p_before_id,'ffffffff-ffff-ffff-ffff-ffffffffffff'::uuid)))
   order by r.created_at desc,r.id desc limit least(greatest(coalesce(p_limit,50),1),100);
end;
$$;
revoke all on function public.daily_post_report_queue_v1(timestamptz,uuid,integer) from public, anon;
grant execute on function public.daily_post_report_queue_v1(timestamptz,uuid,integer) to authenticated;

create or replace function public.daily_post_report_transition_v1(p_report_id uuid,p_status text,p_note text default '')
returns void language plpgsql security definer set search_path=public,pg_temp as $$
declare r public.daily_post_comment_reports;
begin
  if not public.daily_post_can_moderate() then raise exception 'Moderator access required'; end if;
  if p_status not in ('REVIEWED','DISMISSED','ACTIONED') then raise exception 'Invalid report status'; end if;
  update public.daily_post_comment_reports set status=p_status, reviewed_at=now(), reviewed_by=auth.uid() where id=p_report_id returning * into r;
  if not found then raise exception 'Report unavailable'; end if;
  insert into public.daily_post_audit_events(actor_id,post_id,event_type,result,metadata)
  values(auth.uid(),(select post_id from public.daily_post_comments where id=r.comment_id),'COMMENT_REPORT_STATUS_CHANGED','ALLOWED',jsonb_build_object('reportId',r.id,'status',p_status,'note',left(btrim(coalesce(p_note,'')),1000)));
end;
$$;
revoke all on function public.daily_post_report_transition_v1(uuid,text,text) from public, anon;
grant execute on function public.daily_post_report_transition_v1(uuid,text,text) to authenticated;

create or replace function public.daily_post_report_summary_v1()
returns table(comment_id uuid, post_id uuid, report_count bigint, open_count bigint, latest_created_at timestamptz, reasons text[])
language plpgsql security definer set search_path=public,pg_temp as $$
begin
  if not public.daily_post_can_moderate() then raise exception 'Moderator access required'; end if;
  return query select r.comment_id,c.post_id,count(*)::bigint,count(*) filter(where r.status='OPEN')::bigint,max(r.created_at),array_agg(distinct r.reason_code)
    from public.daily_post_comment_reports r join public.daily_post_comments c on c.id=r.comment_id group by r.comment_id,c.post_id order by max(r.created_at) desc;
end;
$$;
revoke all on function public.daily_post_report_summary_v1() from public, anon;
grant execute on function public.daily_post_report_summary_v1() to authenticated;

create or replace function public.daily_post_count_drift_check_v1()
returns integer language plpgsql security definer set search_path=public,pg_temp as $$
declare drift_count integer;
begin
  with current_drift as (
    select p.id, p.comment_count stored_count, count(c.id)::integer visible_count
      from public.daily_posts p left join public.daily_post_comments c on c.post_id=p.id and c.state='VISIBLE'
     group by p.id,p.comment_count having p.comment_count <> count(c.id)::integer
  ), inserted as (
    insert into public.daily_post_count_drift_events(post_id,stored_count,visible_count,drift)
    select id,stored_count,visible_count,visible_count-stored_count from current_drift
    on conflict (post_id) where resolved_at is null do update set stored_count=excluded.stored_count,visible_count=excluded.visible_count,drift=excluded.drift,observed_at=now()
    returning 1
  ) select count(*)::integer into drift_count from inserted;
  update public.daily_post_count_drift_events e set resolved_at=now() where e.resolved_at is null and not exists(select 1 from public.daily_posts p where p.id=e.post_id and p.comment_count <> (select count(*) from public.daily_post_comments c where c.post_id=p.id and c.state='VISIBLE'));
  return coalesce(drift_count,0);
end;
$$;
revoke all on function public.daily_post_count_drift_check_v1() from public, anon, authenticated;
grant execute on function public.daily_post_count_drift_check_v1() to service_role;

do $$
begin
  if exists(select 1 from pg_extension where extname='pg_cron') then
    perform cron.schedule('daily-post-comment-count-drift-check','*/5 * * * *','select public.daily_post_count_drift_check_v1()');
  end if;
exception when unique_violation then null;
end;
$$;

commit;
