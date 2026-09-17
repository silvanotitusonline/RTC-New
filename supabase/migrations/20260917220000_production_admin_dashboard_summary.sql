begin;

-- The Android administration dashboard must read one authoritative, bounded
-- projection. It must not select entire privileged tables or depend on legacy
-- reports/business_submissions/support_requests tables.
create or replace function public.admin_get_moderation_dashboard_summary_v1()
returns table(
  reports_count bigint,
  notices_count bigint,
  events_count bigint,
  work_queue_count bigint,
  total_pending_tasks bigint
)
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_actor uuid := private.ops_assert_staff();
  v_reports bigint;
  v_notices bigint;
  v_events bigint;
  v_work_queue bigint;
begin
  -- Keep every source query bounded by aggregation and only expose counts.
  select count(*) into v_reports
    from public.civic_reports r
   where upper(coalesce(r.status, '')) not in ('RESOLVED', 'CLOSED', 'ARCHIVED', 'CANCELLED');

  select count(*) into v_notices
    from public.official_notices n
   where lower(coalesce(n.status, '')) in ('draft', 'submitted', 'under_review', 'scheduled');

  select count(*) into v_events
    from public.community_events e
   where upper(coalesce(e.state::text, '')) in ('DRAFT', 'SUBMITTED', 'UNDER_REVIEW');

  select count(*) into v_work_queue
    from public.operational_work_items w
   where upper(coalesce(w.state::text, '')) not in ('COMPLETED', 'CANCELLED', 'RESOLVED', 'CLOSED');

  return query select
    v_reports,
    v_notices,
    v_events,
    v_work_queue,
    v_reports + v_notices + v_events + v_work_queue;
end;
$$;

revoke all on function public.admin_get_moderation_dashboard_summary_v1() from public, anon;
grant execute on function public.admin_get_moderation_dashboard_summary_v1() to authenticated;

comment on function public.admin_get_moderation_dashboard_summary_v1() is
  'Staff-only bounded summary for the administration dashboard. Counts current production frontend queues and never returns row data.';

commit;
