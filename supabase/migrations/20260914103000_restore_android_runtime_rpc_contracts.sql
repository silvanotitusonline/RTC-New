begin;

-- The Android Community client consumes a cursor page that also carries server-authoritative
-- repost/bookmark state. Keep the legacy v2 RPC for compatibility and expose the richer v3
-- projection as a separate contract.
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
    case when p.post_type = 'repost' then p.original_post_id else null end as repost_of_id,
    case when p.post_type = 'quote' then p.original_post_id else null end as quote_post_id,
    (
      select count(*)::integer
      from public.community_reposts r
      where r.post_id = f.id
    ) as repost_count,
    (
      select count(*)::integer
      from public.community_bookmarks b
      where b.post_id = f.id
    ) as bookmark_count,
    exists(
      select 1
      from public.community_reposts r
      where r.post_id = f.id
        and r.user_id = auth.uid()
    ) as is_reposted_by_viewer,
    exists(
      select 1
      from public.community_bookmarks b
      where b.post_id = f.id
        and b.user_id = auth.uid()
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

-- The original like RPC returned jsonb while every production Android implementation consumes a
-- row-set outcome. Replace the return contract without changing its server-authoritative logic.
drop function if exists public.toggle_community_post_like(uuid);
create function public.toggle_community_post_like(p_post_id uuid)
returns table(liked boolean, like_count integer)
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_actor_id uuid := auth.uid();
  v_existing_reaction text;
  v_like_count integer;
  v_liked boolean;
begin
  if v_actor_id is null then raise exception 'AUTH_REQUIRED'; end if;
  perform private.ensure_community_profile_for_account(v_actor_id);
  if not private.community_guidelines_accepted() then raise exception 'GUIDELINES_NOT_ACCEPTED'; end if;

  if not exists (
    select 1 from public.community_posts p
    where p.id = p_post_id
      and p.state in ('PUBLISHED', 'LOCKED')
      and p.deleted_at is null
      and private.can_view_community_author(p.author_id)
  ) then
    raise exception 'POST_NOT_AVAILABLE';
  end if;

  perform pg_advisory_xact_lock(hashtext(v_actor_id::text), hashtext(p_post_id::text));
  select r.reaction_type into v_existing_reaction
  from public.community_reactions r
  where r.actor_id = v_actor_id
    and r.subject_type = 'POST'
    and r.subject_id = p_post_id
  for update;

  if v_existing_reaction = 'LIKE' then
    delete from public.community_reactions
    where actor_id = v_actor_id and subject_type = 'POST' and subject_id = p_post_id;
    v_liked := false;
  else
    insert into public.community_reactions(actor_id, subject_type, subject_id, reaction_type)
    values (v_actor_id, 'POST', p_post_id, 'LIKE')
    on conflict (actor_id, subject_type, subject_id) do update
      set reaction_type = excluded.reaction_type, updated_at = now();
    v_liked := true;
  end if;

  select count(*)::integer into v_like_count
  from public.community_reactions r
  where r.subject_type = 'POST' and r.subject_id = p_post_id and r.reaction_type = 'LIKE';

  return query select v_liked, v_like_count;
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
  v_actor_id uuid := auth.uid();
  v_reaction text;
  v_existing text;
  v_count integer;
  v_active boolean;
begin
  if v_actor_id is null then raise exception 'AUTH_REQUIRED'; end if;
  perform private.ensure_community_profile_for_account(v_actor_id);
  if not private.community_guidelines_accepted() then raise exception 'GUIDELINES_NOT_ACCEPTED'; end if;

  if not exists (
    select 1 from public.community_posts p
    where p.id = p_post_id
      and p.state in ('PUBLISHED', 'LOCKED')
      and p.deleted_at is null
      and private.can_view_community_author(p.author_id)
  ) then
    raise exception 'POST_NOT_AVAILABLE';
  end if;

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

  perform pg_advisory_xact_lock(hashtext(v_actor_id::text), hashtext(p_post_id::text));
  select r.reaction_type into v_existing
  from public.community_reactions r
  where r.actor_id = v_actor_id and r.subject_type = 'POST' and r.subject_id = p_post_id
  for update;

  if v_existing = v_reaction then
    delete from public.community_reactions
    where actor_id = v_actor_id and subject_type = 'POST' and subject_id = p_post_id;
    v_active := false;
  else
    insert into public.community_reactions(actor_id, subject_type, subject_id, reaction_type)
    values (v_actor_id, 'POST', p_post_id, v_reaction)
    on conflict (actor_id, subject_type, subject_id) do update
      set reaction_type = excluded.reaction_type, updated_at = now();
    v_active := true;
  end if;

  select count(*)::integer into v_count
  from public.community_reactions r
  where r.subject_type = 'POST' and r.subject_id = p_post_id and r.reaction_type = 'LIKE';
  return query select v_active, v_count;
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
  v_actor_id uuid := auth.uid();
  v_reposted boolean;
  v_count integer;
begin
  if v_actor_id is null then raise exception 'AUTH_REQUIRED'; end if;
  perform private.ensure_community_profile_for_account(v_actor_id);
  if not private.community_guidelines_accepted() then raise exception 'GUIDELINES_NOT_ACCEPTED'; end if;
  if not exists (
    select 1 from public.community_posts p
    where p.id = p_post_id
      and p.state in ('PUBLISHED', 'LOCKED')
      and p.deleted_at is null
      and private.can_view_community_author(p.author_id)
  ) then raise exception 'POST_NOT_AVAILABLE'; end if;

  perform pg_advisory_xact_lock(hashtext(v_actor_id::text), hashtext(p_post_id::text));
  if exists(select 1 from public.community_reposts r where r.user_id = v_actor_id and r.post_id = p_post_id) then
    delete from public.community_reposts where user_id = v_actor_id and post_id = p_post_id;
    v_reposted := false;
  else
    insert into public.community_reposts(user_id, post_id) values (v_actor_id, p_post_id)
    on conflict do nothing;
    v_reposted := true;
  end if;

  select count(*)::integer into v_count from public.community_reposts r where r.post_id = p_post_id;
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
  v_actor_id uuid := auth.uid();
  v_count integer;
begin
  if v_actor_id is null then raise exception 'AUTH_REQUIRED'; end if;
  perform private.ensure_community_profile_for_account(v_actor_id);
  if not exists (
    select 1 from public.community_posts p
    where p.id = p_post_id
      and p.state in ('PUBLISHED', 'LOCKED')
      and p.deleted_at is null
      and private.can_view_community_author(p.author_id)
  ) then raise exception 'POST_NOT_AVAILABLE'; end if;

  insert into public.community_bookmarks(user_id, post_id)
  values (v_actor_id, p_post_id)
  on conflict do nothing;
  select count(*)::integer into v_count from public.community_bookmarks b where b.post_id = p_post_id;
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
  v_actor_id uuid := auth.uid();
  v_count integer;
begin
  if v_actor_id is null then raise exception 'AUTH_REQUIRED'; end if;
  delete from public.community_bookmarks where user_id = v_actor_id and post_id = p_post_id;
  select count(*)::integer into v_count from public.community_bookmarks b where b.post_id = p_post_id;
  return query select false, v_count;
end;
$$;

revoke all on function public.bookmark_community_post(uuid) from public, anon;
revoke all on function public.unbookmark_community_post(uuid) from public, anon;
grant execute on function public.bookmark_community_post(uuid) to authenticated;
grant execute on function public.unbookmark_community_post(uuid) to authenticated;

create index if not exists community_bookmarks_post_idx on public.community_bookmarks(post_id);

-- Forward-cover the Operations Hub RPCs in environments whose production promotion pre-dated
-- the source migration that introduced them.
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
    select w.*
    from public.operational_work_items w
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
    coalesce(nullif(trim(p.display_name), ''), 'Staff member') as display_name,
    private.access_current_role(p.id) as role
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
