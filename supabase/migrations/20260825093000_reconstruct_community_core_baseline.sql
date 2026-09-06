-- Reconstructed from read-only production catalog and function metadata on 2026-08-25.
-- Covers the Community feed, comments, private media and guideline-gated write RPCs.
-- No production data, credentials, FCM tokens, bucket objects, schedulers or Edge Function secrets
-- are included. Client writes remain governed by RLS and narrow SECURITY DEFINER RPCs.

create table if not exists public.community_profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  display_name text not null default '' check (char_length(display_name) between 1 and 80),
  handle text not null unique check (handle ~ '^[a-z0-9_]{3,30}$'),
  avatar_path text,
  bio text not null default '' check (char_length(bio) <= 100),
  guidelines_version integer not null default 0 check (guidelines_version >= 0),
  guidelines_accepted_at timestamptz,
  staff_badge boolean not null default false,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.community_categories (
  id uuid primary key default gen_random_uuid(),
  slug text not null unique check (slug ~ '^[a-z0-9-]{3,60}$'),
  label text not null unique check (char_length(label) between 3 and 80),
  description text not null default '' check (char_length(description) <= 280),
  sort_order smallint not null default 0,
  active boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.community_posts (
  id uuid primary key default gen_random_uuid(),
  author_id uuid not null references public.community_profiles(id) on delete cascade,
  category_id uuid references public.community_categories(id) on delete set null,
  body text not null default '' check (char_length(body) <= 280),
  external_url text check (external_url is null or external_url ~ '^https://'),
  state text not null default 'DRAFT' check (state in ('DRAFT', 'PUBLISHED', 'LIMITED_PENDING_REVIEW', 'HIDDEN_BY_MODERATION', 'LOCKED', 'DELETED_BY_AUTHOR')),
  is_locked boolean not null default false,
  pinned_until timestamptz,
  report_count integer not null default 0 check (report_count >= 0),
  edited_at timestamptz,
  deleted_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.community_post_media (
  id uuid primary key default gen_random_uuid(),
  post_id uuid not null references public.community_posts(id) on delete cascade,
  storage_path text not null unique,
  media_kind text not null check (media_kind in ('IMAGE', 'VIDEO')),
  mime_type text not null check (mime_type in ('image/jpeg', 'image/png', 'image/webp', 'video/mp4', 'video/webm')),
  byte_size bigint not null check (byte_size > 0 and byte_size <= 20971520),
  width integer check (width is null or width > 0),
  height integer check (height is null or height > 0),
  duration_seconds integer check (duration_seconds is null or duration_seconds between 1 and 180),
  position smallint not null check (position between 1 and 10),
  created_at timestamptz not null default now(),
  caption text check (caption is null or char_length(trim(caption)) between 1 and 500),
  uploaded_by uuid references auth.users(id)
);

create table if not exists public.community_hashtags (
  id uuid primary key default gen_random_uuid(),
  tag text not null unique check (tag ~ '^[a-z0-9_]{2,50}$'),
  created_at timestamptz not null default now()
);

create table if not exists public.community_post_hashtags (
  post_id uuid not null references public.community_posts(id) on delete cascade,
  hashtag_id uuid not null references public.community_hashtags(id) on delete cascade,
  primary key (post_id, hashtag_id)
);

create table if not exists public.community_comments (
  id uuid primary key default gen_random_uuid(),
  post_id uuid not null references public.community_posts(id) on delete cascade,
  parent_comment_id uuid references public.community_comments(id) on delete set null,
  author_id uuid not null references public.community_profiles(id) on delete cascade,
  body text not null check (char_length(trim(body)) between 1 and 280),
  state text not null default 'PUBLISHED' check (state in ('PUBLISHED', 'LIMITED_PENDING_REVIEW', 'HIDDEN_BY_MODERATION', 'DELETED_BY_AUTHOR')),
  report_count integer not null default 0 check (report_count >= 0),
  edited_at timestamptz,
  deleted_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.community_reactions (
  actor_id uuid not null references public.community_profiles(id) on delete cascade,
  subject_type text not null check (subject_type in ('POST', 'COMMENT')),
  subject_id uuid not null,
  reaction_type text not null check (reaction_type in ('LIKE', 'HELPFUL', 'CELEBRATE', 'SUPPORT', 'CONCERN')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key (actor_id, subject_type, subject_id)
);

create table if not exists public.community_reports (
  id uuid primary key default gen_random_uuid(),
  reporter_id uuid not null references public.community_profiles(id) on delete cascade,
  subject_type text not null check (subject_type in ('POST', 'COMMENT')),
  subject_id uuid not null,
  reason_code text not null check (reason_code in ('SPAM', 'HARASSMENT_BULLYING', 'HATE_DISCRIMINATION', 'MISINFORMATION', 'PRIVACY_EXPOSURE', 'EXPLICIT_UNSAFE', 'ILLEGAL_ACTIVITY', 'OTHER')),
  details text not null default '' check (char_length(details) <= 1000),
  state text not null default 'OPEN' check (state in ('OPEN', 'UNDER_REVIEW', 'RESOLVED', 'DISMISSED')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.community_blocks (
  blocker_id uuid not null references public.community_profiles(id) on delete cascade,
  blocked_id uuid not null references public.community_profiles(id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (blocker_id, blocked_id),
  check (blocker_id <> blocked_id)
);

create table if not exists public.community_mutes (
  muter_id uuid not null references public.community_profiles(id) on delete cascade,
  muted_id uuid not null references public.community_profiles(id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (muter_id, muted_id),
  check (muter_id <> muted_id)
);

create table if not exists public.community_rate_limits (
  actor_id uuid not null references public.community_profiles(id) on delete cascade,
  event_name text not null,
  window_started_at timestamptz not null,
  request_count integer not null default 0 check (request_count >= 0),
  updated_at timestamptz not null default now(),
  primary key (actor_id, event_name)
);

create index if not exists community_posts_feed_idx on public.community_posts(state, created_at desc) where deleted_at is null;
create index if not exists community_comments_post_idx on public.community_comments(post_id, created_at) where deleted_at is null;
create index if not exists community_post_media_post_idx on public.community_post_media(post_id, position);
create index if not exists community_reports_state_idx on public.community_reports(state, created_at);

create or replace function private.current_community_guidelines_version()
returns integer language sql stable security definer set search_path = public, pg_temp
as $$ select 1; $$;

create or replace function private.ensure_community_profile_for_account(p_user_id uuid)
returns void language plpgsql security definer set search_path = ''
as $$
declare v_display_name text; v_avatar_path text;
begin
  if p_user_id is null then return; end if;
  select p.display_name, p.avatar_url into v_display_name, v_avatar_path from public.profiles p where p.id = p_user_id;
  if not found then return; end if;
  insert into public.community_profiles (id, display_name, handle, avatar_path, bio, guidelines_version, staff_badge)
  values (p_user_id, left(coalesce(nullif(btrim(v_display_name), ''), 'Community member'), 80), 'member_' || replace(left(p_user_id::text, 12), '-', ''), v_avatar_path, '', 0, false)
  on conflict (id) do update set display_name = excluded.display_name, avatar_path = excluded.avatar_path, updated_at = now();
end;
$$;

create or replace function private.community_guidelines_accepted()
returns boolean language sql stable security definer set search_path = public, pg_temp
as $$
  select exists (
    select 1 from public.community_profiles cp
    where cp.id = auth.uid()
      and cp.guidelines_version >= private.current_community_guidelines_version()
      and cp.guidelines_accepted_at is not null
  );
$$;

create or replace function private.community_blocked(viewer uuid, other_user uuid)
returns boolean language sql stable security definer set search_path = public, pg_temp
as $$
  select viewer is not null and exists (
    select 1 from public.community_blocks b
    where (b.blocker_id = viewer and b.blocked_id = other_user)
       or (b.blocker_id = other_user and b.blocked_id = viewer)
  );
$$;

create or replace function private.community_muted(viewer uuid, other_user uuid)
returns boolean language sql stable security definer set search_path = public, pg_temp
as $$
  select viewer is not null and exists (
    select 1 from public.community_mutes m where m.muter_id = viewer and m.muted_id = other_user
  );
$$;

create or replace function private.can_view_community_author(author uuid)
returns boolean language sql stable security definer set search_path = public, pg_temp
as $$ select not private.community_blocked(auth.uid(), author) and not private.community_muted(auth.uid(), author); $$;

create or replace function private.community_staff()
returns boolean language sql stable security definer set search_path = public, pg_temp
as $$ select private.has_any_role(array['MODERATOR'::public.app_role, 'CONTENT_EDITOR'::public.app_role, 'SYSTEM_ADMIN'::public.app_role]); $$;

create or replace function private.is_public_community_post(post_id uuid)
returns boolean language sql stable security definer set search_path = public, pg_temp
as $$
  select exists (
    select 1 from public.community_posts p
    where p.id = post_id and p.state in ('PUBLISHED', 'LOCKED') and p.deleted_at is null and private.can_view_community_author(p.author_id)
  );
$$;

create or replace function private.can_view_community_media_path(object_path text)
returns boolean language sql stable security definer set search_path = public, pg_temp
as $$
  select exists (
    select 1 from public.community_post_media m join public.community_posts p on p.id = m.post_id
    where m.storage_path = object_path and p.state in ('PUBLISHED', 'LOCKED') and p.deleted_at is null and private.can_view_community_author(p.author_id)
  );
$$;

revoke all on function private.current_community_guidelines_version() from public;
revoke all on function private.ensure_community_profile_for_account(uuid) from public;
revoke all on function private.community_guidelines_accepted() from public;
revoke all on function private.community_blocked(uuid, uuid) from public;
revoke all on function private.community_muted(uuid, uuid) from public;
revoke all on function private.can_view_community_author(uuid) from public;
revoke all on function private.community_staff() from public;
revoke all on function private.is_public_community_post(uuid) from public;
revoke all on function private.can_view_community_media_path(text) from public;
grant execute on function private.current_community_guidelines_version() to authenticated;
grant execute on function private.community_guidelines_accepted() to authenticated;
grant execute on function private.community_blocked(uuid, uuid) to authenticated;
grant execute on function private.community_muted(uuid, uuid) to authenticated;
grant execute on function private.can_view_community_author(uuid) to anon, authenticated;
grant execute on function private.community_staff() to anon, authenticated;
grant execute on function private.is_public_community_post(uuid) to anon, authenticated;
grant execute on function private.can_view_community_media_path(text) to authenticated;

alter table public.community_profiles enable row level security;
alter table public.community_categories enable row level security;
alter table public.community_posts enable row level security;
alter table public.community_post_media enable row level security;
alter table public.community_hashtags enable row level security;
alter table public.community_post_hashtags enable row level security;
alter table public.community_comments enable row level security;
alter table public.community_reactions enable row level security;
alter table public.community_reports enable row level security;
alter table public.community_blocks enable row level security;
alter table public.community_mutes enable row level security;
alter table public.community_rate_limits enable row level security;

revoke all on public.community_profiles, public.community_categories, public.community_posts, public.community_post_media,
  public.community_hashtags, public.community_post_hashtags, public.community_comments, public.community_reactions,
  public.community_reports, public.community_blocks, public.community_mutes, public.community_rate_limits from anon, authenticated;
grant select on public.community_profiles to authenticated;
grant select on public.community_categories, public.community_posts, public.community_hashtags, public.community_post_hashtags, public.community_comments to anon, authenticated;
grant select, insert, delete on public.community_post_media to authenticated;
grant insert, update on public.community_comments to authenticated;
grant select on public.community_reactions, public.community_reports, public.community_blocks, public.community_mutes to authenticated;

create policy community_profiles_authenticated_read on public.community_profiles for select to authenticated using (true);
create policy community_categories_public_read on public.community_categories for select to anon, authenticated using (active or private.community_staff());
create policy community_posts_public_read on public.community_posts for select to anon, authenticated using (((state in ('PUBLISHED','LOCKED')) and deleted_at is null and private.can_view_community_author(author_id)) or author_id = auth.uid() or private.community_staff());
create policy community_media_authenticated_read on public.community_post_media for select to authenticated using (private.is_public_community_post(post_id) or exists (select 1 from public.community_posts p where p.id = community_post_media.post_id and (p.author_id = auth.uid() or private.community_staff())));
create policy community_media_insert_own_draft on public.community_post_media for insert to authenticated with check (uploaded_by = auth.uid() and exists (select 1 from public.community_posts p where p.id = community_post_media.post_id and p.author_id = auth.uid() and p.state = 'DRAFT'));
create policy community_media_delete_own_draft on public.community_post_media for delete to authenticated using (uploaded_by = auth.uid() and exists (select 1 from public.community_posts p where p.id = community_post_media.post_id and p.author_id = auth.uid() and p.state = 'DRAFT'));
create policy community_hashtags_public_read on public.community_hashtags for select to anon, authenticated using (true);
create policy community_post_hashtags_public_read on public.community_post_hashtags for select to anon, authenticated using (private.is_public_community_post(post_id) or private.community_staff());
create policy community_comments_public_read on public.community_comments for select to anon, authenticated using (((state = 'PUBLISHED') and deleted_at is null and private.is_public_community_post(post_id) and private.can_view_community_author(author_id)) or author_id = auth.uid() or private.community_staff());
create policy community_comments_insert_authenticated on public.community_comments for insert to authenticated with check (author_id = auth.uid() and parent_comment_id is null and state = 'PUBLISHED' and report_count = 0 and deleted_at is null and exists (select 1 from public.community_posts p where p.id = community_comments.post_id and p.state = 'PUBLISHED' and p.deleted_at is null and p.is_locked = false and private.can_view_community_author(p.author_id)));
create policy community_comments_update_own on public.community_comments for update to authenticated using (author_id = auth.uid() and state = 'PUBLISHED' and deleted_at is null) with check (author_id = auth.uid() and ((state = 'PUBLISHED' and deleted_at is null) or (state = 'DELETED_BY_AUTHOR' and deleted_at is not null)));
create policy community_reactions_public_read on public.community_reactions for select to authenticated using (true);
create policy community_reports_read_owner_or_staff on public.community_reports for select to authenticated using (reporter_id = auth.uid() or private.community_staff());
create policy community_blocks_own on public.community_blocks for select to authenticated using (blocker_id = auth.uid());
create policy community_mutes_own on public.community_mutes for select to authenticated using (muter_id = auth.uid());
create policy community_rate_limits_no_direct_access on public.community_rate_limits for all to authenticated using (false) with check (false);

create or replace function public.accept_community_guidelines()
returns void language plpgsql security definer set search_path = public, pg_temp
as $$
begin
  if auth.uid() is null then raise exception 'AUTH_REQUIRED'; end if;
  perform private.ensure_community_profile_for_account(auth.uid());
  update public.community_profiles
     set guidelines_version = private.current_community_guidelines_version(), guidelines_accepted_at = now(), updated_at = now()
   where id = auth.uid();
  if not found then raise exception 'COMMUNITY_PROFILE_NOT_READY'; end if;
end;
$$;

create or replace function public.community_guidelines_accepted_status()
returns boolean language sql stable security definer set search_path = public, pg_temp
as $$ select private.community_guidelines_accepted(); $$;

create or replace function public.create_community_post_draft(p_body text default '')
returns uuid language plpgsql security definer set search_path = ''
as $$
declare v_user_id uuid := auth.uid(); v_post_id uuid; v_body text := trim(coalesce(p_body, ''));
begin
  if v_user_id is null then raise exception 'AUTH_REQUIRED'; end if;
  if char_length(v_body) > 280 then raise exception 'INVALID_POST'; end if;
  perform private.ensure_community_profile_for_account(v_user_id);
  if not private.community_guidelines_accepted() then raise exception 'GUIDELINES_NOT_ACCEPTED'; end if;
  insert into public.community_posts(author_id, body, state, is_locked, report_count) values (v_user_id, v_body, 'DRAFT', false, 0) returning id into v_post_id;
  return v_post_id;
end;
$$;

create or replace function public.publish_community_post(p_post_id uuid)
returns uuid language plpgsql security definer set search_path = ''
as $$
declare v_post public.community_posts%rowtype; v_media_count integer;
begin
  if auth.uid() is null then raise exception 'AUTH_REQUIRED'; end if;
  select * into v_post from public.community_posts where id = p_post_id for update;
  if not found or v_post.author_id <> auth.uid() or v_post.state <> 'DRAFT' then raise exception 'POST_NOT_AVAILABLE'; end if;
  select count(*)::integer into v_media_count from public.community_post_media where post_id = p_post_id;
  if v_media_count > 10 or (char_length(trim(v_post.body)) = 0 and v_media_count = 0) then raise exception 'INVALID_POST'; end if;
  update public.community_posts set state = 'PUBLISHED', updated_at = now() where id = p_post_id;
  return p_post_id;
end;
$$;

create or replace function public.create_community_comment(p_post_id uuid, p_body text, p_parent_comment_id uuid default null)
returns uuid language plpgsql security definer set search_path = public, pg_temp
as $$
declare comment_id uuid;
begin
  if auth.uid() is null then raise exception 'AUTH_REQUIRED'; end if;
  perform private.ensure_community_profile_for_account(auth.uid());
  if not private.community_guidelines_accepted() then raise exception 'GUIDELINES_NOT_ACCEPTED'; end if;
  if char_length(trim(coalesce(p_body, ''))) not between 1 and 280 then raise exception 'INVALID_COMMENT'; end if;
  if not exists (select 1 from public.community_posts p where p.id = p_post_id and p.state in ('PUBLISHED','LOCKED') and p.deleted_at is null and not p.is_locked and private.can_view_community_author(p.author_id)) then raise exception 'COMMENTS_UNAVAILABLE'; end if;
  if p_parent_comment_id is not null then raise exception 'THREADS_NOT_ENABLED'; end if;
  insert into public.community_comments(post_id, parent_comment_id, author_id, body) values (p_post_id, null, auth.uid(), trim(p_body)) returning id into comment_id;
  return comment_id;
end;
$$;

create or replace function public.register_fcm_device(p_fcm_token text, p_app_version text default null)
returns void language plpgsql security definer set search_path = ''
as $$
declare v_token text := trim(coalesce(p_fcm_token, ''));
begin
  if auth.uid() is null then raise exception 'AUTH_REQUIRED'; end if;
  if char_length(v_token) not between 20 and 512 then raise exception 'INVALID_FCM_TOKEN'; end if;
  insert into public.device_registrations(user_id, fcm_token, platform, app_version, last_seen_at)
  values (auth.uid(), v_token, 'android', nullif(trim(coalesce(p_app_version, '')), ''), now())
  on conflict (fcm_token) do update set user_id = excluded.user_id, platform = excluded.platform, app_version = excluded.app_version, last_seen_at = now();
end;
$$;

revoke all on function public.accept_community_guidelines() from public, anon;
revoke all on function public.community_guidelines_accepted_status() from public, anon;
revoke all on function public.create_community_post_draft(text) from public, anon;
revoke all on function public.publish_community_post(uuid) from public, anon;
revoke all on function public.create_community_comment(uuid, text, uuid) from public, anon;
revoke all on function public.register_fcm_device(text, text) from public, anon;
grant execute on function public.accept_community_guidelines() to authenticated;
grant execute on function public.community_guidelines_accepted_status() to authenticated;
grant execute on function public.create_community_post_draft(text) to authenticated;
grant execute on function public.publish_community_post(uuid) to authenticated;
grant execute on function public.create_community_comment(uuid, text, uuid) to authenticated;
grant execute on function public.register_fcm_device(text, text) to authenticated;

create or replace view public.community_post_feed with (security_invoker = true) as
select p.id, p.author_id, cp.display_name as author_name, cp.handle as author_handle, cp.avatar_path, cp.staff_badge,
  p.body, p.state, p.is_locked, p.created_at, p.updated_at, p.edited_at, p.report_count,
  (select count(*)::integer from public.community_comments c where c.post_id = p.id and c.state = 'PUBLISHED' and c.deleted_at is null) as comment_count
from public.community_posts p join public.community_profiles cp on cp.id = p.author_id
where p.state in ('PUBLISHED','LOCKED') and p.deleted_at is null;

create or replace view public.community_comment_feed with (security_invoker = true) as
select c.id, c.post_id, c.parent_comment_id, c.author_id, coalesce(cp.display_name, 'Community member') as author_name,
  coalesce(cp.handle, 'member_' || replace(left(c.author_id::text, 12), '-', '')) as author_handle,
  cp.avatar_path, coalesce(cp.staff_badge, false) as staff_badge, c.body, c.created_at, c.updated_at, c.edited_at
from public.community_comments c left join public.community_profiles cp on cp.id = c.author_id
where c.state = 'PUBLISHED' and c.deleted_at is null;

create or replace view public.community_media_feed with (security_invoker = true) as
select id, post_id, storage_path, media_kind, mime_type, byte_size, width, height, duration_seconds, position, caption, created_at
from public.community_post_media;

grant select on public.community_post_feed, public.community_comment_feed to anon, authenticated;
grant select on public.community_media_feed to authenticated;

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('rtc-community-media', 'rtc-community-media', false, 20971520, array['image/jpeg','image/png','image/webp','video/mp4','video/webm'])
on conflict (id) do update set public = excluded.public, file_size_limit = excluded.file_size_limit, allowed_mime_types = excluded.allowed_mime_types;

create policy community_storage_upload_draft_owner on storage.objects for insert to authenticated
with check (bucket_id = 'rtc-community-media' and (storage.foldername(name))[1] = auth.uid()::text and exists (select 1 from public.community_posts p where p.id::text = (storage.foldername(objects.name))[2] and p.author_id = auth.uid() and p.state = 'DRAFT'));
create policy community_storage_read_eligible_viewer on storage.objects for select to authenticated
using (bucket_id = 'rtc-community-media' and private.can_view_community_media_path(name));
create policy community_storage_delete_draft_owner on storage.objects for delete to authenticated
using (bucket_id = 'rtc-community-media' and (storage.foldername(name))[1] = auth.uid()::text and exists (select 1 from public.community_posts p where p.id::text = (storage.foldername(objects.name))[2] and p.author_id = auth.uid() and p.state = 'DRAFT'));
