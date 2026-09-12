-- Migration: Bidirectional Keyset Pagination v3
-- Resolves Gap 2.5A

begin;

create or replace function public.community_post_page_v3(
    p_before_created_at timestamp with time zone default null,
    p_before_id uuid default null,
    p_after_created_at timestamp with time zone default null,
    p_after_id uuid default null,
    p_limit integer default 20
)
returns table (
    id uuid,
    author_id uuid,
    author_name text,
    author_handle text,
    avatar_path text,
    staff_badge text,
    body text,
    state text,
    is_locked boolean,
    created_at timestamptz,
    updated_at timestamptz,
    edited_at timestamptz,
    report_count integer,
    comment_count integer,
    category_slug text,
    category_label text,
    reaction_count integer,
    trending_score integer,
    is_followed_topic boolean,
    avatar_updated_at timestamptz,
    media jsonb,
    viewer_has_liked boolean,
    repost_of_id uuid,
    quote_post_id uuid,
    repost_count integer,
    bookmark_count integer,
    is_reposted_by_viewer boolean,
    is_bookmarked_by_viewer boolean,
    has_newer_posts boolean
)
language sql
stable
security invoker
set search_path = public, pg_temp
as $$
    with page_query as (
        select feed.*
        from public.community_post_feed as feed
        where
            -- Forward pagination (older posts)
            (p_before_created_at is null and p_before_id is null and p_after_created_at is null and p_after_id is null)
            or (
                p_before_created_at is not null
                and p_before_id is not null
                and (feed.created_at, feed.id) < (p_before_created_at, p_before_id)
            )
            -- Backward pagination (newer posts)
            or (
                p_after_created_at is not null
                and p_after_id is not null
                and (feed.created_at, feed.id) > (p_after_created_at, p_after_id)
            )
        order by
            case when p_after_created_at is not null then feed.created_at end asc,
            case when p_after_created_at is not null then feed.id end asc,
            case when p_after_created_at is null then feed.created_at end desc,
            case when p_after_created_at is null then feed.id end desc
        limit greatest(1, least(coalesce(p_limit, 20), 50))
    ),
    latest_check as (
        select exists (
            select 1
            from public.community_posts p
            where p.state in ('PUBLISHED', 'LOCKED')
              and p.deleted_at is null
              and (
                  p_before_created_at is not null
                  and (p.created_at, p.id) > (p_before_created_at, coalesce(p_before_id, '00000000-0000-0000-0000-000000000000'::uuid))
              )
        ) as has_newer
    )
    select
        pq.id,
        pq.author_id,
        pq.author_name,
        pq.author_handle,
        pq.avatar_path,
        pq.staff_badge,
        pq.body,
        pq.state,
        pq.is_locked,
        pq.created_at,
        pq.updated_at,
        pq.edited_at,
        pq.report_count,
        pq.comment_count,
        pq.category_slug,
        pq.category_label,
        pq.reaction_count,
        pq.trending_score,
        pq.is_followed_topic,
        pq.avatar_updated_at,
        pq.media,
        pq.viewer_has_liked,
        pq.repost_of_id,
        pq.quote_post_id,
        pq.repost_count,
        pq.bookmark_count,
        pq.is_reposted_by_viewer,
        pq.is_bookmarked_by_viewer,
        lc.has_newer as has_newer_posts
    from page_query pq
    cross join latest_check lc
    order by pq.created_at desc, pq.id desc;
$$;

revoke all on function public.community_post_page_v3(timestamp with time zone, uuid, timestamp with time zone, uuid, integer)
  from public, anon;
grant execute on function public.community_post_page_v3(timestamp with time zone, uuid, timestamp with time zone, uuid, integer)
  to authenticated;

commit;
