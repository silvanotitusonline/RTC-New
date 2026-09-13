BEGIN;
SET LOCAL search_path = extensions, public, pg_catalog;
SELECT plan(22);

SELECT is((SELECT count(*)::integer FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace
  WHERE n.nspname IN ('public','private') AND p.prosecdef
    AND (p.proname LIKE 'daily_post_%' OR p.proname IN
      ('validate_mfa_status','admin_access_guard','calculate_trust_score','route_civic_report_to_dept'))
    AND NOT EXISTS (SELECT 1 FROM unnest(p.proconfig) c WHERE c LIKE 'search_path=%')),
  0, 'Daily Post and repaired legacy definers pin their search paths');

SELECT is((SELECT count(*)::integer FROM (VALUES
  ('private.validate_mfa_status()'), ('public.calculate_trust_score()'),
  ('public.route_civic_report_to_dept(uuid)'), ('public.fan_out_community_post()'),
  ('public.broadcast_notification_event()'), ('public.daily_post_comment_count_sync()'),
  ('public.daily_post_require_editor()'), ('public.enforce_auth_signup_rate_limit()'),
  ('public.check_auth_rate_limit(text,text,integer,integer)')) f(signature)
  WHERE has_function_privilege('anon',signature,'EXECUTE')
     OR has_function_privilege('authenticated',signature,'EXECUTE')),
  0, 'Internal guards, triggers and routing placeholder are not client RPCs');

SELECT is((SELECT count(*)::integer FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace
  WHERE n.nspname='public' AND p.prosecdef AND p.proname LIKE 'daily_post_%'
    AND (has_function_privilege('anon',p.oid,'EXECUTE') OR EXISTS (
      SELECT 1 FROM aclexplode(coalesce(p.proacl,acldefault('f',p.proowner))) a
      WHERE a.grantee=0 AND a.privilege_type='EXECUTE'))),
  0, 'Daily Post definers have no anonymous or implicit PUBLIC execution');
SELECT ok(NOT has_function_privilege('anon','public.admin_access_guard()','EXECUTE')
  AND has_function_privilege('authenticated','public.admin_access_guard()','EXECUTE'),
  'Staff guard exposes only its authenticated client boundary');
SELECT ok(NOT has_function_privilege('anon','public.notify_realtime(text,text,jsonb)','EXECUTE')
  AND NOT has_function_privilege('authenticated','public.notify_realtime(text,text,jsonb)','EXECUTE')
  AND has_function_privilege('service_role','public.notify_realtime(text,text,jsonb)','EXECUTE'),
  'Arbitrary realtime broadcasts are server-only');
SELECT ok(NOT (SELECT prosecdef FROM pg_proc WHERE oid='public.get_cached_feed(uuid,integer)'::regprocedure),
  'Compatibility feed cannot bypass caller RLS');
SELECT ok(NOT (SELECT prosecdef FROM pg_proc WHERE oid='public.get_avatar_url(uuid,boolean)'::regprocedure),
  'Avatar reader cannot bypass profile RLS');
SELECT ok(NOT has_function_privilege('anon','public.get_avatar_url(uuid,boolean)','EXECUTE')
  AND has_function_privilege('authenticated','public.get_avatar_url(uuid,boolean)','EXECUTE'),
  'Avatar reader is restricted to authenticated profile access');

CREATE TEMP TABLE dp_identities(name text PRIMARY KEY, user_id uuid, session_id uuid) ON COMMIT DROP;
INSERT INTO dp_identities VALUES
  ('resident','a1000000-0000-4000-8000-000000000001','b1000000-0000-4000-8000-000000000001'),
  ('editor','a2000000-0000-4000-8000-000000000002','b2000000-0000-4000-8000-000000000002'),
  ('moderator','a3000000-0000-4000-8000-000000000003','b3000000-0000-4000-8000-000000000003'),
  ('admin','a4000000-0000-4000-8000-000000000004','b4000000-0000-4000-8000-000000000004');
GRANT SELECT ON dp_identities TO authenticated;
INSERT INTO auth.users(id,aud,role,email,created_at,updated_at)
SELECT user_id,'authenticated','authenticated',name || '@daily-post.invalid',now(),now() FROM dp_identities;
INSERT INTO auth.sessions(id,user_id,aal,created_at,updated_at,not_after)
SELECT session_id,user_id,'aal1'::auth.aal_level,now(),now(),now()+interval '1 day' FROM dp_identities;
INSERT INTO public.user_roles(user_id,role)
SELECT user_id,CASE name WHEN 'editor' THEN 'CONTENT_EDITOR' WHEN 'moderator' THEN 'MODERATOR'
  WHEN 'admin' THEN 'SYSTEM_ADMIN' ELSE 'RESIDENT' END::public.app_role FROM dp_identities;

SELECT set_config('request.jwt.claims',jsonb_build_object('sub',user_id,'session_id',session_id,
  'role','authenticated','aal','aal2')::text,true) FROM dp_identities WHERE name='resident';
SET LOCAL ROLE authenticated;
SELECT throws_ok('SELECT public.admin_access_guard()', '42501', 'Staff access required',
  'Resident cannot gain staff access even with MFA');
SELECT ok(NOT public.daily_post_can_edit() AND NOT public.daily_post_can_moderate(),
  'Resident cannot edit or moderate publications');
SELECT throws_ok('SELECT public.daily_post_admin_page_v1()', 'P0001', 'Not authorised',
  'Resident cannot browse publication drafts');

SELECT set_config('request.jwt.claims',jsonb_build_object('sub',user_id,'session_id',session_id,
  'role','authenticated','aal','aal1','app_metadata',jsonb_build_object('amfa_enabled',true))::text,true)
  FROM dp_identities WHERE name='admin';
SELECT throws_ok('SELECT public.admin_access_guard()', '42501', 'MFA_REQUIRED',
  'Enrollment metadata cannot replace administrator session MFA');
SELECT ok(NOT public.daily_post_can_edit(), 'Administrator at aal1 cannot edit or upload media');
SELECT ok(NOT public.daily_post_can_moderate(), 'Administrator at aal1 cannot moderate comments');
SELECT throws_ok('SELECT public.daily_post_admin_page_v1()', 'P0001', 'Not authorised',
  'Administrator at aal1 cannot browse drafts');

SELECT set_config('request.jwt.claims',jsonb_build_object('sub',user_id,'session_id',session_id,
  'role','authenticated','aal','aal2')::text,true) FROM dp_identities WHERE name='admin';
SELECT lives_ok('SELECT public.admin_access_guard()', 'Current administrator at aal2 passes staff guard');
SELECT ok(public.daily_post_can_edit() AND public.daily_post_can_moderate(),
  'Current administrator at aal2 can edit and moderate');
SELECT lives_ok('SELECT public.daily_post_admin_page_v1()', 'Current administrator can browse draft workspace');

RESET ROLE;
UPDATE auth.sessions SET not_after=now()-interval '1 minute'
WHERE id=(SELECT session_id FROM dp_identities WHERE name='admin');
SET LOCAL ROLE authenticated;
SELECT throws_ok('SELECT public.admin_access_guard()', '42501', 'Staff access required',
  'Expired administrator session is denied despite aal2 claim');
SELECT ok(NOT public.daily_post_can_edit() AND NOT public.daily_post_can_moderate(),
  'Expired administrator session loses publication and media authority');

SELECT set_config('request.jwt.claims',jsonb_build_object('sub',user_id,'session_id',session_id,
  'role','authenticated','aal','aal1')::text,true) FROM dp_identities WHERE name='editor';
SELECT ok(public.daily_post_can_edit() AND NOT public.daily_post_can_moderate(),
  'Content editor retains publishing role without moderation authority');
SELECT set_config('request.jwt.claims',jsonb_build_object('sub',user_id,'session_id',session_id,
  'role','authenticated','aal','aal1')::text,true) FROM dp_identities WHERE name='moderator';
SELECT ok(public.daily_post_can_moderate() AND NOT public.daily_post_can_edit(),
  'Moderator retains moderation role without publishing authority');

RESET ROLE;
SELECT * FROM finish();
ROLLBACK;
