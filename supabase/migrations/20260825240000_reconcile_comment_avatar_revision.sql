-- Reconstructed non-production baseline reconciliation.
--
-- Source recovery note (2026-08-26): the isolated non-production community_comment_feed
-- projection exposes avatar_path but not the community profile revision timestamp. Android
-- comment avatars therefore cannot reliably invalidate a cached image immediately after a
-- profile photo change, unlike post avatars.
--
-- This source-only migration adds the existing profile revision field to the view contract.
-- It is not applied to the approved isolated project in this checkpoint. Do not change grants,
-- RLS policies, Storage policies, or profile data as part of this narrow projection repair.

begin;

create or replace view public.community_comment_feed
with (security_invoker = true)
as
select
  c.id,
  c.post_id,
  c.parent_comment_id,
  c.author_id,
  coalesce(cp.display_name, 'Community member') as author_name,
  coalesce(cp.handle, 'member_' || replace(left(c.author_id::text, 12), '-', '')) as author_handle,
  cp.avatar_path,
  cp.updated_at as avatar_updated_at,
  coalesce(cp.staff_badge, false) as staff_badge,
  c.body,
  c.created_at,
  c.updated_at,
  c.edited_at
from public.community_comments c
left join public.community_profiles cp on cp.id = c.author_id
where c.state = 'PUBLISHED' and c.deleted_at is null;

commit;
