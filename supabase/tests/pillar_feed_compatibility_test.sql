begin;
set local search_path = extensions, public, pg_catalog;
select plan(13);

insert into auth.users(id, aud, role, email, created_at, updated_at)
values ('ab910001-0000-4000-8000-000000000001', 'authenticated', 'authenticated',
        'pillar-feed@local.invalid', now(), now());
insert into public.profiles(id, display_name)
values ('ab910001-0000-4000-8000-000000000001', 'Feed fixture');
update public.community_profiles
set handle = 'pillar_fixture', staff_badge = true,
    guidelines_version = 1, guidelines_accepted_at = now()
where id = 'ab910001-0000-4000-8000-000000000001';

-- These inserts also exercise the generated search column after every migration.
select lives_ok($sql$
  insert into public.community_posts(id, author_id, body, state) values
    ('ab920001-0000-4000-8000-000000000001', 'ab910001-0000-4000-8000-000000000001', 'quartzfixture published', 'PUBLISHED'),
    ('ab920001-0000-4000-8000-000000000002', 'ab910001-0000-4000-8000-000000000001', 'quartzfixture draft', 'DRAFT')
$sql$, 'post inserts do not assign a generated search column or nonexistent author field');
insert into public.community_reactions(actor_id, subject_type, subject_id, reaction_type)
values ('ab910001-0000-4000-8000-000000000001', 'POST',
        'ab920001-0000-4000-8000-000000000001', 'LIKE');
insert into public.community_comments(post_id, author_id, body, state) values
  ('ab920001-0000-4000-8000-000000000001', 'ab910001-0000-4000-8000-000000000001', 'Visible reply', 'PUBLISHED'),
  ('ab920001-0000-4000-8000-000000000001', 'ab910001-0000-4000-8000-000000000001', 'Hidden reply', 'HIDDEN_BY_MODERATION');

select ok((select attgenerated = 's' from pg_attribute
           where attrelid = 'public.community_posts'::regclass and attname = 'search_vector'),
          'canonical search vector remains stored generated');
select ok((select reloptions @> array['security_invoker=true'] from pg_class
           where oid = 'public.v_community_feed'::regclass), 'feed evaluates caller RLS');
select ok((select reloptions @> array['security_invoker=true'] from pg_class
           where oid = 'public.v_community_feed_optimized'::regclass), 'optimized feed evaluates caller RLS');
select ok(not has_function_privilege('anon', 'public.community_search(text)', 'EXECUTE'),
          'anonymous users cannot execute the compatibility search RPC');

select set_config('request.jwt.claims',
  '{"sub":"ab910001-0000-4000-8000-000000000001","role":"authenticated","aal":"aal1"}', true);
set local role authenticated;

select is((select username from public.v_community_feed where id = 'ab920001-0000-4000-8000-000000000001'),
          'pillar_fixture', 'feed uses canonical community handle');
select is((select is_verified from public.v_community_feed where id = 'ab920001-0000-4000-8000-000000000001'),
          false, 'staff badge is not misrepresented as identity verification');
select is((select like_count from public.v_community_feed where id = 'ab920001-0000-4000-8000-000000000001'),
          1::bigint, 'feed counts canonical post likes');
select is((select reply_count from public.v_community_feed where id = 'ab920001-0000-4000-8000-000000000001'),
          1::bigint, 'feed counts only published comments');
select is((select count(*) from public.v_community_feed where id = 'ab920001-0000-4000-8000-000000000002'),
          0::bigint, 'even an owner does not see drafts in a published feed');
select results_eq(
  $$select * from public.v_community_feed_optimized where id = 'ab920001-0000-4000-8000-000000000001'$$,
  $$select * from public.v_community_feed where id = 'ab920001-0000-4000-8000-000000000001'$$,
  'both feed projections return identical results');
select results_eq($$select post_id from public.community_search('quartzfixture')$$,
  $$values ('ab920001-0000-4000-8000-000000000001'::uuid)$$,
  'search returns the published fixture and excludes the owner draft');
select is((select count(*) from public.community_search('   ')), 0::bigint,
          'blank search returns no rows');

reset role;
select * from finish();
rollback;
