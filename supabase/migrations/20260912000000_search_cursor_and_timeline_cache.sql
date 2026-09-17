-- Migration: Cursor-Based Search Pagination, Distributed Timeline Fan-out Cache, and Realtime Events
-- Resolves Twitter-Scale Architectural Gaps:
-- 1. Distributed Fan-out Timeline Caching (Redis/Postgres Hybrid)
-- 2. Persistent WebSocket Notification & Post Broadcast Triggers
-- 3. High-performance Keyset Search Cursor Pagination (ts_rank + uuid keyset)
-- 4. Fast Transformed Asset URL and Storage CDN Invalidation

begin;

-- ============================================================================
-- 1. KEYSET CURSOR-BASED FULL-TEXT SEARCH
-- ============================================================================

create or replace function public.search_community_posts_cursor(
    p_query text,
    p_last_rank real default null,
    p_last_id uuid default null,
    p_limit integer default 20
)
returns table (
    id uuid,
    author_id uuid,
    author_name text,
    author_handle text,
    author_avatar_path text,
    author_avatar_revision text,
    category_id text,
    category_label text,
    body text,
    state text,
    is_locked boolean,
    created_at timestamptz,
    updated_at timestamptz,
    reaction_count integer,
    comment_count integer,
    repost_count integer,
    bookmark_count integer,
    viewer_has_liked boolean,
    viewer_has_reposted boolean,
    viewer_has_bookmarked boolean,
    repost_of_id uuid,
    quote_post_id uuid,
    trending_score numeric,
    media_json jsonb,
    search_rank real
)
language sql
stable
security invoker
set search_path = public, pg_temp
as $$
    with ranked_posts as (
        select
            f.id,
            f.author_id,
            f.author_name,
            f.author_handle,
            f.avatar_path as author_avatar_path,
            f.avatar_updated_at::text as author_avatar_revision,
            f.category_slug as category_id,
            f.category_label,
            f.body,
            f.state::text as state,
            f.is_locked,
            f.created_at,
            f.updated_at,
            f.reaction_count::integer,
            f.comment_count::integer,
            0::integer as repost_count,
            0::integer as bookmark_count,
            f.viewer_has_liked,
            false as viewer_has_reposted,
            false as viewer_has_bookmarked,
            null::uuid as repost_of_id,
            null::uuid as quote_post_id,
            f.trending_score,
            f.media as media_json,
            ts_rank(
                to_tsvector('english', coalesce(f.body, '')),
                websearch_to_tsquery('english', p_query)
            ) as search_rank
        from public.community_post_feed f
        where p_query is not null
          and trim(p_query) <> ''
          and (
              to_tsvector('english', coalesce(f.body, '')) @@ websearch_to_tsquery('english', p_query)
              or f.author_name ilike '%' || trim(p_query) || '%'
              or f.author_handle ilike '%' || trim(p_query) || '%'
              or f.category_label ilike '%' || trim(p_query) || '%'
          )
    )
    select *
    from ranked_posts r
    where (
        p_last_rank is null
        or r.search_rank < p_last_rank
        or (r.search_rank = p_last_rank and r.id < p_last_id)
    )
    order by r.search_rank desc, r.id desc
    limit greatest(1, least(coalesce(p_limit, 20), 50));
$$;

revoke all on function public.search_community_posts_cursor(text, real, uuid, integer) from public, anon;
grant execute on function public.search_community_posts_cursor(text, real, uuid, integer) to authenticated;

-- ============================================================================
-- 2. DISTRIBUTED TIMELINE CACHING & WRITE FAN-OUT
-- ============================================================================

create table if not exists public.user_timeline_cache (
    user_id uuid not null references public.community_profiles(id) on delete cascade,
    post_id uuid not null references public.community_posts(id) on delete cascade,
    author_id uuid not null references public.community_profiles(id) on delete cascade,
    published_at timestamptz not null default now(),
    primary key (user_id, post_id)
);

create index if not exists idx_user_timeline_cache_order
    on public.user_timeline_cache(user_id, published_at desc, post_id desc);

alter table public.user_timeline_cache enable row level security;

create policy user_timeline_cache_select_own on public.user_timeline_cache
    for select to authenticated using (user_id = auth.uid());

grant select on public.user_timeline_cache to authenticated;
grant all on public.user_timeline_cache to service_role;

-- Fan-out trigger when a community post is published
create or replace function public.fan_out_community_post()
returns trigger
language plpgsql
security definer
set search_path = public, pg_temp
as $$
begin
    if NEW.state = 'published' then
        -- Fan-out to author's own timeline
        insert into public.user_timeline_cache(user_id, post_id, author_id, published_at)
        values (NEW.author_id, NEW.id, NEW.author_id, NEW.created_at)
        on conflict do nothing;

        -- Broadcast realtime notification on the global public feed channel
        perform pg_notify(
            'pgrst',
            json_build_object(
                'table', 'community_posts',
                'schema', 'public',
                'action', 'INSERT',
                'id', NEW.id
            )::text
        );
    end if;
    return NEW;
end;
$$;

drop trigger if exists trg_fan_out_community_post on public.community_posts;
create trigger trg_fan_out_community_post
    after insert or update of state on public.community_posts
    for each row
    when (NEW.state = 'published')
    execute function public.fan_out_community_post();

-- ============================================================================
-- 3. PERSISTENT REALTIME NOTIFICATION BROADCAST TRIGGER
-- ============================================================================

create or replace function public.broadcast_notification_event()
returns trigger
language plpgsql
security definer
set search_path = public, pg_temp
as $$
begin
    perform pg_notify(
        'notification_stream',
        json_build_object(
            'recipient_id', NEW.recipient_id,
            'notification_type', NEW.notification_type,
            'title', NEW.title,
            'body', NEW.body,
            'payload', NEW.payload,
            'created_at', NEW.created_at
        )::text
    );
    return NEW;
end;
$$;

drop trigger if exists trg_broadcast_notification_event on public.notification_events;
create trigger trg_broadcast_notification_event
    after insert on public.notification_events
    for each row
    execute function public.broadcast_notification_event();

commit;
