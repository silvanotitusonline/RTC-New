BEGIN;

-- Use canonical role/session authority and actual MFA assurance. Enrollment
-- metadata alone never establishes that this session completed an MFA challenge.
CREATE OR REPLACE FUNCTION private.validate_mfa_status()
RETURNS boolean
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = ''
AS $$
  SELECT auth.uid() IS NOT NULL
    AND private.is_any_staff()
    AND COALESCE(auth.jwt()->>'aal', 'aal1') = 'aal2';
$$;
REVOKE ALL ON FUNCTION private.validate_mfa_status() FROM PUBLIC, anon, authenticated;

CREATE OR REPLACE FUNCTION public.admin_access_guard()
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
BEGIN
  IF auth.uid() IS NULL OR NOT private.is_any_staff() THEN
    RAISE EXCEPTION USING ERRCODE = '42501', MESSAGE = 'Staff access required';
  END IF;
  IF NOT private.validate_mfa_status() THEN
    RAISE EXCEPTION USING ERRCODE = '42501', MESSAGE = 'MFA_REQUIRED';
  END IF;
END;
$$;
REVOKE ALL ON FUNCTION public.admin_access_guard() FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.admin_access_guard() TO authenticated;

-- Trigger helpers execute as their owner through installed triggers, not as RPCs.
ALTER FUNCTION public.calculate_trust_score() SET search_path = public, pg_temp;
REVOKE ALL ON FUNCTION public.calculate_trust_score() FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.fan_out_community_post() FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.broadcast_notification_event() FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.daily_post_comment_count_sync() FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.daily_post_require_editor() FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.enforce_auth_signup_rate_limit() FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.check_auth_rate_limit(text,text,integer,integer) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.check_auth_rate_limit(text,text,integer,integer) TO service_role;

-- This unintegrated routing placeholder has no supported client contract.
ALTER FUNCTION public.route_civic_report_to_dept(uuid) SET search_path = '';
REVOKE ALL ON FUNCTION public.route_civic_report_to_dept(uuid) FROM PUBLIC, anon, authenticated;

-- Only the server dispatcher is a producer of arbitrary realtime messages.
REVOKE ALL ON FUNCTION public.notify_realtime(text,text,jsonb) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.notify_realtime(text,text,jsonb) TO service_role;

-- These compatibility readers must use the caller's RLS. A global cache cannot
-- safely hold feeds containing caller-specific visibility and reaction fields.
CREATE OR REPLACE FUNCTION public.get_cached_feed(p_cursor uuid DEFAULT NULL, p_limit integer DEFAULT 20)
RETURNS jsonb LANGUAGE sql STABLE SECURITY INVOKER
SET search_path = public, pg_temp
AS $$
  SELECT COALESCE(jsonb_agg(to_jsonb(f) ORDER BY f.created_at DESC, f.id DESC), '[]'::jsonb)
  FROM (
    SELECT visible.* FROM public.community_post_feed visible
    WHERE p_cursor IS NULL OR (visible.created_at, visible.id) < (
      SELECT cursor_post.created_at, cursor_post.id FROM public.community_post_feed cursor_post
      WHERE cursor_post.id = p_cursor
    )
    ORDER BY visible.created_at DESC, visible.id DESC
    LIMIT GREATEST(1, LEAST(COALESCE(p_limit,20),50))
  ) f;
$$;
ALTER FUNCTION public.get_avatar_url(uuid,boolean) SECURITY INVOKER;
REVOKE ALL ON FUNCTION public.get_avatar_url(uuid,boolean) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.get_avatar_url(uuid,boolean) TO authenticated, service_role;

-- Storage policies and moderation must enforce the same administrator assurance
-- as the publication RPCs. Content editors and moderators retain their own roles.
CREATE OR REPLACE FUNCTION public.daily_post_can_edit()
RETURNS boolean LANGUAGE sql STABLE SECURITY DEFINER
SET search_path = ''
AS $$
  SELECT public.daily_post_has_role(ARRAY['CONTENT_EDITOR','SYSTEM_ADMIN']::public.app_role[])
    AND (NOT private.has_role('SYSTEM_ADMIN'::public.app_role)
      OR COALESCE(auth.jwt()->>'aal', 'aal1') = 'aal2');
$$;
CREATE OR REPLACE FUNCTION public.daily_post_can_moderate()
RETURNS boolean LANGUAGE sql STABLE SECURITY DEFINER
SET search_path = ''
AS $$
  SELECT public.daily_post_has_role(ARRAY['MODERATOR','SYSTEM_ADMIN']::public.app_role[])
    AND (NOT private.has_role('SYSTEM_ADMIN'::public.app_role)
      OR COALESCE(auth.jwt()->>'aal', 'aal1') = 'aal2');
$$;

-- Pin pg_temp last for the existing qualified/unqualified Daily Post bodies.
-- Restrict newly created functions' default PUBLIC/anon grants explicitly.
DO $$
DECLARE fn regprocedure;
BEGIN
  FOR fn IN
    SELECT p.oid::regprocedure FROM pg_proc p
    JOIN pg_namespace n ON n.oid = p.pronamespace
    WHERE n.nspname = 'public' AND p.proname LIKE 'daily_post_%' AND p.prosecdef
  LOOP
    EXECUTE format('ALTER FUNCTION %s SET search_path = public, pg_temp', fn);
    EXECUTE format('REVOKE ALL ON FUNCTION %s FROM PUBLIC, anon', fn);
  END LOOP;
END;
$$;

COMMIT;
