begin;

-- Residents no longer require an authenticated account to read published Community content.
-- Keep every mutation path authenticated; this migration only exposes filtered public projections.

create or replace view public.community_post_feed
with (security_invoker = false, security_barrier = true)
as
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
  (
    select count(*)::integer
    from public.community_comments c
    where c.post_id = p.id
      and c.state = 'PUBLISHED'
      and c.deleted_at is null
  ) as comment_count,
  cat.slug as category_slug,
  cat.label as category_label,
  (
    select count(*)::integer
    from public.community_reactions r
    where r.subject_type = 'POST'
      and r.subject_id = p.id
      and r.reaction_type = 'LIKE'
  ) as reaction_count,
  (
    (select count(*)::integer * 4 from public.community_reactions r
      where r.subject_type = 'POST' and r.subject_id = p.id and r.reaction_type = 'LIKE')
    +
    (select count(*)::integer * 2 from public.community_comments c
      where c.post_id = p.id and c.state = 'PUBLISHED' and c.deleted_at is null)
    + greatest(0, 72 - floor(extract(epoch from now() - p.created_at) / 3600)::integer)
  ) as trending_score,
  exists(
    select 1 from public.community_topic_follows tf
    where tf.user_id = auth.uid() and tf.category_id = p.category_id
  ) as is_followed_topic,
  cp.updated_at as avatar_updated_at,
  coalesce((
    select jsonb_agg(
      jsonb_build_object(
        'id', m.id,
        'storage_path', m.storage_path,
        'media_kind', m.media_kind,
        'mime_type', m.mime_type,
        'byte_size', m.byte_size,
        'width', m.width,
        'height', m.height,
        'duration_seconds', m.duration_seconds,
        'position', m.position,
        'caption', m.caption
      ) order by m.position
    )
    from public.community_post_media m
    where m.post_id = p.id
  ), '[]'::jsonb) as media,
  exists(
    select 1 from public.community_reactions r
    where r.actor_id = auth.uid()
      and r.subject_type = 'POST'
      and r.subject_id = p.id
      and r.reaction_type = 'LIKE'
  ) as viewer_has_liked
from public.community_posts p
join public.community_profiles cp on cp.id = p.author_id
left join public.community_categories cat on cat.id = p.category_id
where p.state in ('PUBLISHED', 'LOCKED')
  and p.deleted_at is null
  and private.can_view_community_author(p.author_id);

create or replace view public.community_comment_feed
with (security_invoker = false, security_barrier = true)
as
select
  c.id,
  c.post_id,
  c.parent_comment_id,
  c.author_id,
  coalesce(cp.display_name, 'Community member') as author_name,
  coalesce(cp.handle, 'member_' || replace(left(c.author_id::text, 12), '-', '')) as author_handle,
  cp.avatar_path,
  coalesce(cp.staff_badge, false) as staff_badge,
  c.body,
  c.created_at,
  c.updated_at,
  c.edited_at,
  cp.updated_at as avatar_updated_at
from public.community_comments c
join public.community_posts p on p.id = c.post_id
left join public.community_profiles cp on cp.id = c.author_id
where c.state = 'PUBLISHED'
  and c.deleted_at is null
  and p.state in ('PUBLISHED', 'LOCKED')
  and p.deleted_at is null
  and private.can_view_community_author(p.author_id)
  and private.can_view_community_author(c.author_id);

revoke all on public.community_post_feed from anon;
revoke all on public.community_comment_feed from anon;
grant select on public.community_post_feed to anon;
grant select on public.community_comment_feed to anon;

-- The projection views are the public read boundary. Do not expose base Community tables to anon.
revoke all on public.community_posts from anon;
revoke all on public.community_profiles from anon;
revoke all on public.community_comments from anon;
revoke all on public.community_post_media from anon;
revoke all on public.community_reactions from anon;
revoke all on public.community_topic_follows from anon;

-- Published Community media may be resolved by anonymous readers, but uploads/deletes remain authenticated.
drop policy if exists community_storage_read_anonymous_public on storage.objects;
create policy community_storage_read_anonymous_public
on storage.objects
for select
to anon
using (
  bucket_id = 'rtc-community-media'
  and private.can_view_community_media_path(name)
);

commit;
