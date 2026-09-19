begin;

-- Forward-only, idempotent deployment boundary for the Daily Post count-drift
-- detector. Existing production installs already have these objects through
-- daily_post_operational_health; this migration safely reconciles older
-- non-production or restored environments without destructive statements.
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
  on public.daily_post_count_drift_events(post_id)
  where resolved_at is null;

create index if not exists daily_post_count_drift_time_idx
  on public.daily_post_count_drift_events(observed_at desc, id desc);

alter table public.daily_post_count_drift_events enable row level security;
revoke all on table public.daily_post_count_drift_events from public, anon, authenticated;
drop policy if exists daily_post_count_drift_events_rpc_only on public.daily_post_count_drift_events;
create policy daily_post_count_drift_events_rpc_only
  on public.daily_post_count_drift_events
  as restrictive
  for all
  to anon, authenticated
  using (false)
  with check (false);

create or replace function public.daily_post_count_drift_check_v1()
returns integer
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  drift_count integer;
begin
  with current_drift as (
    select p.id,
           p.comment_count as stored_count,
           count(c.id)::integer as visible_count
      from public.daily_posts p
      left join public.daily_post_comments c
        on c.post_id = p.id
       and c.state = 'VISIBLE'
     group by p.id, p.comment_count
    having p.comment_count <> count(c.id)::integer
  ), inserted as (
    insert into public.daily_post_count_drift_events(
      post_id, stored_count, visible_count, drift
    )
    select id, stored_count, visible_count, visible_count - stored_count
      from current_drift
    on conflict (post_id) where resolved_at is null
      do update set
        stored_count = excluded.stored_count,
        visible_count = excluded.visible_count,
        drift = excluded.drift,
        observed_at = now()
    returning 1
  )
  select count(*)::integer into drift_count from inserted;

  update public.daily_post_count_drift_events e
     set resolved_at = now()
   where e.resolved_at is null
     and not exists (
       select 1
         from public.daily_posts p
        where p.id = e.post_id
          and p.comment_count <> (
            select count(*)
              from public.daily_post_comments c
             where c.post_id = p.id
               and c.state = 'VISIBLE'
          )
     );

  return coalesce(drift_count, 0);
end;
$$;

revoke all on function public.daily_post_count_drift_check_v1()
  from public, anon, authenticated;
grant execute on function public.daily_post_count_drift_check_v1()
  to service_role;

do $$
begin
  if exists (select 1 from pg_extension where extname = 'pg_cron') then
    perform cron.schedule(
      'daily-post-comment-count-drift-check',
      '*/5 * * * *',
      'select public.daily_post_count_drift_check_v1()'
    );
  end if;
exception when unique_violation then
  null;
end;
$$;

commit;
