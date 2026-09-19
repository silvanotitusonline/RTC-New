begin;

-- These domains are intentionally RPC-only. They have RLS enabled and no direct
-- client usage in the application; explicit revocation makes the boundary clear
-- and prevents future grants from exposing tables without a policy review.
revoke all on table
  public.access_role_audit_events,
  public.access_role_change_requests,
  public.access_session_controls,
  public.admin_privacy_analytics_activity_events,
  public.admin_privacy_analytics_daily_totals,
  public.api_rate_limits,
  public.app_error_logs,
  public.conversation_members,
  public.conversations,
  public.messages,
  public.moderation_appeals,
  public.notifications,
  public.official_notice_lifecycle_events,
  public.operational_control_events,
  public.operational_controls,
  public.operational_incidents,
  public.operational_service_events,
  public.operational_work_assignment_events,
  public.operational_work_items,
  public.post_likes,
  public.service_centre_booking_events,
  public.service_centre_booking_messages,
  public.service_centre_bookings,
  public.service_centre_provider_profiles,
  public.trending_hashtags,
  public.daily_post_comment_reports
from public, anon, authenticated;

commit;
