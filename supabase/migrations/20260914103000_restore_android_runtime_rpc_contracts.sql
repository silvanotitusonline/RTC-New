begin;

-- Re-establish the canonical engagement columns in any promoted environment whose later
-- pillar migration replaced them with an alternate representation. These are already present
-- in a clean source replay, so the statements are forward-safe and idempotent.
alter table public.community_posts
  add column if not exists repost_of_id uuid references public.community_posts(id) on delete set null,
  add column if not exists quote_post_id uuid references public.community_posts(id) on delete set null,
  add column if not exists repost_count integer not null default 0 check (repost_count >= 0),
  add column if not exists bookmark_count integer not null default 0 check (bookmark_count >= 0);

create index if not exists idx_community_posts_repost_of
  on public.community_posts(repost_of_id) where repost_of_id is not null;
create index if not exists idx_community_posts_quote_post
  on public.community_posts(quote_post_id) where quote_post_id is not null;
create index if not exists community_bookmarks_post_idx
  on public.community_bookmarks(post_id);

-- Rich keyset page consumed by the Android Community feed. Keep v2 as compatibility fallback.
create or replace function public.community_post_page_v3(
  p_before_created_at timestamptz default null,
  p_before_id uuid default null,
  p_limit integer default 20
)
returns table(
  id uuid,
  author_id uuid,
  author_name text,
  author_handle text,
  avatar_path text,
  avatar_updated_at timestamptz,
  staff_badge boolean,
  body text,
  category_label text,
  is_locked boolean,
  created_at timestamptz,
  edited_at timestamptz,
  comment_count integer,
  reaction_count integer,
  viewer_has_liked boolean,
  trending_score integer,
  is_followed_topic boolean,
  repost_of_id uuid,
  quote_post_id uuid,
  repost_count integer,
  bookmark_count integer,
  is_reposted_by_viewer boolean,
  is_bookmarked_by_viewer boolean,
  media jsonb
)
language sql
stable
security invoker
set search_path = public, pg_temp
as $$
  select
    f.id,
    f.author_id,
    f.author_name,
    f.author_handle,
    f.avatar_path,
    f.avatar_updated_at,
    f.staff_badge,
    f.body,
    f.category_label,
    f.is_locked,
    f.created_at,
    f.edited_at,
    f.comment_count,
    f.reaction_count,
    f.viewer_has_liked,
    f.trending_score,
    f.is_followed_topic,
    p.repost_of_id,
    p.quote_post_id,
    p.repost_count,
    p.bookmark_count,
    exists(
      select 1 from public.community_posts rp
      where rp.author_id = auth.uid()
        and rp.repost_of_id = f.id
        and rp.deleted_at is null
    ) as is_reposted_by_viewer,
    exists(
      select 1 from public.community_bookmarks b
      where b.post_id = f.id and b.user_id = auth.uid()
    ) as is_bookmarked_by_viewer,
    f.media
  from public.community_post_feed f
  join public.community_posts p on p.id = f.id
  where
    (p_before_created_at is null and p_before_id is null)
    or (
      p_before_created_at is not null
      and p_before_id is not null
      and (f.created_at, f.id) < (p_before_created_at, p_before_id)
    )
  order by f.created_at desc, f.id desc
  limit greatest(1, least(coalesce(p_limit, 20), 50));
$$;

revoke all on function public.community_post_page_v3(timestamptz, uuid, integer) from public, anon;
grant execute on function public.community_post_page_v3(timestamptz, uuid, integer) to authenticated;

-- PostgREST returns SETOF/TABLE functions as arrays. The Android repository intentionally
-- decodes these authoritative mutation outcomes as lists, so every engagement RPC uses TABLE.
drop function if exists public.toggle_community_post_like(uuid);
create function public.toggle_community_post_like(p_post_id uuid)
returns table(liked boolean, like_count integer)
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_actor uuid := auth.uid();
  v_existing text;
  v_count integer;
  v_liked boolean;
begin
  if v_actor is null or not private.access_session_is_current() then raise exception 'AUTH_REQUIRED'; end if;
  perform private.ensure_community_profile_for_account(v_actor);
  if not private.community_guidelines_accepted() then raise exception 'GUIDELINES_NOT_ACCEPTED'; end if;
  perform 1 from public.community_posts where id = p_post_id for update;
  if not found or not private.is_public_community_post(p_post_id) then raise exception 'POST_NOT_AVAILABLE'; end if;

  select r.reaction_type into v_existing
  from public.community_reactions r
  where r.actor_id = v_actor and r.subject_type = 'POST' and r.subject_id = p_post_id
  for update;

  if v_existing = 'LIKE' then
    delete from public.community_reactions
    where actor_id = v_actor and subject_type = 'POST' and subject_id = p_post_id;
    v_liked := false;
  else
    insert into public.community_reactions(actor_id, subject_type, subject_id, reaction_type)
    values (v_actor, 'POST', p_post_id, 'LIKE')
    on conflict (actor_id, subject_type, subject_id) do update
      set reaction_type = excluded.reaction_type, updated_at = now();
    v_liked := true;
  end if;

  select count(*)::integer into v_count
  from public.community_reactions r
  where r.subject_type = 'POST' and r.subject_id = p_post_id and r.reaction_type = 'LIKE';
  return query select v_liked, v_count;
end;
$$;

revoke all on function public.toggle_community_post_like(uuid) from public, anon;
grant execute on function public.toggle_community_post_like(uuid) to authenticated;

create or replace function public.toggle_community_post_reaction(p_post_id uuid, p_emoji text)
returns table(liked boolean, like_count integer)
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_actor uuid := auth.uid();
  v_reaction text;
  v_existing text;
  v_count integer;
  v_active boolean;
begin
  if v_actor is null or not private.access_session_is_current() then raise exception 'AUTH_REQUIRED'; end if;
  perform private.ensure_community_profile_for_account(v_actor);
  if not private.community_guidelines_accepted() then raise exception 'GUIDELINES_NOT_ACCEPTED'; end if;
  perform 1 from public.community_posts where id = p_post_id for update;
  if not found or not private.is_public_community_post(p_post_id) then raise exception 'POST_NOT_AVAILABLE'; end if;

  v_reaction := case trim(coalesce(p_emoji, ''))
    when '❤️' then 'LIKE'
    when '❤' then 'LIKE'
    when '👍' then 'HELPFUL'
    when '🎉' then 'CELEBRATE'
    when '🤝' then 'SUPPORT'
    when '😟' then 'CONCERN'
    else upper(trim(coalesce(p_emoji, '')))
  end;
  if v_reaction not in ('LIKE', 'HELPFUL', 'CELEBRATE', 'SUPPORT', 'CONCERN') then
    raise exception 'INVALID_REACTION';
  end if;

  select r.reaction_type into v_existing
  from public.community_reactions r
  where r.actor_id = v_actor and r.subject_type = 'POST' and r.subject_id = p_post_id
  for update;

  if v_existing = v_reaction then
    delete from public.community_reactions
    where actor_id = v_actor and subject_type = 'POST' and subject_id = p_post_id;
    v_active := false;
  else
    insert into public.community_reactions(actor_id, subject_type, subject_id, reaction_type)
    values (v_actor, 'POST', p_post_id, v_reaction)
    on conflict (actor_id, subject_type, subject_id) do update
      set reaction_type = excluded.reaction_type, updated_at = now();
    v_active := true;
  end if;

  select count(*)::integer into v_count
  from public.community_reactions r
  where r.subject_type = 'POST' and r.subject_id = p_post_id and r.reaction_type = 'LIKE';
  return query select (v_active and v_reaction = 'LIKE'), v_count;
end;
$$;

revoke all on function public.toggle_community_post_reaction(uuid, text) from public, anon;
grant execute on function public.toggle_community_post_reaction(uuid, text) to authenticated;

create or replace function public.repost_community_post(p_post_id uuid)
returns table(reposted boolean, repost_count integer)
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_actor uuid := auth.uid();
  v_existing uuid;
  v_count integer;
  v_reposted boolean;
begin
  if v_actor is null or not private.access_session_is_current() then raise exception 'AUTH_REQUIRED'; end if;
  perform private.ensure_community_profile_for_account(v_actor);
  if not private.community_guidelines_accepted() then raise exception 'GUIDELINES_NOT_ACCEPTED'; end if;
  perform 1 from public.community_posts where id = p_post_id for update;
  if not found or not private.is_public_community_post(p_post_id) then raise exception 'POST_NOT_AVAILABLE'; end if;

  select id into v_existing
  from public.community_posts
  where author_id = v_actor and repost_of_id = p_post_id and deleted_at is null
  order by id limit 1;

  if v_existing is not null then
    update public.community_posts set deleted_at = now()
    where author_id = v_actor and repost_of_id = p_post_id and deleted_at is null;
    v_reposted := false;
  else
    insert into public.community_posts(author_id, body, state, is_locked, report_count, repost_of_id)
    values(v_actor, '', 'PUBLISHED', false, 0, p_post_id);
    v_reposted := true;
  end if;

  select count(*)::integer into v_count
  from public.community_posts
  where repost_of_id = p_post_id and deleted_at is null;
  update public.community_posts set repost_count = v_count where id = p_post_id;
  return query select v_reposted, v_count;
end;
$$;

revoke all on function public.repost_community_post(uuid) from public, anon;
grant execute on function public.repost_community_post(uuid) to authenticated;

create or replace function public.bookmark_community_post(p_post_id uuid)
returns table(bookmarked boolean, bookmark_count integer)
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_actor uuid := auth.uid();
  v_count integer;
begin
  if v_actor is null or not private.access_session_is_current() then raise exception 'AUTH_REQUIRED'; end if;
  perform 1 from public.community_posts where id = p_post_id for update;
  if not found or not private.is_public_community_post(p_post_id) then raise exception 'POST_NOT_AVAILABLE'; end if;
  perform private.ensure_community_profile_for_account(v_actor);

  insert into public.community_bookmarks(user_id, post_id) values(v_actor, p_post_id)
  on conflict (user_id, post_id) do nothing;
  select count(*)::integer into v_count from public.community_bookmarks where post_id = p_post_id;
  update public.community_posts set bookmark_count = v_count where id = p_post_id;
  return query select true, v_count;
end;
$$;

create or replace function public.unbookmark_community_post(p_post_id uuid)
returns table(bookmarked boolean, bookmark_count integer)
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_actor uuid := auth.uid();
  v_count integer;
  v_visible boolean;
begin
  if v_actor is null or not private.access_session_is_current() then raise exception 'AUTH_REQUIRED'; end if;
  perform 1 from public.community_posts where id = p_post_id for update;
  v_visible := private.is_public_community_post(p_post_id);
  delete from public.community_bookmarks where user_id = v_actor and post_id = p_post_id;
  select count(*)::integer into v_count from public.community_bookmarks where post_id = p_post_id;
  update public.community_posts set bookmark_count = v_count where id = p_post_id;
  return query select false, case when v_visible then v_count else 0 end;
end;
$$;

revoke all on function public.bookmark_community_post(uuid) from public, anon;
revoke all on function public.unbookmark_community_post(uuid) from public, anon;
grant execute on function public.bookmark_community_post(uuid) to authenticated;
grant execute on function public.unbookmark_community_post(uuid) to authenticated;

-- Search/autocomplete migrations existed in source history but were absent from some promoted
-- environments. Reassert them here so Android search is never dependent on promotion history.
create or replace function public.search_community_posts_cursor(
  p_query text,
  p_last_rank real default null,
  p_last_id uuid default null,
  p_limit integer default 20
)
returns table(
  id uuid,
  author_id uuid,
  author_name text,
  author_handle text,
  avatar_path text,
  avatar_updated_at timestamptz,
  staff_badge boolean,
  body text,
  category_label text,
  is_locked boolean,
  created_at timestamptz,
  edited_at timestamptz,
  comment_count integer,
  reaction_count integer,
  viewer_has_liked boolean,
  trending_score integer,
  is_followed_topic boolean,
  repost_of_id uuid,
  quote_post_id uuid,
  repost_count integer,
  bookmark_count integer,
  is_reposted_by_viewer boolean,
  is_bookmarked_by_viewer boolean,
  media jsonb,
  search_rank real
)
language sql
stable
security invoker
set search_path = public, pg_temp
as $$
  with ranked as (
    select
      p3.*,
      ts_rank(to_tsvector('english', coalesce(p3.body, '')), websearch_to_tsquery('english', p_query))::real as search_rank
    from public.community_post_page_v3(null, null, 50) p3
    where nullif(trim(p_query), '') is not null
      and (
        to_tsvector('english', coalesce(p3.body, '')) @@ websearch_to_tsquery('english', p_query)
        or p3.author_name ilike '%' || trim(p_query) || '%'
        or p3.author_handle ilike '%' || trim(p_query) || '%'
        or p3.category_label ilike '%' || trim(p_query) || '%'
      )
  )
  select * from ranked r
  where p_last_rank is null
     or r.search_rank < p_last_rank
     or (r.search_rank = p_last_rank and r.id < p_last_id)
  order by r.search_rank desc, r.id desc
  limit greatest(1, least(coalesce(p_limit, 20), 50));
$$;

create or replace function public.search_community_posts(
  p_query text,
  p_limit integer default 20,
  p_offset integer default 0
)
returns table(
  id uuid,
  author_id uuid,
  author_name text,
  author_handle text,
  avatar_path text,
  avatar_updated_at timestamptz,
  staff_badge boolean,
  body text,
  category_label text,
  is_locked boolean,
  created_at timestamptz,
  edited_at timestamptz,
  comment_count integer,
  reaction_count integer,
  viewer_has_liked boolean,
  trending_score integer,
  is_followed_topic boolean,
  repost_of_id uuid,
  quote_post_id uuid,
  repost_count integer,
  bookmark_count integer,
  is_reposted_by_viewer boolean,
  is_bookmarked_by_viewer boolean,
  media jsonb
)
language sql
stable
security invoker
set search_path = public, pg_temp
as $$
  select p3.*
  from public.community_post_page_v3(null, null, 50) p3
  where nullif(trim(p_query), '') is not null
    and (
      to_tsvector('english', coalesce(p3.body, '')) @@ websearch_to_tsquery('english', p_query)
      or p3.author_name ilike '%' || trim(p_query) || '%'
      or p3.author_handle ilike '%' || trim(p_query) || '%'
      or p3.category_label ilike '%' || trim(p_query) || '%'
    )
  order by p3.created_at desc, p3.id desc
  limit greatest(1, least(coalesce(p_limit, 20), 50))
  offset greatest(0, coalesce(p_offset, 0));
$$;

create or replace function public.autocomplete_hashtags(p_prefix text, p_limit integer default 10)
returns table(tag text, post_count bigint)
language sql stable security invoker set search_path = public, pg_temp as $$
  select h.tag, count(ph.post_id)::bigint
  from public.community_hashtags h
  left join public.community_post_hashtags ph on ph.hashtag_id = h.id
  where h.tag ilike trim(leading '#' from coalesce(p_prefix, '')) || '%'
  group by h.id, h.tag
  order by count(ph.post_id) desc, h.tag asc
  limit greatest(1, least(coalesce(p_limit, 10), 30));
$$;

create or replace function public.autocomplete_mentions(p_prefix text, p_limit integer default 10)
returns table(id uuid, handle text, display_name text, avatar_path text)
language sql stable security invoker set search_path = public, pg_temp as $$
  select p.id, p.handle, p.display_name, p.avatar_path
  from public.community_profiles p
  where p.handle ilike trim(leading '@' from coalesce(p_prefix, '')) || '%'
     or p.display_name ilike coalesce(p_prefix, '') || '%'
  order by p.display_name asc, p.id
  limit greatest(1, least(coalesce(p_limit, 10), 30));
$$;

revoke all on function public.search_community_posts_cursor(text, real, uuid, integer) from public, anon;
revoke all on function public.search_community_posts(text, integer, integer) from public, anon;
revoke all on function public.autocomplete_hashtags(text, integer) from public, anon;
revoke all on function public.autocomplete_mentions(text, integer) from public, anon;
grant execute on function public.search_community_posts_cursor(text, real, uuid, integer) to authenticated;
grant execute on function public.search_community_posts(text, integer, integer) to authenticated;
grant execute on function public.autocomplete_hashtags(text, integer) to authenticated;
grant execute on function public.autocomplete_mentions(text, integer) to authenticated;

-- Forward-cover Operations Hub RPCs in production promotions that pre-date their source migration.
create or replace function public.ops_workspace_summary_v1()
returns table(
  assigned_to_me bigint,
  high_priority bigint,
  unassigned bigint,
  ready_for_review bigint,
  overdue bigint,
  total_visible bigint
)
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_actor uuid := private.ops_assert_staff();
  v_role public.app_role := private.access_current_role(v_actor);
begin
  return query
  with visible_work as (
    select w.* from public.operational_work_items w
    where w.state in ('OPEN', 'CLAIMED', 'READY_FOR_REVIEW')
      and (v_role = 'SYSTEM_ADMIN'::public.app_role or w.target_role = v_role)
      and (w.assigned_to is null or w.assigned_to = v_actor or v_role = 'SYSTEM_ADMIN'::public.app_role)
  )
  select
    count(*) filter (where assigned_to = v_actor),
    count(*) filter (where priority in ('URGENT', 'HIGH')),
    count(*) filter (where assigned_to is null),
    count(*) filter (where state = 'READY_FOR_REVIEW'),
    count(*) filter (where due_at is not null and due_at < now()),
    count(*)
  from visible_work;
end;
$$;

create or replace function public.ops_list_eligible_assignees_v1(p_work_item_id uuid)
returns table(user_id uuid, display_name text, role public.app_role)
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_actor uuid := private.access_assert_system_admin();
  v_target_role public.app_role;
begin
  select w.target_role into v_target_role
  from public.operational_work_items w
  where w.id = p_work_item_id and w.state not in ('RESOLVED', 'CANCELLED');

  if v_target_role is null then raise exception 'This work item is not available for reassignment.'; end if;

  return query
  select
    p.id,
    coalesce(nullif(trim(p.display_name), ''), 'Staff member'),
    private.access_current_role(p.id)
  from public.profiles p
  where private.access_current_role(p.id) in (v_target_role, 'SYSTEM_ADMIN'::public.app_role)
  order by
    case when p.id = v_actor then 0 else 1 end,
    lower(coalesce(nullif(trim(p.display_name), ''), 'Staff member')),
    p.id
  limit 100;
end;
$$;

revoke all on function public.ops_workspace_summary_v1() from public, anon;
revoke all on function public.ops_list_eligible_assignees_v1(uuid) from public, anon;
grant execute on function public.ops_workspace_summary_v1() to authenticated;
grant execute on function public.ops_list_eligible_assignees_v1(uuid) to authenticated;

commit;
