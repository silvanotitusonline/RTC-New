-- Migration: Replace N+1 correlated subqueries with JOIN-based aggregation & Add Missing Indexes
-- Resolves Gap 1.1A, Gap 1.1B, and Gap 1.1C

begin;

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
    coalesce(rc.viewer_has_liked, false) as viewer_has_liked
from public.community_posts p
join public.community_profiles cp on cp.id = p.author_id
left join public.community_categories cat on cat.id = p.category_id
left join reaction_counts rc on rc.subject_id = p.id
left join comment_counts cc on cc.post_id = p.id
left join topic_follows tf on tf.category_id = p.category_id
left join media_agg ma on ma.post_id = p.id
where p.state in ('PUBLISHED', 'LOCKED') and p.deleted_at is null;

revoke all on public.community_post_feed from public, anon;
grant select on public.community_post_feed to anon, authenticated, service_role;

-- Missing indexes for the JOIN-based aggregation (Gap 1.1A)
create index if not exists idx_community_reactions_subject
    on public.community_reactions(subject_type, subject_id);

create index if not exists idx_community_comments_published_post
    on public.community_comments(post_id, created_at)
    where state = 'PUBLISHED' and deleted_at is null;

create index if not exists idx_community_topic_follows_user_cat
    on public.community_topic_follows(user_id, category_id);

create index if not exists idx_community_post_media_post_agg
    on public.community_post_media(post_id, position);

-- Gap 1.1B: Index for nested comment queries
create index if not exists idx_community_comments_parent_id
    on public.community_comments(parent_comment_id, created_at)
    where parent_comment_id is not null and deleted_at is null;

-- Gap 1.1C: Covering and composite indexes for feed queries
create index if not exists idx_community_posts_author_created
    on public.community_posts(author_id, created_at desc)
    where deleted_at is null and state in ('PUBLISHED', 'LOCKED');

create index if not exists idx_community_posts_category_created
    on public.community_posts(category_id, created_at desc)
    where deleted_at is null and state in ('PUBLISHED', 'LOCKED');

create index if not exists idx_community_posts_covering_cursor
    on public.community_posts(created_at desc, id desc, state, author_id, category_id)
    where deleted_at is null and state in ('PUBLISHED', 'LOCKED');

commit;
