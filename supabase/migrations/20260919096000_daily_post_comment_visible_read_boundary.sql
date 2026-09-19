begin;

-- The client-facing count and comment list must use the same visibility contract.
-- HIDDEN and DELETED comments are moderation/audit records, not visible comments.
create or replace function public.daily_post_comments_page_v1(
  p_post_id uuid,
  p_after_created_at timestamptz default null,
  p_after_id uuid default null,
  p_limit integer default 100
)
returns setof public.daily_post_comments
language sql
stable
set search_path = public
as $$
  select c.*
    from public.daily_post_comments c
    join public.daily_posts p on p.id = c.post_id
   where c.post_id = p_post_id
     and p.state = 'PUBLISHED'
     and c.state = 'VISIBLE'
     and (
       p_after_created_at is null
       or (c.created_at, c.id) > (
            p_after_created_at,
            coalesce(p_after_id, '00000000-0000-0000-0000-000000000000'::uuid)
          )
     )
   order by c.created_at, c.id
   limit least(greatest(coalesce(p_limit, 100), 1), 200);
$$;

create or replace function public.daily_post_comments_page_v2(
  p_post_id uuid,
  p_before_created_at timestamptz default null,
  p_before_id uuid default null,
  p_limit integer default 20
)
returns setof public.daily_post_comments
language sql
stable
set search_path = public
as $$
  select c.*
    from public.daily_post_comments c
    join public.daily_posts p on p.id = c.post_id
   where c.post_id = p_post_id
     and p.state = 'PUBLISHED'
     and c.state = 'VISIBLE'
     and (
       p_before_created_at is null
       or (c.created_at, c.id) < (
            p_before_created_at,
            coalesce(p_before_id, 'ffffffff-ffff-ffff-ffff-ffffffffffff'::uuid)
          )
     )
   order by c.created_at desc, c.id desc
   limit least(greatest(coalesce(p_limit, 20), 1), 50);
$$;

commit;
