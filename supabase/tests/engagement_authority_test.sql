BEGIN;
SET LOCAL search_path = extensions, public, pg_catalog;
SELECT plan(10);

INSERT INTO auth.users(id,aud,role,email,created_at,updated_at) VALUES
 ('c1000000-0000-4000-8000-000000000001','authenticated','authenticated','engagement-viewer@local.invalid',now(),now()),
 ('c2000000-0000-4000-8000-000000000002','authenticated','authenticated','engagement-author@local.invalid',now(),now());
INSERT INTO public.profiles(id,display_name) VALUES
 ('c1000000-0000-4000-8000-000000000001','Engagement Viewer'),
 ('c2000000-0000-4000-8000-000000000002','Engagement Author');
INSERT INTO auth.sessions(id,user_id,aal,created_at,updated_at,not_after) VALUES
 ('d1000000-0000-4000-8000-000000000001','c1000000-0000-4000-8000-000000000001','aal1',now(),now(),now()+interval '1 day');
UPDATE public.community_profiles SET guidelines_version=private.current_community_guidelines_version(),guidelines_accepted_at=now()
 WHERE id='c1000000-0000-4000-8000-000000000001';
INSERT INTO public.community_posts(id,author_id,body,state,deleted_at) VALUES
 ('e1000000-0000-4000-8000-000000000001','c2000000-0000-4000-8000-000000000002','Published','PUBLISHED',NULL),
 ('e2000000-0000-4000-8000-000000000002','c2000000-0000-4000-8000-000000000002','Hidden','HIDDEN_BY_MODERATION',NULL),
 ('e3000000-0000-4000-8000-000000000003','c2000000-0000-4000-8000-000000000002','Deleted','PUBLISHED',now());
INSERT INTO public.community_bookmarks(user_id,post_id) VALUES
 ('c1000000-0000-4000-8000-000000000001','e2000000-0000-4000-8000-000000000002'),
 ('c2000000-0000-4000-8000-000000000002','e2000000-0000-4000-8000-000000000002');

SELECT ok(NOT has_table_privilege('authenticated','public.community_bookmarks','INSERT')
 AND NOT has_table_privilege('authenticated','public.community_bookmarks','DELETE'),
 'Bookmarks cannot bypass guarded RPCs through direct table mutation');
SELECT set_config('request.jwt.claims',jsonb_build_object(
 'sub','c1000000-0000-4000-8000-000000000001','session_id','d1000000-0000-4000-8000-000000000001',
 'role','authenticated','aal','aal1')::text,true);
SET LOCAL ROLE authenticated;
SELECT throws_ok($q$SELECT public.bookmark_community_post('e2000000-0000-4000-8000-000000000002')$q$,
 'P0001','POST_NOT_AVAILABLE','Hidden source cannot be bookmarked');
SELECT throws_ok($q$SELECT public.bookmark_community_post('e3000000-0000-4000-8000-000000000003')$q$,
 'P0001','POST_NOT_AVAILABLE','Deleted source cannot be bookmarked');
SELECT throws_ok($q$SELECT public.repost_community_post('e2000000-0000-4000-8000-000000000002')$q$,
 'P0001','POST_NOT_AVAILABLE','Hidden source cannot be reposted');
SELECT throws_ok($q$SELECT public.repost_community_post('e3000000-0000-4000-8000-000000000003')$q$,
 'P0001','POST_NOT_AVAILABLE','Deleted source cannot be reposted');
SELECT lives_ok($q$SELECT public.bookmark_community_post('e1000000-0000-4000-8000-000000000001')$q$,
 'Visible source can be bookmarked');
SELECT lives_ok($q$SELECT public.repost_community_post('e1000000-0000-4000-8000-000000000001')$q$,
 'Visible source can be reposted');
SELECT is(
 (SELECT bookmark_count FROM public.unbookmark_community_post('e2000000-0000-4000-8000-000000000002')),
 0,
 'Own hidden bookmark can be removed without revealing other residents count'
);
SELECT is((SELECT count(*)::integer FROM public.community_bookmarks
 WHERE post_id='e2000000-0000-4000-8000-000000000002'),0,'Own hidden bookmark was removed');
RESET ROLE;
SELECT is((SELECT count(*)::integer FROM public.community_bookmarks
 WHERE post_id='e2000000-0000-4000-8000-000000000002'),1,'Other residents bookmark remains untouched');
SELECT * FROM finish();
ROLLBACK;
