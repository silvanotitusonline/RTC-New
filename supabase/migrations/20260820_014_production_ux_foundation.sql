-- RTC Community production UX foundation.
-- Additive migration: account controls, official notices, staff preferences and protected operations.
-- Client access is governed by explicit RLS policies; service-role processing remains server-side.

create table if not exists public.account_preferences (
  user_id uuid primary key references auth.users(id) on delete cascade,
  bio text not null default '',
  interests text[] not null default '{}',
  reading_mode boolean not null default false,
  theme_preference text not null default 'system' check (theme_preference in ('light', 'dark', 'system')),
  support_notifications boolean not null default true,
  community_notifications boolean not null default true,
  avatar_object_path text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.data_export_requests (
  id uuid primary key default gen_random_uuid(),
  requester_id uuid not null references auth.users(id),
  state text not null default 'submitted' check (state in ('submitted', 'processing', 'ready', 'expired', 'failed', 'cancelled')),
  artifact_path text,
  expires_at timestamptz,
  created_at timestamptz not null default now(),
  processed_at timestamptz,
  failure_reason text
);

create table if not exists public.account_deletion_requests (
  id uuid primary key default gen_random_uuid(),
  requester_id uuid not null references auth.users(id),
  state text not null default 'reauthentication_required' check (state in ('reauthentication_required', 'submitted', 'processing', 'completed', 'cancelled', 'failed')),
  requested_at timestamptz not null default now(),
  reauthenticated_at timestamptz,
  completed_at timestamptz,
  audit_note text
);

create table if not exists public.user_feedback (
  id uuid primary key default gen_random_uuid(),
  reporter_id uuid not null references auth.users(id),
  message text not null check (char_length(trim(message)) between 3 and 4000),
  app_version text,
  device_summary text,
  attachment_object_path text,
  state text not null default 'submitted' check (state in ('submitted', 'triaged', 'resolved', 'closed')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.help_articles (
  id uuid primary key default gen_random_uuid(),
  slug text not null unique,
  title text not null,
  summary text not null,
  body text not null,
  category text not null,
  published_at timestamptz,
  created_by uuid not null references auth.users(id),
  updated_by uuid not null references auth.users(id),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.official_notice_templates (
  id uuid primary key default gen_random_uuid(),
  template_key text not null unique,
  title text not null,
  body_template text not null,
  category text not null,
  is_active boolean not null default true,
  created_by uuid not null references auth.users(id),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.official_notices (
  id uuid primary key default gen_random_uuid(),
  title text not null check (char_length(trim(title)) between 3 and 180),
  body text not null check (char_length(trim(body)) between 3 and 12000),
  category text not null default 'Community Updates',
  status text not null default 'submitted' check (status in ('draft', 'submitted', 'under_review', 'scheduled', 'published', 'not_published', 'archived')),
  safety_sensitive boolean not null default false,
  created_by uuid not null references auth.users(id),
  reviewed_by uuid references auth.users(id),
  moderated_by uuid references auth.users(id),
  published_by uuid references auth.users(id),
  scheduled_at timestamptz,
  published_at timestamptz,
  archived_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint official_notices_schedule_requires_time check (status <> 'scheduled' or scheduled_at is not null),
  constraint official_notices_published_requires_time check (status <> 'published' or published_at is not null)
);

create table if not exists public.official_notice_reviews (
  id uuid primary key default gen_random_uuid(),
  notice_id uuid not null references public.official_notices(id) on delete cascade,
  reviewer_id uuid not null references auth.users(id),
  review_kind text not null check (review_kind in ('editorial', 'safety', 'publication', 'not_published')),
  outcome text not null check (outcome in ('approved', 'changes_requested', 'rejected')),
  note text,
  created_at timestamptz not null default now()
);

create table if not exists public.staff_work_preferences (
  user_id uuid primary key references auth.users(id) on delete cascade,
  queue_order text[] not null default array['priority', 'age'],
  assigned_work_notifications boolean not null default true,
  availability_status text not null default 'available' check (availability_status in ('available', 'busy', 'away')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.bulk_action_batches (
  id uuid primary key default gen_random_uuid(),
  actor_id uuid not null references auth.users(id),
  action_type text not null,
  typed_confirmation text not null,
  audit_note text not null check (char_length(trim(audit_note)) >= 3),
  state text not null default 'proposed' check (state in ('proposed', 'confirmed', 'completed', 'failed', 'cancelled')),
  created_at timestamptz not null default now(),
  completed_at timestamptz
);

create table if not exists public.bulk_action_items (
  id uuid primary key default gen_random_uuid(),
  batch_id uuid not null references public.bulk_action_batches(id) on delete cascade,
  entity_type text not null,
  entity_id uuid not null,
  result text,
  created_at timestamptz not null default now()
);

create index if not exists official_notices_public_index on public.official_notices (status, published_at desc);
create index if not exists official_notices_submitter_index on public.official_notices (created_by, created_at desc);
create index if not exists data_export_requests_requester_index on public.data_export_requests (requester_id, created_at desc);
create index if not exists account_deletion_requests_requester_index on public.account_deletion_requests (requester_id, requested_at desc);
create index if not exists user_feedback_state_index on public.user_feedback (state, created_at desc);
create index if not exists bulk_action_batches_actor_index on public.bulk_action_batches (actor_id, created_at desc);

create or replace function private.is_content_authority()
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select private.has_any_role(array['CONTENT_EDITOR', 'SYSTEM_ADMIN']::public.app_role[]);
$$;

create or replace function private.is_moderation_authority()
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select private.has_any_role(array['MODERATOR', 'SYSTEM_ADMIN']::public.app_role[]);
$$;

create or replace function private.is_any_staff()
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select private.has_any_role(array['CASE_STAFF', 'CONTENT_EDITOR', 'MODERATOR', 'EVIDENCE_REVIEWER', 'SYSTEM_ADMIN']::public.app_role[]);
$$;

revoke all on function private.is_content_authority() from public;
revoke all on function private.is_moderation_authority() from public;
revoke all on function private.is_any_staff() from public;

alter table public.account_preferences enable row level security;
alter table public.data_export_requests enable row level security;
alter table public.account_deletion_requests enable row level security;
alter table public.user_feedback enable row level security;
alter table public.help_articles enable row level security;
alter table public.official_notice_templates enable row level security;
alter table public.official_notices enable row level security;
alter table public.official_notice_reviews enable row level security;
alter table public.staff_work_preferences enable row level security;
alter table public.bulk_action_batches enable row level security;
alter table public.bulk_action_items enable row level security;

revoke all on public.account_preferences, public.data_export_requests, public.account_deletion_requests,
  public.user_feedback, public.help_articles, public.official_notice_templates, public.official_notices,
  public.official_notice_reviews, public.staff_work_preferences, public.bulk_action_batches,
  public.bulk_action_items from anon, authenticated;

grant select, insert, update on public.account_preferences to authenticated;
grant select, insert on public.data_export_requests to authenticated;
grant select on public.account_deletion_requests to authenticated;
grant select, insert on public.user_feedback to authenticated;
grant select on public.help_articles to anon, authenticated;
grant insert, update, delete on public.help_articles to authenticated;
grant select on public.official_notice_templates to authenticated;
grant insert, update, delete on public.official_notice_templates to authenticated;
grant select, insert, update, delete on public.official_notices to authenticated;
grant select, insert on public.official_notice_reviews to authenticated;
grant select, insert, update on public.staff_work_preferences to authenticated;
grant select on public.bulk_action_batches, public.bulk_action_items to authenticated;

create policy account_preferences_select_own on public.account_preferences
  for select to authenticated using ((select auth.uid()) = user_id);
create policy account_preferences_insert_own on public.account_preferences
  for insert to authenticated with check ((select auth.uid()) = user_id);
create policy account_preferences_update_own on public.account_preferences
  for update to authenticated using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);

create policy data_export_requests_select_own on public.data_export_requests
  for select to authenticated using ((select auth.uid()) = requester_id);
create policy data_export_requests_insert_own on public.data_export_requests
  for insert to authenticated with check ((select auth.uid()) = requester_id and state = 'submitted');

create policy account_deletion_requests_select_own on public.account_deletion_requests
  for select to authenticated using ((select auth.uid()) = requester_id);

create policy user_feedback_select_own_or_triage on public.user_feedback
  for select to authenticated using ((select auth.uid()) = reporter_id or private.is_content_authority());
create policy user_feedback_insert_own on public.user_feedback
  for insert to authenticated with check ((select auth.uid()) = reporter_id and state = 'submitted');

create policy help_articles_read_published on public.help_articles
  for select to anon, authenticated using (published_at is not null or private.is_content_authority());
create policy help_articles_write_authority on public.help_articles
  for all to authenticated using (private.is_content_authority()) with check (private.is_content_authority());

create policy templates_read_content_authority on public.official_notice_templates
  for select to authenticated using (private.is_content_authority());
create policy templates_write_content_authority on public.official_notice_templates
  for all to authenticated using (private.is_content_authority()) with check (private.is_content_authority());

create policy notices_read_public_own_or_authority on public.official_notices
  for select to anon, authenticated using (
    published_at is not null
    or (select auth.uid()) = created_by
    or private.is_content_authority()
    or private.is_moderation_authority()
  );
create policy notices_insert_author_or_content_authority on public.official_notices
  for insert to authenticated with check (
    (select auth.uid()) = created_by
    and (
      status = 'submitted'
      or (status = 'draft' and private.is_content_authority())
    )
  );
create policy notices_update_authority_or_own_unpublished on public.official_notices
  for update to authenticated using (
    private.is_content_authority()
    or (created_by = (select auth.uid()) and status in ('submitted', 'draft'))
  ) with check (
    private.is_content_authority()
    or (created_by = (select auth.uid()) and status in ('submitted', 'draft'))
  );
create policy notices_delete_content_authority on public.official_notices
  for delete to authenticated using (private.is_content_authority());

create policy notice_reviews_read_relevant_staff_or_submitter on public.official_notice_reviews
  for select to authenticated using (
    private.is_content_authority()
    or private.is_moderation_authority()
    or exists (select 1 from public.official_notices n where n.id = notice_id and n.created_by = (select auth.uid()))
  );
create policy notice_reviews_insert_review_authority on public.official_notice_reviews
  for insert to authenticated with check (
    reviewer_id = (select auth.uid())
    and (private.is_content_authority() or private.is_moderation_authority())
  );

create policy staff_preferences_select_own on public.staff_work_preferences
  for select to authenticated using ((select auth.uid()) = user_id and private.is_any_staff());
create policy staff_preferences_insert_own on public.staff_work_preferences
  for insert to authenticated with check ((select auth.uid()) = user_id and private.is_any_staff());
create policy staff_preferences_update_own on public.staff_work_preferences
  for update to authenticated using ((select auth.uid()) = user_id and private.is_any_staff()) with check ((select auth.uid()) = user_id and private.is_any_staff());

create policy bulk_action_batches_read_own_or_admin on public.bulk_action_batches
  for select to authenticated using ((select auth.uid()) = actor_id or private.has_role('SYSTEM_ADMIN'::public.app_role));
create policy bulk_action_items_read_own_or_admin on public.bulk_action_items
  for select to authenticated using (
    exists (select 1 from public.bulk_action_batches b where b.id = batch_id and (b.actor_id = (select auth.uid()) or private.has_role('SYSTEM_ADMIN'::public.app_role)))
  );

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('rtc-profile-media', 'rtc-profile-media', false, 5242880, array['image/jpeg', 'image/png', 'image/webp'])
on conflict (id) do update set public = excluded.public, file_size_limit = excluded.file_size_limit, allowed_mime_types = excluded.allowed_mime_types;

create policy profile_media_select_owner on storage.objects
  for select to authenticated using (bucket_id = 'rtc-profile-media' and (storage.foldername(name))[1] = (select auth.uid())::text);
create policy profile_media_insert_owner on storage.objects
  for insert to authenticated with check (bucket_id = 'rtc-profile-media' and (storage.foldername(name))[1] = (select auth.uid())::text);
create policy profile_media_update_owner on storage.objects
  for update to authenticated using (bucket_id = 'rtc-profile-media' and (storage.foldername(name))[1] = (select auth.uid())::text) with check (bucket_id = 'rtc-profile-media' and (storage.foldername(name))[1] = (select auth.uid())::text);
create policy profile_media_delete_owner on storage.objects
  for delete to authenticated using (bucket_id = 'rtc-profile-media' and (storage.foldername(name))[1] = (select auth.uid())::text);

-- Server-only publisher. It is intentionally not granted to API client roles.
create or replace function public.publish_due_official_notices()
returns integer
language plpgsql
security definer
set search_path = ''
as $$
declare
  published_count integer;
begin
  update public.official_notices
     set status = 'published',
         published_at = now(),
         updated_at = now()
   where status = 'scheduled'
     and scheduled_at <= now()
     and (not safety_sensitive or moderated_by is not null);

  get diagnostics published_count = row_count;
  return published_count;
end;
$$;
revoke all on function public.publish_due_official_notices() from public, anon, authenticated;

-- The verification/processing Edge Function may use service role for deletion and export completion.
-- No client grant exists for account deletion state mutation.
