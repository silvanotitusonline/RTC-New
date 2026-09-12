begin;

create extension if not exists pg_net with schema extensions;
create extension if not exists pg_cron with schema pg_catalog;

-- Canonical Production types that were absent from the reconstructed environment.
do $$ begin
  if not exists (select 1 from pg_type t join pg_namespace n on n.oid=t.typnamespace where n.nspname='public' and t.typname='content_state') then
    create type public.content_state as enum ('DRAFT','PUBLISHED','ARCHIVED');
  end if;
  if not exists (select 1 from pg_type t join pg_namespace n on n.oid=t.typnamespace where n.nspname='public' and t.typname='ai_action_state') then
    create type public.ai_action_state as enum ('PENDING_CONFIRMATION','COMPLETED_READ_ONLY','CONFIRMED_DRAFTS_CREATED','EXPIRED','STALE','REQUIRES_LIFECYCLE_REVIEW','FAILED');
  end if;
end $$;

create or replace function public.set_updated_at()
returns trigger
language plpgsql
set search_path = public
as $$ begin new.updated_at = now(); return new; end $$;

create table if not exists public.app_content (
  id uuid primary key default gen_random_uuid(),
  entity_type text not null,
  slug text not null,
  state public.content_state not null default 'DRAFT',
  title text,
  summary text,
  data jsonb not null default '{}'::jsonb,
  created_by uuid references auth.users(id) on delete set null,
  updated_by uuid references auth.users(id) on delete set null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  published_at timestamptz,
  unique(entity_type,slug),
  constraint app_content_entity_type_check check (entity_type in (
    'home_content','explore_content','notices','community_notices','community_moderation','events','projects','centres','opportunities','resources','faqs','contacts','dashboard_metrics'
  ))
);

create table if not exists public.ai_actions (
  id uuid primary key default gen_random_uuid(),
  actor_id uuid not null references auth.users(id) on delete restrict,
  command text not null check (char_length(command) between 1 and 12000),
  scope text[] not null default '{}'::text[],
  proposal jsonb not null,
  target_versions jsonb not null default '[]'::jsonb,
  state public.ai_action_state not null,
  expires_at timestamptz not null,
  confirmed_by uuid references auth.users(id) on delete set null,
  confirmed_at timestamptz,
  completed_at timestamptz,
  created_at timestamptz not null default now()
);

create table if not exists public.ai_rate_limits (
  actor_id uuid primary key references auth.users(id) on delete cascade,
  window_started_at timestamptz not null,
  request_count integer not null check (request_count >= 0),
  updated_at timestamptz not null default now()
);

create table if not exists public.content_drafts (
  id uuid primary key default gen_random_uuid(),
  entity_type text not null,
  target_entity_id uuid,
  operation text not null check (operation in ('CREATE_DRAFT','PATCH_DRAFT','CREATE_CORRECTION')),
  changes jsonb not null,
  reason text not null,
  source_proposal_id uuid not null references public.ai_actions(id) on delete restrict,
  actor_id uuid not null references auth.users(id) on delete restrict,
  state text not null default 'DRAFT' check (state in ('DRAFT','REVIEWED','PUBLISHED','REJECTED')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.content_versions (
  id uuid primary key default gen_random_uuid(),
  entity_type text not null,
  entity_id uuid,
  version_type text not null,
  source_proposal_id uuid references public.ai_actions(id) on delete set null,
  actor_id uuid references auth.users(id) on delete set null,
  before_data jsonb,
  after_data jsonb,
  reason text,
  created_at timestamptz not null default now()
);

create table if not exists public.evidence_items (
  id uuid primary key default gen_random_uuid(),
  submitted_by uuid not null references auth.users(id) on delete restrict,
  case_id uuid references public.community_cases(id) on delete set null,
  subject_type text not null,
  subject_id uuid,
  storage_path text not null,
  checksum_sha256 text,
  classification text,
  state text not null default 'SUBMITTED' check (state in ('SUBMITTED','UNDER_REVIEW','ACCEPTED','REJECTED')),
  reviewer_id uuid references auth.users(id) on delete set null,
  reviewer_note text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.community_polls (
  post_id uuid primary key references public.community_posts(id) on delete cascade,
  closes_at timestamptz not null,
  created_at timestamptz not null default now(),
  check (closes_at > created_at and closes_at <= created_at + interval '7 days')
);
create table if not exists public.community_poll_options (
  id uuid primary key default gen_random_uuid(),
  post_id uuid not null references public.community_polls(post_id) on delete cascade,
  label text not null check (char_length(trim(label)) between 1 and 120),
  position smallint not null check (position between 1 and 4),
  unique(post_id,position)
);
create table if not exists public.community_poll_votes (
  poll_post_id uuid not null references public.community_polls(post_id) on delete cascade,
  option_id uuid not null references public.community_poll_options(id) on delete cascade,
  voter_id uuid not null references public.community_profiles(id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key(poll_post_id,voter_id)
);
create table if not exists public.community_topic_follows (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references public.community_profiles(id) on delete cascade,
  category_id uuid references public.community_categories(id) on delete cascade,
  hashtag_id uuid references public.community_hashtags(id) on delete cascade,
  created_at timestamptz not null default now(),
  check (((category_id is not null)::integer + (hashtag_id is not null)::integer) = 1),
  unique(user_id,category_id),
  unique(user_id,hashtag_id)
);

create table if not exists public.operational_service_events (
  id uuid primary key default gen_random_uuid(),
  service_key text not null check (service_key in ('ALERT_DELIVERY','NOTIFICATION_DELIVERY','SCHEDULED_JOBS','COMMUNITY_AVAILABILITY','PROTECTED_CONTROLS')),
  status text not null check (status in ('GREEN','AMBER','RED')),
  category text not null check (char_length(trim(category)) between 3 and 120),
  affected_count integer not null default 0 check (affected_count >= 0),
  last_successful_at timestamptz,
  source text not null default 'system',
  occurred_at timestamptz not null default now()
);

create index if not exists app_content_public_read_idx on public.app_content(entity_type,state,published_at desc);
create index if not exists ai_actions_actor_idx on public.ai_actions(actor_id,created_at desc);
create index if not exists evidence_items_case_idx on public.evidence_items(case_id,created_at desc);
create index if not exists community_follows_category_idx on public.community_topic_follows(category_id) where category_id is not null;

alter table public.app_content enable row level security;
alter table public.ai_actions enable row level security;
alter table public.ai_rate_limits enable row level security;
alter table public.content_drafts enable row level security;
alter table public.content_versions enable row level security;
alter table public.evidence_items enable row level security;
alter table public.community_polls enable row level security;
alter table public.community_poll_options enable row level security;
alter table public.community_poll_votes enable row level security;
alter table public.community_topic_follows enable row level security;
alter table public.operational_service_events enable row level security;

revoke all on public.ai_actions,public.ai_rate_limits,public.content_drafts,public.content_versions,public.evidence_items,public.operational_service_events from public,anon,authenticated;
revoke all on public.app_content,public.community_polls,public.community_poll_options,public.community_poll_votes,public.community_topic_follows from public,anon,authenticated;
grant select on public.app_content to anon,authenticated;
grant insert,update,delete on public.app_content to authenticated;
grant select on public.ai_actions,public.content_drafts,public.content_versions to authenticated;
grant select,insert on public.evidence_items to authenticated;
grant select on public.community_polls,public.community_poll_options to anon,authenticated;
grant select on public.community_poll_votes,public.community_topic_follows to authenticated;

drop policy if exists app_content_public_read on public.app_content;
create policy app_content_public_read on public.app_content for select to anon,authenticated
using (state='PUBLISHED'::public.content_state or (select private.has_any_role(array['CONTENT_EDITOR'::public.app_role,'SYSTEM_ADMIN'::public.app_role])));
drop policy if exists app_content_admin_write on public.app_content;
create policy app_content_admin_write on public.app_content for all to authenticated
using ((select private.has_any_role(array['CONTENT_EDITOR'::public.app_role,'SYSTEM_ADMIN'::public.app_role])))
with check ((select private.has_any_role(array['CONTENT_EDITOR'::public.app_role,'SYSTEM_ADMIN'::public.app_role])));

drop policy if exists ai_actions_select_owner_or_admin on public.ai_actions;
create policy ai_actions_select_owner_or_admin on public.ai_actions for select to authenticated
using (actor_id=(select auth.uid()) or (select private.has_role('SYSTEM_ADMIN'::public.app_role)));
drop policy if exists ai_rate_limits_client_denied on public.ai_rate_limits;
create policy ai_rate_limits_client_denied on public.ai_rate_limits for all to authenticated using(false) with check(false);
drop policy if exists content_drafts_select_editor_or_owner on public.content_drafts;
create policy content_drafts_select_editor_or_owner on public.content_drafts for select to authenticated
using (actor_id=(select auth.uid()) or (select private.has_any_role(array['CONTENT_EDITOR'::public.app_role,'SYSTEM_ADMIN'::public.app_role])));
drop policy if exists content_versions_select_editor_or_owner on public.content_versions;
create policy content_versions_select_editor_or_owner on public.content_versions for select to authenticated
using (actor_id=(select auth.uid()) or (select private.has_any_role(array['CONTENT_EDITOR'::public.app_role,'SYSTEM_ADMIN'::public.app_role])));
drop policy if exists evidence_items_create_own on public.evidence_items;
create policy evidence_items_create_own on public.evidence_items for insert to authenticated with check(submitted_by=(select auth.uid()));
drop policy if exists evidence_items_select_submitter_or_reviewer on public.evidence_items;
create policy evidence_items_select_submitter_or_reviewer on public.evidence_items for select to authenticated
using (submitted_by=(select auth.uid()) or (select private.has_any_role(array['EVIDENCE_REVIEWER'::public.app_role,'SYSTEM_ADMIN'::public.app_role])));
drop policy if exists community_polls_public_read on public.community_polls;
create policy community_polls_public_read on public.community_polls for select to anon,authenticated using(private.is_public_community_post(post_id));
drop policy if exists community_poll_options_public_read on public.community_poll_options;
create policy community_poll_options_public_read on public.community_poll_options for select to anon,authenticated using(private.is_public_community_post(post_id));
drop policy if exists community_poll_votes_own_or_count on public.community_poll_votes;
create policy community_poll_votes_own_or_count on public.community_poll_votes for select to authenticated
using (voter_id=(select auth.uid()) or exists(select 1 from public.community_polls p where p.post_id=community_poll_votes.poll_post_id and private.is_public_community_post(p.post_id)));
drop policy if exists community_follows_own on public.community_topic_follows;
create policy community_follows_own on public.community_topic_follows for select to authenticated using(user_id=(select auth.uid()));

drop trigger if exists app_content_set_updated_at on public.app_content;
create trigger app_content_set_updated_at before update on public.app_content for each row execute function public.set_updated_at();
drop trigger if exists content_drafts_set_updated_at on public.content_drafts;
create trigger content_drafts_set_updated_at before update on public.content_drafts for each row execute function public.set_updated_at();
drop trigger if exists evidence_items_set_updated_at on public.evidence_items;
create trigger evidence_items_set_updated_at before update on public.evidence_items for each row execute function public.set_updated_at();

insert into storage.buckets(id,name,public,file_size_limit,allowed_mime_types)
values
 ('rtc-secure-evidence','rtc-secure-evidence',false,26214400,array['application/pdf','image/jpeg','image/png','image/webp']::text[]),
 ('rtc-feedback-media','rtc-feedback-media',false,5242880,array['image/jpeg','image/png','image/webp']::text[])
on conflict(id) do update set public=excluded.public,file_size_limit=excluded.file_size_limit,allowed_mime_types=excluded.allowed_mime_types;

drop policy if exists evidence_storage_select_authorised on storage.objects;
create policy evidence_storage_select_authorised on storage.objects for select to authenticated using(
  bucket_id='rtc-secure-evidence' and exists(select 1 from public.evidence_items e where e.storage_path=name and (e.submitted_by=(select auth.uid()) or (select private.has_any_role(array['EVIDENCE_REVIEWER'::public.app_role,'SYSTEM_ADMIN'::public.app_role]))))
);
drop policy if exists evidence_storage_insert_own_prefix on storage.objects;
create policy evidence_storage_insert_own_prefix on storage.objects for insert to authenticated with check(bucket_id='rtc-secure-evidence' and (storage.foldername(name))[1]=(select auth.uid())::text);
drop policy if exists evidence_storage_delete_own_prefix on storage.objects;
create policy evidence_storage_delete_own_prefix on storage.objects for delete to authenticated using(bucket_id='rtc-secure-evidence' and (storage.foldername(name))[1]=(select auth.uid())::text);
drop policy if exists feedback_media_insert_owner on storage.objects;
create policy feedback_media_insert_owner on storage.objects for insert to authenticated with check(bucket_id='rtc-feedback-media' and (storage.foldername(name))[1]=(select auth.uid())::text);
drop policy if exists feedback_media_select_owner_or_triage on storage.objects;
create policy feedback_media_select_owner_or_triage on storage.objects for select to authenticated using(bucket_id='rtc-feedback-media' and ((storage.foldername(name))[1]=(select auth.uid())::text or private.is_content_authority()));
drop policy if exists feedback_media_delete_owner on storage.objects;
create policy feedback_media_delete_owner on storage.objects for delete to authenticated using(bucket_id='rtc-feedback-media' and (storage.foldername(name))[1]=(select auth.uid())::text);

commit;
