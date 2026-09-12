-- RTC Community production-readiness reconciliation.
-- Keeps Community feed semantics truthful and makes upload finalization retry-safe.

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
  (select count(*)::integer from public.community_comments c where c.post_id=p.id and c.state='PUBLISHED' and c.deleted_at is null) as comment_count,
  cat.slug as category_slug,
  cat.label as category_label,
  (select count(*)::integer from public.community_reactions r where r.subject_type='POST' and r.subject_id=p.id) as reaction_count,
  ((select count(*)::integer * 4 from public.community_reactions r where r.subject_type='POST' and r.subject_id=p.id)
    + (select count(*)::integer * 2 from public.community_comments c where c.post_id=p.id and c.state='PUBLISHED' and c.deleted_at is null)
    + greatest(0, 72 - floor(extract(epoch from (now() - p.created_at)) / 3600)::integer)) as trending_score,
  exists(
    select 1 from public.community_topic_follows tf
    where tf.user_id=(select auth.uid()) and tf.category_id=p.category_id
  ) as is_followed_topic,
  cp.updated_at as avatar_updated_at,
  coalesce((
    select jsonb_agg(
      jsonb_build_object(
        'id',m.id,
        'storage_path',m.storage_path,
        'media_kind',m.media_kind,
        'mime_type',m.mime_type,
        'byte_size',m.byte_size,
        'width',m.width,
        'height',m.height,
        'duration_seconds',m.duration_seconds,
        'position',m.position,
        'caption',m.caption
      ) order by m.position
    )
    from public.community_post_media m
    where m.post_id=p.id
  ), '[]'::jsonb) as media
from public.community_posts p
join public.community_profiles cp on cp.id=p.author_id
left join public.community_categories cat on cat.id=p.category_id
where p.state in ('PUBLISHED','LOCKED') and p.deleted_at is null;

revoke all on public.community_post_feed from public, anon, authenticated;
grant select on public.community_post_feed to anon, authenticated, service_role;

create or replace function public.finalize_community_post(
  p_post_id uuid,
  p_media jsonb default '[]'::jsonb,
  p_hashtags text[] default '{}'::text[]
) returns uuid
language plpgsql
security definer
set search_path to 'public','pg_temp'
as $function$
declare
  media_item jsonb;
  media_count integer := 0;
  object_bytes bigint;
  object_mime text;
  tag_value text;
  tag_id uuid;
  current_state text;
begin
  if auth.uid() is null then raise exception 'AUTH_REQUIRED'; end if;
  if not private.community_guidelines_accepted() then raise exception 'GUIDELINES_NOT_ACCEPTED'; end if;

  select state into current_state from public.community_posts where id=p_post_id and author_id=auth.uid() and deleted_at is null;
  if current_state in ('PUBLISHED','LOCKED') then return p_post_id; end if;
  if current_state is distinct from 'DRAFT' then raise exception 'DRAFT_UNAVAILABLE'; end if;

  if jsonb_typeof(p_media)<>'array' or jsonb_array_length(p_media)>10 then raise exception 'INVALID_MEDIA'; end if;
  if coalesce(array_length(p_hashtags,1),0)>10 then raise exception 'TOO_MANY_HASHTAGS'; end if;

  for media_item in select value from jsonb_array_elements(p_media) loop
    media_count := media_count + 1;
    select (metadata->>'size')::bigint, metadata->>'mimetype' into object_bytes,object_mime
      from storage.objects where bucket_id='rtc-community-media' and name=media_item->>'path';
    if object_bytes is null or object_mime is null then raise exception 'MEDIA_NOT_FOUND'; end if;
    if (storage.foldername(media_item->>'path'))[1]<>auth.uid()::text or (storage.foldername(media_item->>'path'))[2]<>p_post_id::text then raise exception 'MEDIA_OWNER_MISMATCH'; end if;
    if object_mime not in ('image/jpeg','image/png','image/webp','video/mp4','video/webm') then raise exception 'MEDIA_TYPE_NOT_ALLOWED'; end if;
    if object_bytes>20971520 or (object_mime like 'image/%' and object_bytes>5242880) then raise exception 'MEDIA_TOO_LARGE'; end if;
    if (media_item->>'mediaKind')='VIDEO' and coalesce((media_item->>'durationSeconds')::integer,0) not between 1 and 180 then raise exception 'INVALID_VIDEO_DURATION'; end if;
    insert into public.community_post_media(post_id,storage_path,media_kind,mime_type,byte_size,width,height,duration_seconds,position,uploaded_by)
    values(p_post_id,media_item->>'path',media_item->>'mediaKind',object_mime,object_bytes,nullif(media_item->>'width','')::integer,nullif(media_item->>'height','')::integer,nullif(media_item->>'durationSeconds','')::integer,media_count,auth.uid());
  end loop;

  foreach tag_value in array coalesce(p_hashtags,'{}'::text[]) loop
    tag_value:=lower(regexp_replace(tag_value,'^#',''));
    if tag_value!~'^[a-z0-9_]{2,50}$' then raise exception 'INVALID_HASHTAG'; end if;
    insert into public.community_hashtags(tag) values(tag_value) on conflict(tag) do update set tag=excluded.tag returning id into tag_id;
    insert into public.community_post_hashtags(post_id,hashtag_id) values(p_post_id,tag_id) on conflict do nothing;
  end loop;

  update public.community_posts set state='PUBLISHED' where id=p_post_id and author_id=auth.uid() and state='DRAFT';
  insert into public.audit_events(actor_id,event_type,entity_type,entity_id,result,metadata,source)
  values(auth.uid(),'COMMUNITY_POST_PUBLISHED','community_post',p_post_id,'SUCCESS',jsonb_build_object('mediaCount',media_count),'community-rpc');
  return p_post_id;
end
$function$;

revoke all on function public.finalize_community_post(uuid,jsonb,text[]) from public,anon;
grant execute on function public.finalize_community_post(uuid,jsonb,text[]) to authenticated,service_role;
