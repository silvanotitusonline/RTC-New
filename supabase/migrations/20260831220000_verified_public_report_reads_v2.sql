-- Verified-only resident Public Reports reads.
--
-- This migration is additive. Older clients continue to use the v1 dashboard/page
-- semantics while the resident information architecture uses these v2 reads.

begin;

create or replace function public.civic_report_dashboard_v2()
returns table(
  verified_reports bigint,
  active_reports bigint,
  resolved_reports bigint,
  unresolved_reports bigint
)
language sql
stable
security invoker
set search_path=''
as $$
  select
    count(*)::bigint,
    count(*) filter (where report.status = 'IN_PROGRESS')::bigint,
    count(*) filter (where report.status in ('COMPLETED', 'CLOSED'))::bigint,
    count(*) filter (where report.status not in ('COMPLETED', 'CLOSED'))::bigint
  from public.civic_reports_public report
  where report.verified_at is not null;
$$;

create or replace function public.civic_report_page_v2(
  p_scope text,
  p_urgency text,
  p_category_slug text,
  p_sort text,
  p_cursor_created_at timestamptz,
  p_cursor_id uuid,
  p_limit integer,
  p_evaluated_at timestamptz
)
returns setof public.civic_reports_public
language plpgsql
stable
security invoker
set search_path=''
as $$
declare
  v_scope text := upper(trim(coalesce(p_scope, 'VERIFIED')));
  v_urgency text := upper(trim(coalesce(p_urgency, '')));
  v_category text := lower(trim(coalesce(p_category_slug, '')));
  v_sort text := upper(trim(coalesce(p_sort, 'LATEST')));
  v_limit integer := greatest(1, least(coalesce(p_limit, 20), 50));
begin
  if v_scope not in ('VERIFIED', 'ACTIVE', 'RESOLVED', 'UNRESOLVED') then
    raise exception 'CIVIC_REPORT_INVALID_SCOPE' using errcode = '22023';
  end if;
  if v_urgency not in ('', 'LOW', 'NORMAL', 'HIGH', 'CRITICAL') then
    v_urgency := '';
  end if;
  if v_sort not in ('LATEST', 'OLDEST', 'URGENCY', 'MOST_DISCUSSED', 'MOST_SUPPORTED', 'HOT') then
    v_sort := 'LATEST';
  end if;

  return query
  select report.*
  from public.civic_reports_public report
  where report.verified_at is not null
    and (
      v_scope = 'VERIFIED'
      or (v_scope = 'ACTIVE' and report.status = 'IN_PROGRESS')
      or (v_scope = 'RESOLVED' and report.status in ('COMPLETED', 'CLOSED'))
      or (v_scope = 'UNRESOLVED' and report.status not in ('COMPLETED', 'CLOSED'))
    )
    and (v_urgency = '' or report.urgency = v_urgency)
    and (v_category = '' or report.category_slug = v_category)
    and (
      p_cursor_created_at is null or p_cursor_id is null
      or (v_sort = 'OLDEST' and (report.created_at, report.id) > (p_cursor_created_at, p_cursor_id))
      or (v_sort <> 'OLDEST' and (report.created_at, report.id) < (p_cursor_created_at, p_cursor_id))
    )
  order by
    case when v_sort = 'OLDEST' then report.created_at end asc,
    case when v_sort = 'URGENCY' then case report.urgency when 'CRITICAL' then 4 when 'HIGH' then 3 when 'NORMAL' then 2 else 1 end end desc,
    case when v_sort = 'MOST_DISCUSSED' then report.comment_count end desc,
    case when v_sort = 'MOST_SUPPORTED' then report.thumbs_up_count end desc,
    case when v_sort = 'HOT' then report.hot_score end desc,
    case when v_sort <> 'OLDEST' then report.created_at end desc,
    report.id desc
  limit v_limit;
end;
$$;

revoke all on function public.civic_report_dashboard_v2() from public, anon, authenticated;
revoke all on function public.civic_report_page_v2(text,text,text,text,timestamptz,uuid,integer,timestamptz) from public, anon, authenticated;

grant execute on function public.civic_report_dashboard_v2() to anon, authenticated;
grant execute on function public.civic_report_page_v2(text,text,text,text,timestamptz,uuid,integer,timestamptz) to anon, authenticated;

comment on function public.civic_report_dashboard_v2() is
  'Verified-only resident counts over the safe, time-bounded Public Reports projection.';
comment on function public.civic_report_page_v2(text,text,text,text,timestamptz,uuid,integer,timestamptz) is
  'Verified-only resident Public Reports page with bounded cursor pagination and typed scopes.';

commit;
