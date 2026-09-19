-- Owner-only community post lifecycle.
-- Edit uses the existing one-hour product rule. Delete is a true hard delete;
-- all post-owned rows are removed by the existing ON DELETE CASCADE constraints.

create or replace function public.edit_community_post(
  p_post_id uuid,
  p_body text,
  p_category_slug text,
  p_external_url text default null
) returns void
language plpgsql
security definer
set search_path = public, pg_temp
as $function$
declare
  category_uuid uuid;
  clean_body text := trim(coalesce(p_body, ''));
begin
  if auth.uid() is null then raise exception 'AUTH_REQUIRED'; end if;
  if char_length(clean_body) not between 1 and 280 and p_external_url is null then
    raise exception 'INVALID_POST_CONTENT';
  end if;
  if p_external_url is not null and p_external_url !~ '^https://' then
    raise exception 'INVALID_LINK';
  end if;

  -- The feed projection exposes the category label, while callers may also
  -- have the canonical slug. Accept both representations without weakening
  -- the active-category boundary.
  select id into category_uuid
  from public.community_categories
  where active and (slug = p_category_slug or label = p_category_slug)
  order by (slug = p_category_slug) desc
  limit 1;
  if category_uuid is null then raise exception 'INVALID_CATEGORY'; end if;

  update public.community_posts
  set body = clean_body,
      category_id = category_uuid,
      external_url = p_external_url,
      edited_at = now(),
      updated_at = now()
  where id = p_post_id
    and author_id = auth.uid()
    and state in ('PUBLISHED', 'LOCKED')
    and deleted_at is null
    and created_at >= now() - interval '1 hour';
  if not found then raise exception 'EDIT_NOT_ALLOWED'; end if;

  insert into public.audit_events(actor_id, event_type, entity_type, entity_id, result, metadata, source)
  values (auth.uid(), 'COMMUNITY_POST_EDITED', 'community_post', p_post_id, 'SUCCESS',
          jsonb_build_object('bodyLength', char_length(clean_body)), 'community-rpc');
end;
$function$;

revoke all on function public.edit_community_post(uuid, text, text, text) from public, anon;
grant execute on function public.edit_community_post(uuid, text, text, text) to authenticated, service_role;

create or replace function public.delete_community_post(p_post_id uuid)
returns void
language plpgsql
security definer
set search_path = public, pg_temp
as $function$
begin
  if auth.uid() is null then raise exception 'AUTH_REQUIRED'; end if;

  -- The post foreign keys are cascade-backed for comments, media, reactions,
  -- bookmarks, reposts, notifications, polls, hashtags, and analytics rows.
  -- This removes the base record, so every public and user-specific feed
  -- projection can no longer return it. The owner check is server-authoritative.
  delete from public.community_posts
  where id = p_post_id
    and author_id = auth.uid()
    and deleted_at is null;
  if not found then raise exception 'DELETE_NOT_ALLOWED'; end if;

  insert into public.audit_events(actor_id, event_type, entity_type, entity_id, result, metadata, source)
  values (auth.uid(), 'COMMUNITY_POST_DELETED', 'community_post', p_post_id, 'SUCCESS',
          jsonb_build_object('hardDelete', true), 'community-rpc');
end;
$function$;

revoke all on function public.delete_community_post(uuid) from public, anon;
grant execute on function public.delete_community_post(uuid) to authenticated, service_role;

comment on function public.edit_community_post(uuid, text, text, text) is
  'Owner-only edit of a published community post within one hour; accepts category slug or label.';
comment on function public.delete_community_post(uuid) is
  'Owner-only hard deletion of a community post and its cascade-owned rows.';

-- Contract checks are intentionally source-controlled alongside the migration.
-- Production verification must confirm the function definitions and grants after apply.
