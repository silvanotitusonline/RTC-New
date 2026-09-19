-- Daily Post comment visibility/count transition tests.
--
-- Run only through tools/tests/run_daily_post_comment_transition_tests.sh.
-- The test creates two disposable Daily Posts and rolls the entire fixture back.
-- It intentionally uses direct SQL so the trigger is tested independently of
-- authenticated RPC behavior.

begin;
set local search_path = extensions, public, pg_catalog;
select plan(18);

select has_table('public', 'daily_posts', 'Daily Post table exists');
select has_table('public', 'daily_post_comments', 'Daily Post comments table exists');
select has_trigger(
  'public',
  'daily_post_comments',
  'daily_post_comment_count_sync',
  'Visible-count trigger is installed'
);

create temporary table transition_fixture (
  post_a uuid not null,
  post_b uuid not null,
  author_id uuid not null
) on commit drop;

create temporary table transition_comments (
  label text primary key,
  id uuid not null
) on commit drop;

do $$
declare
  v_author_id uuid;
  v_post_a uuid;
  v_post_b uuid;
begin
  select id into v_author_id
    from auth.users
   order by created_at
   limit 1;

  if v_author_id is null then
    raise exception 'Staging must contain at least one auth user for the fixture';
  end if;

  insert into public.daily_posts (author_id, headline, excerpt, content_blocks)
  values (v_author_id, 'pgTAP transition fixture A', 'Disposable fixture', '[]'::jsonb)
  returning id into v_post_a;

  insert into public.daily_posts (author_id, headline, excerpt, content_blocks)
  values (v_author_id, 'pgTAP transition fixture B', 'Disposable fixture', '[]'::jsonb)
  returning id into v_post_b;

  insert into transition_fixture(post_a, post_b, author_id)
  values (v_post_a, v_post_b, v_author_id);
end;
$$;

select is(
  (select comment_count from public.daily_posts where id = post_a),
  0,
  'Fixture starts with zero comments on post A'
) from transition_fixture;
select is(
  (select comment_count from public.daily_posts where id = post_b),
  0,
  'Fixture starts with zero comments on post B'
) from transition_fixture;

-- INSERT: VISIBLE -> +1.
with inserted as (
  insert into public.daily_post_comments(post_id, author_id, body, state)
  select post_a, author_id, 'visible fixture comment', 'VISIBLE'
    from transition_fixture
  returning id
)
insert into transition_comments(label, id)
select 'visible', id from inserted;

select is(
  (select comment_count from public.daily_posts where id = post_a),
  1,
  'INSERT VISIBLE increments the count'
) from transition_fixture;

-- VISIBLE -> HIDDEN: -1.
update public.daily_post_comments
   set state = 'HIDDEN', body = 'hidden fixture comment'
 where id = (select id from transition_comments where label = 'visible');
select is(
  (select comment_count from public.daily_posts where id = post_a),
  0,
  'VISIBLE to HIDDEN decrements the count'
) from transition_fixture;

-- HIDDEN -> VISIBLE: +1.
update public.daily_post_comments
   set state = 'VISIBLE', body = 'visible again fixture comment'
 where id = (select id from transition_comments where label = 'visible');
select is(
  (select comment_count from public.daily_posts where id = post_a),
  1,
  'HIDDEN to VISIBLE increments the count'
) from transition_fixture;

-- VISIBLE -> DELETED: -1, including the audited empty-body shape.
update public.daily_post_comments
   set state = 'DELETED', body = ''
 where id = (select id from transition_comments where label = 'visible');
select is(
  (select comment_count from public.daily_posts where id = post_a),
  0,
  'VISIBLE to DELETED decrements the count'
) from transition_fixture;

-- DELETED -> HIDDEN: no change.
update public.daily_post_comments
   set state = 'HIDDEN', body = 'hidden after delete fixture comment'
 where id = (select id from transition_comments where label = 'visible');
select is(
  (select comment_count from public.daily_posts where id = post_a),
  0,
  'DELETED to HIDDEN does not change the count'
) from transition_fixture;

-- HIDDEN -> DELETED: no change.
update public.daily_post_comments
   set state = 'DELETED', body = ''
 where id = (select id from transition_comments where label = 'visible');
select is(
  (select comment_count from public.daily_posts where id = post_a),
  0,
  'HIDDEN to DELETED does not change the count'
) from transition_fixture;

-- DELETED -> VISIBLE: +1.
update public.daily_post_comments
   set state = 'VISIBLE', body = 'restored fixture comment'
 where id = (select id from transition_comments where label = 'visible');
select is(
  (select comment_count from public.daily_posts where id = post_a),
  1,
  'DELETED to VISIBLE increments the count'
) from transition_fixture;

-- A non-visibility update must not change the count.
update public.daily_post_comments
   set body = 'edited visible fixture comment', updated_at = now()
 where id = (select id from transition_comments where label = 'visible');
select is(
  (select comment_count from public.daily_posts where id = post_a),
  1,
  'A body/timestamp update does not change the count'
) from transition_fixture;

-- Move a visible comment between posts: -1 from old post, +1 to new post.
update public.daily_post_comments
   set post_id = (select post_b from transition_fixture)
 where id = (select id from transition_comments where label = 'visible');
select is(
  (select comment_count from public.daily_posts where id = post_a),
  0,
  'Moving a VISIBLE comment decrements the old post'
) from transition_fixture;
select is(
  (select comment_count from public.daily_posts where id = post_b),
  1,
  'Moving a VISIBLE comment increments the new post'
) from transition_fixture;

-- Physical delete of VISIBLE: -1.
delete from public.daily_post_comments
 where id = (select id from transition_comments where label = 'visible');
select is(
  (select comment_count from public.daily_posts where id = post_b),
  0,
  'Deleting a VISIBLE comment decrements the count'
) from transition_fixture;

-- INSERT: HIDDEN and INSERT: DELETED -> no change.
with inserted as (
  insert into public.daily_post_comments(post_id, author_id, body, state)
  select post_a, author_id, 'hidden fixture comment', 'HIDDEN'
    from transition_fixture
  returning id
)
insert into transition_comments(label, id)
select 'hidden', id from inserted;

with inserted as (
  insert into public.daily_post_comments(post_id, author_id, body, state)
  select post_a, author_id, '', 'DELETED'
    from transition_fixture
  returning id
)
insert into transition_comments(label, id)
select 'deleted', id from inserted;

select is(
  (select comment_count from public.daily_posts where id = post_a),
  0,
  'INSERT HIDDEN and INSERT DELETED do not increment the count'
) from transition_fixture;

-- Physical delete of non-visible comments: no change.
delete from public.daily_post_comments
 where id in (
   select id from transition_comments where label in ('hidden', 'deleted')
 );
select is(
  (select comment_count from public.daily_posts where id = post_a),
  0,
  'Deleting HIDDEN or DELETED comments does not change the count'
) from transition_fixture;

select * from finish();
rollback;
