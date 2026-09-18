-- RTC Community Production boundary check
-- Project ref: pbzzfzfgwzwdstvnwzqu
-- Read-only: paste this file into the Supabase SQL editor. Do not paste the
-- Python checker or shell commands into the SQL editor.

with expected(function_name, identity_arguments) as (
  values
    ('civic_report_categories_v1', ''),
    ('civic_report_comment_page_v1', 'p_report_id uuid, p_cursor_created_at timestamp with time zone, p_cursor_id uuid, p_limit integer'),
    ('civic_report_dashboard_v1', ''),
    ('civic_report_dashboard_v2', ''),
    ('civic_report_evidence_public_v1', 'p_report_id uuid'),
    ('civic_report_get_v1', 'p_report_id uuid'),
    ('civic_report_page_v1', 'p_status text, p_urgency text, p_category_slug text, p_verified boolean, p_sort text, p_cursor_created_at timestamp with time zone, p_cursor_id uuid, p_limit integer, p_evaluated_at timestamp with time zone'),
    ('civic_report_page_v2', 'p_scope text, p_urgency text, p_category_slug text, p_sort text, p_cursor_created_at timestamp with time zone, p_cursor_id uuid, p_limit integer, p_evaluated_at timestamp with time zone'),
    ('civic_report_timeline_v1', 'p_report_id uuid'),
    ('ui_configuration_effective_global', '')
), observed as (
  select
    p.proname as function_name,
    pg_get_function_identity_arguments(p.oid) as identity_arguments,
    coalesce(array_to_string(p.proconfig, ';'), '') as config
  from pg_proc p
  join pg_namespace n on n.oid = p.pronamespace
  where n.nspname = 'public'
    and p.prosecdef
    and has_function_privilege('anon', p.oid, 'EXECUTE')
)
select
  'anonymous_security_definer_allowlist' as check_name,
  case
    when exists (select 1 from observed o left join expected e using (function_name, identity_arguments) where e.function_name is null)
      then 'FAIL'
    when exists (select 1 from expected e left join observed o using (function_name, identity_arguments) where o.function_name is null)
      then 'FAIL'
    when exists (select 1 from observed where config not like '%search_path=%')
      then 'FAIL'
    else 'PASS'
  end as status,
  (select count(*) from observed) as observed_count,
  (select count(*) from expected) as expected_count;

with rpc_only(table_name) as (
  values
    ('access_role_audit_events'), ('access_role_change_requests'), ('access_session_controls'),
    ('admin_privacy_analytics_activity_events'), ('admin_privacy_analytics_daily_totals'),
    ('api_rate_limits'), ('app_error_logs'), ('conversation_members'), ('conversations'),
    ('messages'), ('moderation_appeals'), ('notifications'), ('official_notice_lifecycle_events'),
    ('operational_control_events'), ('operational_controls'), ('operational_incidents'),
    ('operational_service_events'), ('operational_work_assignment_events'), ('operational_work_items'),
    ('post_likes'), ('service_centre_booking_events'), ('service_centre_booking_messages'),
    ('service_centre_bookings'), ('service_centre_provider_profiles'), ('trending_hashtags')
), observed as (
  select
    r.table_name,
    c.oid,
    c.relrowsecurity,
    has_table_privilege('anon', c.oid, 'SELECT')
      or has_table_privilege('anon', c.oid, 'INSERT')
      or has_table_privilege('anon', c.oid, 'UPDATE')
      or has_table_privilege('anon', c.oid, 'DELETE') as anon_direct_access,
    has_table_privilege('authenticated', c.oid, 'SELECT')
      or has_table_privilege('authenticated', c.oid, 'INSERT')
      or has_table_privilege('authenticated', c.oid, 'UPDATE')
      or has_table_privilege('authenticated', c.oid, 'DELETE') as authenticated_direct_access
  from rpc_only r
  left join (
    select c.oid, c.relname, c.relrowsecurity
    from pg_class c
    join pg_namespace n on n.oid = c.relnamespace
    where n.nspname = 'public'
  ) c on c.relname = r.table_name
)
select
  'rpc_only_table_boundary' as check_name,
  case
    when exists (select 1 from observed where oid is null or not relrowsecurity or anon_direct_access or authenticated_direct_access)
      then 'FAIL'
    else 'PASS'
  end as status,
  (select count(*) from observed) as expected_count,
  (select count(*) from observed where oid is not null and relrowsecurity and not anon_direct_access and not authenticated_direct_access) as passing_count;

select
  'unexpected_anonymous_security_definer' as check_name,
  p.proname as function_name,
  pg_get_function_identity_arguments(p.oid) as identity_arguments,
  coalesce(array_to_string(p.proconfig, ';'), '') as config
from pg_proc p
join pg_namespace n on n.oid = p.pronamespace
where n.nspname = 'public'
  and p.prosecdef
  and has_function_privilege('anon', p.oid, 'EXECUTE')
  and p.proname not in (
    'civic_report_categories_v1', 'civic_report_comment_page_v1',
    'civic_report_dashboard_v1', 'civic_report_dashboard_v2',
    'civic_report_evidence_public_v1', 'civic_report_get_v1',
    'civic_report_page_v1', 'civic_report_page_v2',
    'civic_report_timeline_v1', 'ui_configuration_effective_global'
  )
order by p.proname
limit 100;

select
  'rpc_only_table_direct_access' as check_name,
  c.relname as table_name,
  has_table_privilege('anon', c.oid, 'SELECT')
    or has_table_privilege('anon', c.oid, 'INSERT')
    or has_table_privilege('anon', c.oid, 'UPDATE')
    or has_table_privilege('anon', c.oid, 'DELETE') as anon_direct_access,
  has_table_privilege('authenticated', c.oid, 'SELECT')
    or has_table_privilege('authenticated', c.oid, 'INSERT')
    or has_table_privilege('authenticated', c.oid, 'UPDATE')
    or has_table_privilege('authenticated', c.oid, 'DELETE') as authenticated_direct_access
from pg_class c
join pg_namespace n on n.oid = c.relnamespace
where n.nspname = 'public'
  and c.relname in (
    'access_role_audit_events', 'access_role_change_requests', 'access_session_controls',
    'admin_privacy_analytics_activity_events', 'admin_privacy_analytics_daily_totals',
    'api_rate_limits', 'app_error_logs', 'conversation_members', 'conversations',
    'messages', 'moderation_appeals', 'notifications', 'official_notice_lifecycle_events',
    'operational_control_events', 'operational_controls', 'operational_incidents',
    'operational_service_events', 'operational_work_assignment_events', 'operational_work_items',
    'post_likes', 'service_centre_booking_events', 'service_centre_booking_messages',
    'service_centre_bookings', 'service_centre_provider_profiles', 'trending_hashtags'
)
  and (
    has_table_privilege('anon', c.oid, 'SELECT')
    or has_table_privilege('anon', c.oid, 'INSERT')
    or has_table_privilege('anon', c.oid, 'UPDATE')
    or has_table_privilege('anon', c.oid, 'DELETE')
    or has_table_privilege('authenticated', c.oid, 'SELECT')
    or has_table_privilege('authenticated', c.oid, 'INSERT')
    or has_table_privilege('authenticated', c.oid, 'UPDATE')
    or has_table_privilege('authenticated', c.oid, 'DELETE')
  )
order by c.relname
limit 100;
