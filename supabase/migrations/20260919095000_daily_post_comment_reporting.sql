begin;

create table if not exists public.daily_post_comment_reports (
  id uuid primary key default gen_random_uuid(),
  comment_id uuid not null references public.daily_post_comments(id) on delete cascade,
  reporter_id uuid not null references auth.users(id) on delete cascade,
  reason_code text not null check (reason_code in ('HARASSMENT','HATE','SPAM','THREAT','OTHER')),
  detail text not null default '' check (char_length(detail) <= 1000),
  status text not null default 'OPEN' check (status in ('OPEN','REVIEWED','DISMISSED','ACTIONED')),
  created_at timestamptz not null default now(),
  reviewed_at timestamptz,
  reviewed_by uuid references auth.users(id),
  unique (comment_id, reporter_id)
);

create index if not exists daily_post_comment_reports_queue_idx
  on public.daily_post_comment_reports(status, created_at, id);
create index if not exists daily_post_comment_reports_comment_idx
  on public.daily_post_comment_reports(comment_id, created_at desc, id desc);

alter table public.daily_post_comment_reports enable row level security;
revoke all on table public.daily_post_comment_reports from public, anon, authenticated;
drop policy if exists daily_post_comment_reports_rpc_only on public.daily_post_comment_reports;
create policy daily_post_comment_reports_rpc_only
  on public.daily_post_comment_reports
  as restrictive
  for all
  to anon, authenticated
  using (false)
  with check (false);

create or replace function public.daily_post_comment_report_v1(
  p_comment_id uuid,
  p_reason_code text,
  p_detail text default ''
)
returns uuid
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  report_id uuid;
  recent_count integer;
begin
  if auth.uid() is null then
    raise exception 'Authentication required';
  end if;
  p_reason_code := upper(btrim(coalesce(p_reason_code, '')));
  p_detail := btrim(coalesce(p_detail, ''));
  if p_reason_code not in ('HARASSMENT','HATE','SPAM','THREAT','OTHER') then
    raise exception 'Invalid report reason';
  end if;
  if char_length(p_detail) > 1000 then
    raise exception 'Report detail is too long';
  end if;
  if not exists (
    select 1 from public.daily_post_comments c
    join public.daily_posts p on p.id = c.post_id
    where c.id = p_comment_id and c.state = 'VISIBLE' and p.state = 'PUBLISHED'
  ) then
    raise exception 'Comment unavailable';
  end if;
  select count(*) into recent_count
    from public.daily_post_comment_reports
   where reporter_id = auth.uid()
     and created_at > now() - interval '1 hour';
  if recent_count >= 10 then
    raise exception 'Report rate limit reached';
  end if;
  insert into public.daily_post_comment_reports(comment_id, reporter_id, reason_code, detail)
  values (p_comment_id, auth.uid(), p_reason_code, p_detail)
  on conflict (comment_id, reporter_id) do update
    set reason_code = excluded.reason_code,
        detail = excluded.detail,
        status = 'OPEN',
        reviewed_at = null,
        reviewed_by = null
  returning id into report_id;
  insert into public.daily_post_audit_events(actor_id, post_id, event_type, result, metadata)
  select auth.uid(), c.post_id, 'COMMENT_REPORTED', 'ALLOWED',
         jsonb_build_object('commentId', c.id, 'reportId', report_id, 'reasonCode', p_reason_code)
    from public.daily_post_comments c
   where c.id = p_comment_id;
  return report_id;
end;
$$;

revoke all on function public.daily_post_comment_report_v1(uuid, text, text) from public, anon;
grant execute on function public.daily_post_comment_report_v1(uuid, text, text) to authenticated;

commit;
