-- Migration: Full-Text Search and Autocomplete
-- Resolves Gap 2.4A

begin;

-- Add generated tsvector column to community_posts
alter table public.community_posts
    add column if not exists search_vector tsvector
    generated always as (to_tsvector('english', coalesce(body, ''))) stored;

create index if not exists idx_community_posts_search_vector
    on public.community_posts using gin(search_vector);

-- Add generated tsvector column to community_comments
alter table public.community_comments
    add column if not exists search_vector tsvector
    generated always as (to_tsvector('english', coalesce(body, ''))) stored;

create index if not exists idx_community_comments_search_vector
    on public.community_comments using gin(search_vector);

-- Search posts RPC function
create or replace function public.search_community_posts(
    p_query text,
    p_limit integer default 20,
    p_offset integer default 0
)
returns setof public.community_post_feed
language sql
stable
security invoker
set search_path = public, pg_temp
as $$
    select *
    from public.community_post_feed
    where p_query is not null
      and trim(p_query) <> ''
      and (
          to_tsvector('english', coalesce(body, '')) @@ websearch_to_tsquery('english', p_query)
          or author_name ilike '%' || trim(p_query) || '%'
          or author_handle ilike '%' || trim(p_query) || '%'
          or category_label ilike '%' || trim(p_query) || '%'
          or body ilike '%' || trim(p_query) || '%'
      )
    order by created_at desc
    limit greatest(1, least(coalesce(p_limit, 20), 50))
    offset greatest(0, coalesce(p_offset, 0));
$$;

revoke all on function public.search_community_posts(text, integer, integer) from public, anon;
grant execute on function public.search_community_posts(text, integer, integer) to authenticated;

-- Autocomplete hashtags RPC function
create or replace function public.autocomplete_hashtags(
    p_prefix text,
    p_limit integer default 10
)
returns table(tag text, post_count bigint)
language sql
stable
security invoker
set search_path = public, pg_temp
as $$
    select h.tag, count(ph.post_id)::bigint as post_count
    from public.community_hashtags h
    left join public.community_post_hashtags ph on ph.hashtag_id = h.id
    where h.tag ilike trim(leading '#' from p_prefix) || '%'
    group by h.id, h.tag
    order by post_count desc, h.tag asc
    limit greatest(1, least(coalesce(p_limit, 10), 30));
$$;

revoke all on function public.autocomplete_hashtags(text, integer) from public, anon;
grant execute on function public.autocomplete_hashtags(text, integer) to authenticated;

-- Autocomplete user mentions RPC function
create or replace function public.autocomplete_mentions(
    p_prefix text,
    p_limit integer default 10
)
returns table(id uuid, handle text, display_name text, avatar_path text)
language sql
stable
security invoker
set search_path = public, pg_temp
as $$
    select p.id, p.handle, p.display_name, p.avatar_path
    from public.community_profiles p
    where p.handle ilike trim(leading '@' from p_prefix) || '%'
       or p.display_name ilike p_prefix || '%'
    order by p.display_name asc
    limit greatest(1, least(coalesce(p_limit, 10), 30));
$$;

revoke all on function public.autocomplete_mentions(text, integer) from public, anon;
grant execute on function public.autocomplete_mentions(text, integer) to authenticated;

commit;
