begin;

-- Community pagination remains caller-scoped: this function is SECURITY INVOKER and reads the
-- existing security-invoker projection. It grants no direct table access and is intentionally
-- source-only in this branch; deployment is owned by the integration/release process.
create or replace function public.community_post_page_v2(
  p_before_created_at timestamp with time zone default null,
  p_before_id uuid default null,
  p_limit integer default 20
)
returns setof public.community_post_feed
language sql
stable
security invoker
set search_path = public, pg_temp
as $function$
  select feed.*
  from public.community_post_feed as feed
  where
    (p_before_created_at is null and p_before_id is null)
    or (
      p_before_created_at is not null
      and p_before_id is not null
      and (feed.created_at, feed.id) < (p_before_created_at, p_before_id)
    )
  order by feed.created_at desc, feed.id desc
  limit greatest(1, least(coalesce(p_limit, 20), 50));
$function$;

revoke all on function public.community_post_page_v2(timestamp with time zone, uuid, integer)
  from public, anon;
grant execute on function public.community_post_page_v2(timestamp with time zone, uuid, integer)
  to authenticated;

comment on function public.community_post_page_v2(timestamp with time zone, uuid, integer) is
  'Authenticated, RLS-respecting composite-keyset page for the Community feed.';

-- The feed projection filters deleted/non-public posts. This partial index supplies the matching
-- deterministic created_at/id access path without changing unrelated Community indexes.
create index if not exists community_posts_public_cursor_idx
  on public.community_posts (created_at desc, id desc)
  where deleted_at is null and state in ('PUBLISHED', 'LOCKED');

commit;
