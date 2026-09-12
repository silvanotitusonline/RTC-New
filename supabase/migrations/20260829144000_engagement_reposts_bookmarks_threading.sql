-- Migration: Repost, Bookmark, and Comment Threading Systems
-- Resolves Gap 2.3A, Gap 2.3B, and Gap 2.3C

begin;

-- Gap 2.3A: Repost & Quote Post system
alter table public.community_posts
    add column if not exists repost_of_id uuid references public.community_posts(id) on delete set null,
    add column if not exists quote_post_id uuid references public.community_posts(id) on delete set null,
    add column if not exists repost_count integer not null default 0 check (repost_count >= 0),
    add column if not exists bookmark_count integer not null default 0 check (bookmark_count >= 0);

create index if not exists idx_community_posts_repost_of
    on public.community_posts(repost_of_id) where repost_of_id is not null;

create index if not exists idx_community_posts_quote_post
    on public.community_posts(quote_post_id) where quote_post_id is not null;

-- Repost RPC function
create or replace function public.repost_community_post(p_post_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    v_user_id uuid := auth.uid();
    v_existing_id uuid;
    v_reposted boolean;
    v_new_count integer;
begin
    if v_user_id is null then raise exception 'AUTH_REQUIRED'; end if;
    perform private.ensure_community_profile_for_account(v_user_id);
    if not private.community_guidelines_accepted() then raise exception 'GUIDELINES_NOT_ACCEPTED'; end if;

    if not exists (
        select 1 from public.community_posts
        where id = p_post_id and state in ('PUBLISHED', 'LOCKED') and deleted_at is null
    ) then
        raise exception 'POST_NOT_AVAILABLE';
    end if;

    select id into v_existing_id
    from public.community_posts
    where author_id = v_user_id and repost_of_id = p_post_id and deleted_at is null
    limit 1;

    if v_existing_id is not null then
        update public.community_posts set deleted_at = now() where id = v_existing_id;
        update public.community_posts set repost_count = greatest(0, repost_count - 1) where id = p_post_id;
        v_reposted := false;
    else
        insert into public.community_posts (author_id, body, state, is_locked, report_count, repost_of_id)
        values (v_user_id, '', 'PUBLISHED', false, 0, p_post_id);
        update public.community_posts set repost_count = repost_count + 1 where id = p_post_id;
        v_reposted := true;
    end if;

    select repost_count into v_new_count from public.community_posts where id = p_post_id;

    return jsonb_build_object('reposted', v_reposted, 'repost_count', v_new_count);
end;
$$;

revoke all on function public.repost_community_post(uuid) from public, anon;
grant execute on function public.repost_community_post(uuid) to authenticated;

-- Gap 2.3B: Bookmark system
create table if not exists public.community_bookmarks (
    user_id uuid not null references public.community_profiles(id) on delete cascade,
    post_id uuid not null references public.community_posts(id) on delete cascade,
    created_at timestamptz not null default now(),
    primary key (user_id, post_id)
);

create index if not exists idx_community_bookmarks_user
    on public.community_bookmarks(user_id, created_at desc);

create index if not exists idx_community_bookmarks_post
    on public.community_bookmarks(post_id);

alter table public.community_bookmarks enable row level security;

create policy community_bookmarks_select_own on public.community_bookmarks
    for select to authenticated using (user_id = auth.uid());

create policy community_bookmarks_insert_own on public.community_bookmarks
    for insert to authenticated with check (user_id = auth.uid());

create policy community_bookmarks_delete_own on public.community_bookmarks
    for delete to authenticated using (user_id = auth.uid());

grant select, insert, delete on public.community_bookmarks to authenticated;

create or replace function public.bookmark_community_post(p_post_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    v_user_id uuid := auth.uid();
    v_count integer;
begin
    if v_user_id is null then raise exception 'AUTH_REQUIRED'; end if;

    insert into public.community_bookmarks (user_id, post_id)
    values (v_user_id, p_post_id)
    on conflict (user_id, post_id) do nothing;

    update public.community_posts
    set bookmark_count = (select count(*)::integer from public.community_bookmarks where post_id = p_post_id)
    where id = p_post_id;

    select bookmark_count into v_count from public.community_posts where id = p_post_id;

    return jsonb_build_object('bookmarked', true, 'bookmark_count', coalesce(v_count, 0));
end;
$$;

create or replace function public.unbookmark_community_post(p_post_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    v_user_id uuid := auth.uid();
    v_count integer;
begin
    if v_user_id is null then raise exception 'AUTH_REQUIRED'; end if;

    delete from public.community_bookmarks
    where user_id = v_user_id and post_id = p_post_id;

    update public.community_posts
    set bookmark_count = (select count(*)::integer from public.community_bookmarks where post_id = p_post_id)
    where id = p_post_id;

    select bookmark_count into v_count from public.community_posts where id = p_post_id;

    return jsonb_build_object('bookmarked', false, 'bookmark_count', coalesce(v_count, 0));
end;
$$;

revoke all on function public.bookmark_community_post(uuid) from public, anon;
grant execute on function public.bookmark_community_post(uuid) to authenticated;
revoke all on function public.unbookmark_community_post(uuid) from public, anon;
grant execute on function public.unbookmark_community_post(uuid) to authenticated;

-- Gap 2.3C: Threading support for comments
alter table public.community_comments
    add column if not exists reply_count integer not null default 0 check (reply_count >= 0),
    add column if not exists depth smallint not null default 0 check (depth >= 0 and depth <= 5);

create or replace function public.create_community_comment(
    p_post_id uuid,
    p_body text,
    p_parent_comment_id uuid default null
)
returns uuid
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    v_user_id uuid := auth.uid();
    v_comment_id uuid;
    v_parent_depth smallint := 0;
    v_clean_body text;
begin
    if v_user_id is null then raise exception 'AUTH_REQUIRED'; end if;
    v_clean_body := public.sanitize_input_text(p_body);
    if char_length(v_clean_body) < 1 or char_length(v_clean_body) > 280 then
        raise exception 'INVALID_COMMENT';
    end if;

    perform private.ensure_community_profile_for_account(v_user_id);
    if not private.community_guidelines_accepted() then raise exception 'GUIDELINES_NOT_ACCEPTED'; end if;

    if not exists (
        select 1 from public.community_posts
        where id = p_post_id and state in ('PUBLISHED', 'LOCKED') and deleted_at is null
    ) then
        raise exception 'POST_NOT_AVAILABLE';
    end if;

    if p_parent_comment_id is not null then
        select depth into v_parent_depth
        from public.community_comments
        where id = p_parent_comment_id and post_id = p_post_id and deleted_at is null and state = 'PUBLISHED';

        if v_parent_depth is null then
            raise exception 'PARENT_COMMENT_NOT_FOUND';
        end if;

        if v_parent_depth >= 5 then
            raise exception 'MAX_COMMENT_DEPTH_EXCEEDED';
        end if;

        update public.community_comments
        set reply_count = reply_count + 1
        where id = p_parent_comment_id;
    end if;

    insert into public.community_comments (
        post_id, author_id, body, state, report_count, parent_comment_id, depth, reply_count
    ) values (
        p_post_id, v_user_id, v_clean_body, 'PUBLISHED', 0, p_parent_comment_id,
        (v_parent_depth + case when p_parent_comment_id is not null then 1 else 0 end)::smallint, 0
    ) returning id into v_comment_id;

    return v_comment_id;
end;
$$;

revoke all on function public.create_community_comment(uuid, text, uuid) from public, anon;
grant execute on function public.create_community_comment(uuid, text, uuid) to authenticated;

-- Recursive comment thread view
create or replace view public.community_comment_thread
with (security_invoker = true)
as
select
    c.id,
    c.post_id,
    c.author_id,
    cp.display_name as author_name,
    cp.handle as author_handle,
    cp.avatar_path,
    cp.staff_badge,
    c.body,
    c.state,
    c.created_at,
    c.updated_at,
    c.edited_at,
    c.parent_comment_id,
    c.depth,
    c.reply_count
from public.community_comments c
join public.community_profiles cp on cp.id = c.author_id
where c.state = 'PUBLISHED' and c.deleted_at is null;

revoke all on public.community_comment_thread from public, anon;
grant select on public.community_comment_thread to authenticated, anon, service_role;

-- Update community_post_feed to include reposts, bookmarks, and user engagement states
create or replace view public.community_post_feed
with (security_invoker = true)
as
with reaction_counts as (
    select subject_id,
           count(*)::integer as reaction_count,
           bool_or(actor_id = auth.uid()) as viewer_has_liked
    from public.community_reactions
    where subject_type = 'POST' and reaction_type = 'LIKE'
    group by subject_id
),
comment_counts as (
    select post_id, count(*)::integer as comment_count
    from public.community_comments
    where state = 'PUBLISHED' and deleted_at is null
    group by post_id
),
topic_follows as (
    select category_id, user_id
    from public.community_topic_follows
    where user_id = auth.uid()
),
viewer_reposts as (
    select repost_of_id
    from public.community_posts
    where author_id = auth.uid() and repost_of_id is not null and deleted_at is null
),
viewer_bookmarks as (
    select post_id
    from public.community_bookmarks
    where user_id = auth.uid()
),
media_agg as (
    select post_id,
           coalesce(jsonb_agg(
               jsonb_build_object(
                   'id', id,
                   'storage_path', storage_path,
                   'media_kind', media_kind,
                   'mime_type', mime_type,
                   'byte_size', byte_size,
                   'width', width,
                   'height', height,
                   'duration_seconds', duration_seconds,
                   'position', position,
                   'caption', caption
               ) order by position
           )::jsonb, '[]'::jsonb) as media_json
    from public.community_post_media
    group by post_id
)
select
    p.id,
    p.author_id,
    cp.display_name as author_name,
    cp.handle as author_handle,
    cp.avatar_path,
    cp.staff_badge,
    p.body,
    p.state,
    p.is_locked,
    p.created_at,
    p.updated_at,
    p.edited_at,
    p.report_count,
    coalesce(cc.comment_count, 0) as comment_count,
    cat.slug as category_slug,
    cat.label as category_label,
    coalesce(rc.reaction_count, 0) as reaction_count,
    (coalesce(rc.reaction_count, 0) * 4
     + coalesce(cc.comment_count, 0) * 2
     + greatest(0, 72 - floor(extract(epoch from (now() - p.created_at)) / 3600)::integer)
    ) as trending_score,
    tf.user_id is not null as is_followed_topic,
    cp.updated_at as avatar_updated_at,
    coalesce(ma.media_json, '[]'::jsonb) as media,
    coalesce(rc.viewer_has_liked, false) as viewer_has_liked,
    p.repost_of_id,
    p.quote_post_id,
    p.repost_count,
    p.bookmark_count,
    vr.repost_of_id is not null as is_reposted_by_viewer,
    vb.post_id is not null as is_bookmarked_by_viewer
from public.community_posts p
join public.community_profiles cp on cp.id = p.author_id
left join public.community_categories cat on cat.id = p.category_id
left join reaction_counts rc on rc.subject_id = p.id
left join comment_counts cc on cc.post_id = p.id
left join topic_follows tf on tf.category_id = p.category_id
left join viewer_reposts vr on vr.repost_of_id = p.id
left join viewer_bookmarks vb on vb.post_id = p.id
left join media_agg ma on ma.post_id = p.id
where p.state in ('PUBLISHED', 'LOCKED') and p.deleted_at is null;

revoke all on public.community_post_feed from public, anon;
grant select on public.community_post_feed to anon, authenticated, service_role;

commit;
