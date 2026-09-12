-- Production-beta Community controls.
-- The Supabase CLI is not available in this execution environment, so this migration was
-- created directly using the repository's timestamped migration convention.

begin;

create or replace function private.ensure_community_profile_for_account(p_user_id uuid)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_display_name text;
  v_avatar_path text;
begin
  if p_user_id is null then
    return;
  end if;

  if not exists (select 1 from auth.users u where u.id = p_user_id) then
    raise exception 'AUTH_ACCOUNT_NOT_FOUND';
  end if;

  insert into public.profiles (id, display_name)
  values (p_user_id, 'Community member')
  on conflict (id) do nothing;

  select p.display_name, p.avatar_url
    into v_display_name, v_avatar_path
    from public.profiles p
   where p.id = p_user_id;

  if not found then
    raise exception 'PROFILE_NOT_READY';
  end if;

  insert into public.community_profiles (
    id,
    display_name,
    handle,
    avatar_path,
    bio,
    guidelines_version,
    staff_badge
  )
  values (
    p_user_id,
    left(coalesce(nullif(btrim(v_display_name), ''), 'Community member'), 80),
    'member_' || replace(left(p_user_id::text, 20), '-', ''),
    v_avatar_path,
    '',
    0,
    false
  )
  on conflict (id) do update
    set display_name = excluded.display_name,
        avatar_path = excluded.avatar_path,
        updated_at = now();
end;
$$;

revoke all on function private.ensure_community_profile_for_account(uuid) from public, anon, authenticated;

create or replace function public.accept_community_guidelines_v2()
returns boolean
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_actor uuid := auth.uid();
begin
  if v_actor is null then
    raise exception 'AUTH_REQUIRED';
  end if;

  perform private.ensure_community_profile_for_account(v_actor);

  update public.community_profiles
     set guidelines_version = private.current_community_guidelines_version(),
         guidelines_accepted_at = now(),
         updated_at = now()
   where id = v_actor;

  if not found then
    raise exception 'COMMUNITY_PROFILE_NOT_READY';
  end if;

  return true;
end;
$$;

create or replace function public.accept_community_guidelines()
returns void
language plpgsql
security definer
set search_path = ''
as $$
begin
  perform public.accept_community_guidelines_v2();
end;
$$;

revoke all on function public.accept_community_guidelines_v2() from public, anon;
revoke all on function public.accept_community_guidelines() from public, anon;
grant execute on function public.accept_community_guidelines_v2() to authenticated;
grant execute on function public.accept_community_guidelines() to authenticated;

create or replace function public.moderate_community_comment_v1(
  p_comment_id uuid,
  p_reason text
)
returns boolean
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_actor uuid := auth.uid();
  v_reason text := btrim(coalesce(p_reason, ''));
  v_previous_state text;
begin
  if v_actor is null then
    raise exception 'AUTH_REQUIRED';
  end if;
  if not private.community_staff() then
    raise exception 'MODERATOR_REQUIRED';
  end if;
  if char_length(v_reason) not between 3 and 1000 then
    raise exception 'MODERATION_REASON_REQUIRED';
  end if;

  select c.state
    into v_previous_state
    from public.community_comments c
   where c.id = p_comment_id
   for update;

  if not found then
    raise exception 'COMMENT_UNAVAILABLE';
  end if;
  if v_previous_state = 'HIDDEN_BY_MODERATION' then
    return true;
  end if;
  if v_previous_state <> 'PUBLISHED' then
    raise exception 'COMMENT_UNAVAILABLE';
  end if;

  update public.community_comments
     set state = 'HIDDEN_BY_MODERATION',
         updated_at = now()
   where id = p_comment_id;

  insert into public.audit_events (
    actor_id,
    event_type,
    entity_type,
    entity_id,
    result,
    metadata,
    source
  )
  values (
    v_actor,
    'COMMUNITY_MODERATION_HIDE',
    'community_comment',
    p_comment_id,
    'SUCCESS',
    jsonb_build_object('reason', v_reason, 'previous_state', v_previous_state),
    'community-rpc'
  );

  return true;
end;
$$;

revoke all on function public.moderate_community_comment_v1(uuid, text) from public, anon;
grant execute on function public.moderate_community_comment_v1(uuid, text) to authenticated;

commit;
