-- Isolated editorial-notice workflow.
-- Direct editor writes are closed: mutation occurs only through guarded RPCs with durable lifecycle
-- and audit records. A scheduled publication request is explicitly rejected because this isolated
-- project has no approved scheduler; no notice is left in a state that cannot be published safely.

alter table public.official_notices
  add column if not exists retired_reason text,
  add column if not exists corrects_notice_id uuid references public.official_notices(id) on delete set null;

create table if not exists public.official_notice_lifecycle_events (
  id uuid primary key default gen_random_uuid(),
  notice_id uuid not null references public.official_notices(id) on delete cascade,
  actor_id uuid references auth.users(id) on delete set null,
  event_type text not null check (event_type in ('DRAFT_CREATED', 'SUBMITTED', 'REVIEW_STARTED', 'APPROVED', 'CHANGES_REQUESTED', 'REJECTED', 'SCHEDULED', 'PUBLISHED', 'RETIRED', 'CORRECTION_CREATED')),
  note text,
  occurred_at timestamptz not null default now()
);

create index if not exists official_notice_lifecycle_events_notice_index
  on public.official_notice_lifecycle_events(notice_id, occurred_at desc);

alter table public.official_notice_lifecycle_events enable row level security;
revoke all on public.official_notice_lifecycle_events from public, anon, authenticated;
create policy official_notice_lifecycle_events_no_direct_client_access
  on public.official_notice_lifecycle_events for all to authenticated using (false) with check (false);

-- Retain the established select paths for public/author/staff visibility, but remove every client
-- mutation route. Security-definer functions below enforce state transitions, actor separation,
-- limits, audit records, and System Administrator MFA for safety-sensitive notices.
revoke insert, update, delete on public.official_notices from authenticated;
revoke insert, update, delete on public.official_notice_reviews from authenticated;
drop policy if exists notices_insert_author_or_content_authority on public.official_notices;
drop policy if exists notices_update_authority_or_own_unpublished on public.official_notices;
drop policy if exists notices_delete_content_authority on public.official_notices;
drop policy if exists notice_reviews_insert_review_authority on public.official_notice_reviews;

create or replace function public.editorial_create_notice_draft(
  p_title text,
  p_body text,
  p_category text default 'Community Updates',
  p_safety_sensitive boolean default false,
  p_corrects_notice_id uuid default null
)
returns uuid
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_actor uuid := private.ops_assert_staff();
  v_id uuid;
  v_title text := trim(coalesce(p_title, ''));
  v_body text := trim(coalesce(p_body, ''));
  v_category text := trim(coalesce(p_category, 'Community Updates'));
begin
  if not private.is_content_authority() then
    raise exception 'Content Editor access is required.';
  end if;
  if char_length(v_title) not between 3 and 180
    or char_length(v_body) not between 3 and 12000
    or char_length(v_category) not between 3 and 120 then
    raise exception 'Provide a valid title, body, and category.';
  end if;
  if p_corrects_notice_id is not null and not exists (
    select 1 from public.official_notices where id = p_corrects_notice_id and status = 'published'
  ) then
    raise exception 'Only a published notice can be corrected.';
  end if;
  if coalesce(p_safety_sensitive, false) then
    perform private.access_assert_system_admin();
  end if;

  insert into public.official_notices(title, body, category, status, safety_sensitive, created_by, corrects_notice_id)
  values (v_title, v_body, v_category, 'draft', coalesce(p_safety_sensitive, false), v_actor, p_corrects_notice_id)
  returning id into v_id;
  insert into public.official_notice_lifecycle_events(notice_id, actor_id, event_type, note)
  values (v_id, v_actor, case when p_corrects_notice_id is null then 'DRAFT_CREATED' else 'CORRECTION_CREATED' end, null);
  perform private.ops_log_audit(v_actor, 'EDITORIAL_DRAFT_CREATED', 'OFFICIAL_NOTICE', v_id, 'SUCCESS');
  return v_id;
end;
$$;

create or replace function public.editorial_submit_notice(
  p_notice_id uuid,
  p_note text default null
)
returns void
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_actor uuid := private.ops_assert_staff();
  v_notice public.official_notices%rowtype;
  v_note text := nullif(trim(coalesce(p_note, '')), '');
begin
  if not private.is_content_authority() then
    raise exception 'Content Editor access is required.';
  end if;
  select * into v_notice from public.official_notices where id = p_notice_id for update;
  if not found or v_notice.created_by <> v_actor or v_notice.status not in ('draft', 'not_published') then
    raise exception 'Only your unpublished draft can be submitted.';
  end if;
  if v_notice.safety_sensitive then
    perform private.access_assert_system_admin();
  end if;
  update public.official_notices set status = 'submitted', updated_at = now() where id = v_notice.id;
  insert into public.official_notice_lifecycle_events(notice_id, actor_id, event_type, note)
  values (v_notice.id, v_actor, 'SUBMITTED', v_note);
  perform private.ops_upsert_work_item(
    'NOTICE_REVIEW', v_notice.id, 'CONTENT_EDITOR'::public.app_role,
    'Review notice: ' || left(v_notice.title, 120),
    'An editorial submission requires review by a different authorised editor or System Administrator.',
    'NORMAL', now() + interval '3 days'
  );
  perform private.ops_log_audit(v_actor, 'EDITORIAL_NOTICE_SUBMITTED', 'OFFICIAL_NOTICE', v_notice.id, 'SUCCESS');
end;
$$;

create or replace function public.editorial_review_notice(
  p_notice_id uuid,
  p_outcome text,
  p_note text,
  p_publish_mode text default 'PUBLISH',
  p_scheduled_at timestamptz default null
)
returns void
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_actor uuid := private.ops_assert_staff();
  v_notice public.official_notices%rowtype;
  v_outcome text := upper(trim(coalesce(p_outcome, '')));
  v_mode text := upper(trim(coalesce(p_publish_mode, 'PUBLISH')));
  v_note text := trim(coalesce(p_note, ''));
begin
  if not private.is_content_authority() then
    raise exception 'Content Editor access is required.';
  end if;
  if v_outcome not in ('APPROVE', 'CHANGES_REQUESTED', 'REJECT') or char_length(v_note) not between 3 and 2000 then
    raise exception 'Provide a valid review outcome and note.';
  end if;
  select * into v_notice from public.official_notices where id = p_notice_id for update;
  if not found or v_notice.status not in ('submitted', 'under_review') then
    raise exception 'This notice is not available for review.';
  end if;
  if v_notice.created_by = v_actor then
    raise exception 'A different authorised editor or System Administrator must review this notice.';
  end if;
  if v_notice.safety_sensitive then
    perform private.access_assert_system_admin();
  end if;

  if v_outcome = 'APPROVE' then
    if v_mode = 'SCHEDULE' then
      raise exception 'Scheduled publishing is disabled in this isolated environment until a dedicated scheduler is configured and tested.';
    elsif v_mode <> 'PUBLISH' then
      raise exception 'Choose publish now; scheduled publishing is currently unavailable.';
    end if;
    update public.official_notices
       set status = 'published', reviewed_by = v_actor, published_by = v_actor,
           published_at = now(), scheduled_at = null, updated_at = now()
     where id = v_notice.id;
    insert into public.official_notice_reviews(notice_id, reviewer_id, review_kind, outcome, note)
    values (v_notice.id, v_actor, 'publication', 'approved', v_note);
    insert into public.official_notice_lifecycle_events(notice_id, actor_id, event_type, note)
    values (v_notice.id, v_actor, 'APPROVED', v_note), (v_notice.id, v_actor, 'PUBLISHED', v_note);
  elsif v_outcome = 'CHANGES_REQUESTED' then
    update public.official_notices
       set status = 'draft', reviewed_by = v_actor, updated_at = now()
     where id = v_notice.id;
    insert into public.official_notice_reviews(notice_id, reviewer_id, review_kind, outcome, note)
    values (v_notice.id, v_actor, 'editorial', 'changes_requested', v_note);
    insert into public.official_notice_lifecycle_events(notice_id, actor_id, event_type, note)
    values (v_notice.id, v_actor, 'CHANGES_REQUESTED', v_note);
  else
    update public.official_notices
       set status = 'not_published', reviewed_by = v_actor, updated_at = now()
     where id = v_notice.id;
    insert into public.official_notice_reviews(notice_id, reviewer_id, review_kind, outcome, note)
    values (v_notice.id, v_actor, 'not_published', 'rejected', v_note);
    insert into public.official_notice_lifecycle_events(notice_id, actor_id, event_type, note)
    values (v_notice.id, v_actor, 'REJECTED', v_note);
  end if;

  perform private.ops_resolve_work_for_source('NOTICE_REVIEW', v_notice.id, v_actor);
  perform private.ops_log_audit(v_actor, 'EDITORIAL_NOTICE_' || v_outcome, 'OFFICIAL_NOTICE', v_notice.id, 'SUCCESS', jsonb_build_object('publish_mode', v_mode, 'note', v_note));
end;
$$;

create or replace function public.editorial_retire_notice(
  p_notice_id uuid,
  p_reason text
)
returns void
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_actor uuid := private.ops_assert_staff();
  v_notice public.official_notices%rowtype;
  v_reason text := trim(coalesce(p_reason, ''));
begin
  if not private.is_content_authority() then
    raise exception 'Content Editor access is required.';
  end if;
  if char_length(v_reason) not between 3 and 2000 then
    raise exception 'Provide a retirement reason between 3 and 2000 characters.';
  end if;
  select * into v_notice from public.official_notices where id = p_notice_id for update;
  if not found or v_notice.status not in ('published', 'scheduled') then
    raise exception 'Only a scheduled or published notice can be retired.';
  end if;
  if v_notice.safety_sensitive then
    perform private.access_assert_system_admin();
  end if;
  update public.official_notices
     set status = 'archived', archived_at = now(), retired_reason = v_reason, updated_at = now()
   where id = v_notice.id;
  insert into public.official_notice_lifecycle_events(notice_id, actor_id, event_type, note)
  values (v_notice.id, v_actor, 'RETIRED', v_reason);
  perform private.ops_log_audit(v_actor, 'EDITORIAL_NOTICE_RETIRED', 'OFFICIAL_NOTICE', v_notice.id, 'SUCCESS', jsonb_build_object('reason', v_reason));
end;
$$;

revoke all on function public.editorial_create_notice_draft(text, text, text, boolean, uuid) from public, anon;
grant execute on function public.editorial_create_notice_draft(text, text, text, boolean, uuid) to authenticated;
revoke all on function public.editorial_submit_notice(uuid, text) from public, anon;
grant execute on function public.editorial_submit_notice(uuid, text) to authenticated;
revoke all on function public.editorial_review_notice(uuid, text, text, text, timestamptz) from public, anon;
grant execute on function public.editorial_review_notice(uuid, text, text, text, timestamptz) to authenticated;
revoke all on function public.editorial_retire_notice(uuid, text) from public, anon;
grant execute on function public.editorial_retire_notice(uuid, text) to authenticated;
