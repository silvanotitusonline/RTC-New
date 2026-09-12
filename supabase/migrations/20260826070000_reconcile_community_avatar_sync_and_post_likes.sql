-- Reconciles the isolated profile-to-Community projection trigger into source control and
-- adds the first genuine, server-authorized Community interaction: a post LIKE.
-- This migration intentionally leaves direct authenticated mutation of community_reactions unavailable.

begin;

-- The earlier insert-only trigger is superseded by a single after-write trigger that keeps the
-- derived Community identity synchronized when the canonical profile display name or avatar changes.
drop trigger if exists profiles_community_profile_after_insert on public.profiles;
drop trigger if exists profiles_ensure_community_profile_after_write on public.profiles;

create or replace function private.ensure_community_profile_after_profile_change()
returns trigger
language plpgsql
security definer
set search_path = ''
as $function$
begin
  perform private.ensure_community_profile_for_account(new.id);
  return new;
end;
$function$;

revoke all on function private.ensure_community_profile_after_profile_change() from public, anon, authenticated;

create trigger profiles_ensure_community_profile_after_write
  after insert or update of display_name, avatar_url on public.profiles
  for each row execute function private.ensure_community_profile_after_profile_change();

create or replace function public.toggle_community_post_like(p_post_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = public, pg_temp
as $function$
declare
  v_actor_id uuid := auth.uid();
  v_existing_reaction text;
  v_like_count integer;
  v_liked boolean;
begin
  if v_actor_id is null then
    raise exception 'AUTH_REQUIRED';
  end if;

  perform private.ensure_community_profile_for_account(v_actor_id);
  if not private.community_guidelines_accepted() then
    raise exception 'GUIDELINES_NOT_ACCEPTED';
  end if;

  if not exists (
    select 1
      from public.community_posts p
     where p.id = p_post_id
       and p.state in ('PUBLISHED', 'LOCKED')
       and p.deleted_at is null
       and private.can_view_community_author(p.author_id)
  ) then
    raise exception 'POST_NOT_AVAILABLE';
  end if;

  -- Serialize each actor/post pair so repeated taps or parallel sessions cannot invert the result.
  perform pg_advisory_xact_lock(hashtext(v_actor_id::text), hashtext(p_post_id::text));

  select r.reaction_type
    into v_existing_reaction
    from public.community_reactions r
   where r.actor_id = v_actor_id
     and r.subject_type = 'POST'
     and r.subject_id = p_post_id
   for update;

  if v_existing_reaction = 'LIKE' then
    delete from public.community_reactions
     where actor_id = v_actor_id
       and subject_type = 'POST'
       and subject_id = p_post_id;
    v_liked := false;
  else
    insert into public.community_reactions(actor_id, subject_type, subject_id, reaction_type)
    values (v_actor_id, 'POST', p_post_id, 'LIKE')
    on conflict (actor_id, subject_type, subject_id) do update
      set reaction_type = excluded.reaction_type,
          updated_at = now();
    v_liked := true;
  end if;

  select count(*)::integer
    into v_like_count
    from public.community_reactions r
   where r.subject_type = 'POST'
     and r.subject_id = p_post_id
     and r.reaction_type = 'LIKE';

  return jsonb_build_object('liked', v_liked, 'like_count', v_like_count);
end;
$function$;

revoke all on function public.toggle_community_post_like(uuid) from public, anon;
grant execute on function public.toggle_community_post_like(uuid) to authenticated;

create or replace view public.community_post_feed
with (security_invoker = true)
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
  (select count(*)::integer from public.community_comments c where c.post_id = p.id and c.state = 'PUBLISHED' and c.deleted_at is null) as comment_count,
  cat.slug as category_slug,
  cat.label as category_label,
  (select count(*)::integer from public.community_reactions r where r.subject_type = 'POST' and r.subject_id = p.id and r.reaction_type = 'LIKE') as reaction_count,
  ((select count(*)::integer * 4 from public.community_reactions r where r.subject_type = 'POST' and r.subject_id = p.id and r.reaction_type = 'LIKE')
    + (select count(*)::integer * 2 from public.community_comments c where c.post_id = p.id and c.state = 'PUBLISHED' and c.deleted_at is null)
    + greatest(0, 72 - floor(extract(epoch from (now() - p.created_at)) / 3600)::integer)) as trending_score,
  exists (select 1 from public.community_topic_follows tf where tf.user_id = auth.uid() and tf.category_id = p.category_id) as is_followed_topic,
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
  exists (select 1 from public.community_reactions r where r.actor_id = auth.uid() and r.subject_type = 'POST' and r.subject_id = p.id and r.reaction_type = 'LIKE') as viewer_has_liked
from public.community_posts p
join public.community_profiles cp on cp.id = p.author_id
left join public.community_categories cat on cat.id = p.category_id
where p.state in ('PUBLISHED', 'LOCKED')
  and p.deleted_at is null;

revoke all on public.community_post_feed from public, anon, authenticated;
grant select on public.community_post_feed to authenticated;

-- The isolated history predates the source comment-avatar revision migration. Reconcile it here
-- so existing comment rows receive the same signed-URL cache invalidation as feed posts.
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
  coalesce(cp.staff_badge, false) as staff_badge,
  c.body,
  c.created_at,
  c.updated_at,
  c.edited_at,
  cp.updated_at as avatar_updated_at
from public.community_comments c
left join public.community_profiles cp on cp.id = c.author_id
where c.state = 'PUBLISHED' and c.deleted_at is null;

revoke all on public.community_comment_feed from public, anon, authenticated;
grant select on public.community_comment_feed to authenticated;

commit;
