-- Correct two production defects without weakening role or row-level access checks.

create or replace function public.admin_privacy_analytics_dashboard(p_period text default '30D')
returns table(metric text, label text, metric_value bigint)
language plpgsql
security definer
set search_path = auth, public, pg_temp
as $$
declare
  v_start date;
begin
  perform private.access_assert_system_admin();
  v_start := private.admin_privacy_assert_period(p_period);
  perform private.admin_privacy_write_audit(
    'PRIVACY_ANALYTICS_DASHBOARD_VIEWED',
    null,
    null,
    'VIEWED',
    jsonb_build_object('period', upper(trim(p_period)))
  );

  return query
  select 'VERIFIED_SIGNUPS'::text, 'Verified sign-ups'::text,
    (select count(*)::bigint from auth.users where email_confirmed_at is not null and created_at::date >= v_start)
  union all
  select 'ACTIVE_ACCOUNT_SIGNALS', 'Active account signals'::text,
    coalesce((select sum(t.metric_value) from public.admin_privacy_analytics_daily_totals t where t.metric = 'ACTIVE_ACCOUNT_SIGNALS' and t.activity_date >= v_start), 0)::bigint
  union all
  select 'COMMUNITY_POSTS', 'Community posts'::text,
    (select count(*)::bigint from public.community_posts where created_at::date >= v_start)
  union all
  select 'COMMUNITY_COMMENTS', 'Community comments'::text,
    (select count(*)::bigint from public.community_comments where created_at::date >= v_start)
  union all
  select 'SUPPORT_REQUESTS', 'Support requests'::text,
    (select count(*)::bigint from public.community_cases where created_at::date >= v_start)
  union all
  select 'DIRECTORY_SEARCHES', 'Directory searches'::text,
    coalesce((select sum(t.metric_value) from public.admin_privacy_analytics_daily_totals t where t.metric = 'DIRECTORY_SEARCHES' and t.activity_date >= v_start), 0)::bigint
  union all
  select 'NOTIFICATIONS_SENT', 'Notifications created'::text,
    (select count(*)::bigint from public.notification_events where created_at::date >= v_start)
  union all
  select 'NOTIFICATIONS_DELIVERED', 'Notifications delivered'::text,
    (select count(*)::bigint from public.notification_events where delivered_at is not null and delivered_at::date >= v_start)
  union all
  select 'NOTIFICATIONS_READ', 'Notifications read'::text,
    (select count(*)::bigint from public.notification_events where read_at is not null and read_at::date >= v_start)
  union all
  select 'ROLE_CHANGES', 'Role changes and approvals'::text,
    (select count(*)::bigint from public.access_role_audit_events where occurred_at::date >= v_start);
end;
$$;

-- The helper returns only the current caller's staff-status boolean. Existing RLS policies
-- call it to constrain a user to their own staff preference record; authenticated execution
-- is necessary for PostgreSQL policy evaluation and does not grant table access by itself.
grant execute on function private.is_any_staff() to authenticated;
