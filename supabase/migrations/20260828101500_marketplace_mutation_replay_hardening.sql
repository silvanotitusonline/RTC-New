begin;

-- Marketplace checkpoint 2: make logical mutation retries replay committed outcomes.
-- Existing implementations are preserved as non-executable base functions so the
-- wrappers can add replay semantics without duplicating their mutation bodies.

create or replace function private.marketplace_replay_metadata(
  p_actor uuid,
  p_event text,
  p_key uuid
) returns jsonb
language sql
stable
security definer set search_path = '' as $$
  select jsonb_build_object(
    'businessId', e.business_id,
    'revisionId', e.revision_id,
    'metadata', e.metadata
  )
  from public.marketplace_audit_events e
  where p_key is not null
    and e.actor_id = p_actor
    and e.event_type = p_event
    and e.correlation_key = p_key
  order by e.created_at desc
  limit 1
$$;

revoke all on function private.marketplace_replay_metadata(uuid,text,uuid) from public, anon, authenticated;

-- New location creation can otherwise duplicate after commit + lost response.
revoke execute on function public.marketplace_upsert_location(uuid,uuid,jsonb,uuid) from public, anon, authenticated;
alter function public.marketplace_upsert_location(uuid,uuid,jsonb,uuid)
  rename to marketplace_upsert_location_checkpoint2_base;

create or replace function public.marketplace_upsert_location(
  p_business_id uuid,
  p_location_id uuid default null,
  p_payload jsonb default '{}'::jsonb,
  p_idempotency_key uuid default null
) returns jsonb
language plpgsql
security definer set search_path = '' as $$
declare
  v_actor uuid := private.marketplace_assert_editor(p_business_id);
  v_replay jsonb;
begin
  if p_idempotency_key is null then
    raise exception 'Marketplace mutation idempotency key is required.';
  end if;
  v_replay := private.marketplace_replay_metadata(v_actor, 'LOCATION_SAVED', p_idempotency_key);
  if v_replay is not null then
    return jsonb_build_object(
      'locationId', v_replay->'metadata'->>'location_id',
      'revisionId', v_replay->>'revisionId',
      'replayed', true
    );
  end if;
  return public.marketplace_upsert_location_checkpoint2_base(
    p_business_id, p_location_id, p_payload, p_idempotency_key
  );
end;
$$;

revoke all on function public.marketplace_upsert_location_checkpoint2_base(uuid,uuid,jsonb,uuid) from public, anon, authenticated;
revoke all on function public.marketplace_upsert_location(uuid,uuid,jsonb,uuid) from public, anon;
grant execute on function public.marketplace_upsert_location(uuid,uuid,jsonb,uuid) to authenticated;

-- New offering creation has the same create-after-timeout duplication risk.
revoke execute on function public.marketplace_upsert_offering(uuid,uuid,jsonb,uuid) from public, anon, authenticated;
alter function public.marketplace_upsert_offering(uuid,uuid,jsonb,uuid)
  rename to marketplace_upsert_offering_checkpoint2_base;

create or replace function public.marketplace_upsert_offering(
  p_business_id uuid,
  p_offering_id uuid default null,
  p_payload jsonb default '{}'::jsonb,
  p_idempotency_key uuid default null
) returns jsonb
language plpgsql
security definer set search_path = '' as $$
declare
  v_actor uuid := private.marketplace_assert_editor(p_business_id);
  v_replay jsonb;
begin
  if p_idempotency_key is null then
    raise exception 'Marketplace mutation idempotency key is required.';
  end if;
  v_replay := private.marketplace_replay_metadata(v_actor, 'OFFERING_SAVED', p_idempotency_key);
  if v_replay is not null then
    return jsonb_build_object(
      'offeringId', v_replay->'metadata'->>'offering_id',
      'revisionId', v_replay->>'revisionId',
      'replayed', true
    );
  end if;
  return public.marketplace_upsert_offering_checkpoint2_base(
    p_business_id, p_offering_id, p_payload, p_idempotency_key
  );
end;
$$;

revoke all on function public.marketplace_upsert_offering_checkpoint2_base(uuid,uuid,jsonb,uuid) from public, anon, authenticated;
revoke all on function public.marketplace_upsert_offering(uuid,uuid,jsonb,uuid) from public, anon;
grant execute on function public.marketplace_upsert_offering(uuid,uuid,jsonb,uuid) to authenticated;

-- Deletion retries should report the already-committed deletion as success.
revoke execute on function public.marketplace_delete_draft_media(uuid,uuid,uuid) from public, anon, authenticated;
alter function public.marketplace_delete_draft_media(uuid,uuid,uuid)
  rename to marketplace_delete_draft_media_checkpoint2_base;

create or replace function public.marketplace_delete_draft_media(
  p_business_id uuid,
  p_asset_id uuid,
  p_idempotency_key uuid default null
) returns jsonb
language plpgsql
security definer set search_path = '' as $$
declare
  v_actor uuid := private.marketplace_assert_editor(p_business_id);
  v_replay jsonb;
begin
  if p_idempotency_key is null then
    raise exception 'Marketplace mutation idempotency key is required.';
  end if;
  v_replay := private.marketplace_replay_metadata(v_actor, 'MEDIA_DELETED', p_idempotency_key);
  if v_replay is not null then
    return jsonb_build_object('assetId', p_asset_id, 'state', 'DELETED', 'replayed', true);
  end if;
  return public.marketplace_delete_draft_media_checkpoint2_base(
    p_business_id, p_asset_id, p_idempotency_key
  );
end;
$$;

revoke all on function public.marketplace_delete_draft_media_checkpoint2_base(uuid,uuid,uuid) from public, anon, authenticated;
revoke all on function public.marketplace_delete_draft_media(uuid,uuid,uuid) from public, anon;
grant execute on function public.marketplace_delete_draft_media(uuid,uuid,uuid) to authenticated;

revoke execute on function public.marketplace_delete_review(uuid,uuid) from public, anon, authenticated;
alter function public.marketplace_delete_review(uuid,uuid)
  rename to marketplace_delete_review_checkpoint2_base;

create or replace function public.marketplace_delete_review(
  p_review_id uuid,
  p_idempotency_key uuid default null
) returns jsonb
language plpgsql
security definer set search_path = '' as $$
declare
  v_actor uuid := private.marketplace_actor();
  v_replay jsonb;
begin
  if p_idempotency_key is null then
    raise exception 'Marketplace mutation idempotency key is required.';
  end if;
  v_replay := private.marketplace_replay_metadata(v_actor, 'REVIEW_DELETED', p_idempotency_key);
  if v_replay is not null then
    return jsonb_build_object('reviewId', p_review_id, 'deleted', true, 'replayed', true);
  end if;
  return public.marketplace_delete_review_checkpoint2_base(p_review_id, p_idempotency_key);
end;
$$;

revoke all on function public.marketplace_delete_review_checkpoint2_base(uuid,uuid) from public, anon, authenticated;
revoke all on function public.marketplace_delete_review(uuid,uuid) from public, anon;
grant execute on function public.marketplace_delete_review(uuid,uuid) to authenticated;

-- Reports get a real correlation key. The former four-argument function is retired.
revoke execute on function public.marketplace_report_review(uuid,uuid,text,text) from public, anon, authenticated;
alter function public.marketplace_report_review(uuid,uuid,text,text)
  rename to marketplace_report_review_checkpoint2_base;

create or replace function public.marketplace_report_review(
  p_review_id uuid default null,
  p_response_id uuid default null,
  p_reason_code text default 'OTHER',
  p_details text default '',
  p_idempotency_key uuid default null
) returns jsonb
language plpgsql
security definer set search_path = '' as $$
declare
  v_actor uuid := private.marketplace_actor();
  v_replay jsonb;
  v_report uuid;
  v_business uuid;
begin
  if p_idempotency_key is null then
    raise exception 'Marketplace mutation idempotency key is required.';
  end if;
  if ((p_review_id is not null)::integer + (p_response_id is not null)::integer) <> 1 then
    raise exception 'Report one review or one response.';
  end if;

  v_replay := private.marketplace_replay_metadata(v_actor, 'REVIEW_REPORTED', p_idempotency_key);
  if v_replay is not null then
    v_report := nullif(v_replay->'metadata'->>'report_id', '')::uuid;
    if v_report is null or not exists (
      select 1
      from public.marketplace_review_reports rr
      where rr.id = v_report
        and rr.review_id is not distinct from p_review_id
        and rr.response_id is not distinct from p_response_id
    ) then
      raise exception 'Marketplace idempotency key was already used for another report.';
    end if;
    return jsonb_build_object('reportId', v_report, 'state', 'OPEN', 'replayed', true);
  end if;

  if p_review_id is not null then
    select r.business_id into v_business
    from public.marketplace_reviews r
    where r.id = p_review_id;
  else
    select rs.business_id into v_business
    from public.marketplace_review_responses rs
    where rs.id = p_response_id;
  end if;
  if v_business is null then
    raise exception 'This Marketplace review target is not available.';
  end if;

  insert into public.marketplace_review_reports(
    review_id, response_id, reporter_id, reason_code, details
  ) values (
    p_review_id, p_response_id, v_actor, p_reason_code,
    left(trim(coalesce(p_details, '')), 2000)
  ) returning id into v_report;

  perform private.ops_upsert_work_item(
    'MARKETPLACE_REVIEW_REPORT', v_report, 'MODERATOR'::public.app_role,
    'Review Marketplace report',
    'Marketplace review or response requires moderation.',
    'NORMAL', now() + interval '24 hours'
  );
  perform private.marketplace_audit(
    v_actor, 'REVIEW_REPORTED', v_business, null,
    jsonb_build_object('report_id', v_report), p_idempotency_key
  );
  return jsonb_build_object('reportId', v_report, 'state', 'OPEN');
end;
$$;

revoke all on function public.marketplace_report_review_checkpoint2_base(uuid,uuid,text,text) from public, anon, authenticated;
revoke all on function public.marketplace_report_review(uuid,uuid,text,text,uuid) from public, anon;
grant execute on function public.marketplace_report_review(uuid,uuid,text,text,uuid) to authenticated;

-- Owner archive retries should return the committed terminal state.
revoke execute on function public.marketplace_archive_business(uuid,uuid) from public, anon, authenticated;
alter function public.marketplace_archive_business(uuid,uuid)
  rename to marketplace_archive_business_checkpoint2_base;

create or replace function public.marketplace_archive_business(
  p_business_id uuid,
  p_idempotency_key uuid
) returns jsonb
language plpgsql
security definer set search_path = '' as $$
declare
  v_actor uuid := private.marketplace_assert_editor(p_business_id);
  v_replay jsonb;
begin
  v_replay := private.marketplace_replay_metadata(v_actor, 'ARCHIVED', p_idempotency_key);
  if v_replay is not null then
    return jsonb_build_object('businessId', p_business_id, 'state', 'ARCHIVED', 'replayed', true);
  end if;
  return public.marketplace_archive_business_checkpoint2_base(p_business_id, p_idempotency_key);
end;
$$;

revoke all on function public.marketplace_archive_business_checkpoint2_base(uuid,uuid) from public, anon, authenticated;
revoke all on function public.marketplace_archive_business(uuid,uuid) from public, anon;
grant execute on function public.marketplace_archive_business(uuid,uuid) to authenticated;

-- Administrative decisions are terminal transitions; replay committed outcomes.
revoke execute on function public.marketplace_request_changes(uuid,text,uuid) from public, anon, authenticated;
alter function public.marketplace_request_changes(uuid,text,uuid)
  rename to marketplace_request_changes_checkpoint2_base;

create or replace function public.marketplace_request_changes(
  p_submission_id uuid,
  p_feedback text,
  p_idempotency_key uuid
) returns jsonb
language plpgsql
security definer set search_path = '' as $$
declare
  v_actor uuid := private.marketplace_assert_content_admin();
  v_replay jsonb;
begin
  v_replay := private.marketplace_replay_metadata(v_actor, 'CHANGES_REQUESTED', p_idempotency_key);
  if v_replay is not null then
    return jsonb_build_object('submissionId', p_submission_id, 'state', 'CHANGES_REQUESTED', 'replayed', true);
  end if;
  return public.marketplace_request_changes_checkpoint2_base(p_submission_id, p_feedback, p_idempotency_key);
end;
$$;

revoke all on function public.marketplace_request_changes_checkpoint2_base(uuid,text,uuid) from public, anon, authenticated;
revoke all on function public.marketplace_request_changes(uuid,text,uuid) from public, anon;
grant execute on function public.marketplace_request_changes(uuid,text,uuid) to authenticated;

revoke execute on function public.marketplace_approve_and_publish(uuid,uuid) from public, anon, authenticated;
alter function public.marketplace_approve_and_publish(uuid,uuid)
  rename to marketplace_approve_and_publish_checkpoint2_base;

create or replace function public.marketplace_approve_and_publish(
  p_submission_id uuid,
  p_idempotency_key uuid
) returns jsonb
language plpgsql
security definer set search_path = '' as $$
declare
  v_actor uuid := private.marketplace_assert_content_admin();
  v_replay jsonb;
begin
  v_replay := private.marketplace_replay_metadata(v_actor, 'PUBLISHED', p_idempotency_key);
  if v_replay is not null then
    return jsonb_build_object(
      'submissionId', p_submission_id,
      'businessId', v_replay->>'businessId',
      'revisionId', v_replay->>'revisionId',
      'state', 'PUBLISHED',
      'replayed', true
    );
  end if;
  return public.marketplace_approve_and_publish_checkpoint2_base(p_submission_id, p_idempotency_key);
end;
$$;

revoke all on function public.marketplace_approve_and_publish_checkpoint2_base(uuid,uuid) from public, anon, authenticated;
revoke all on function public.marketplace_approve_and_publish(uuid,uuid) from public, anon;
grant execute on function public.marketplace_approve_and_publish(uuid,uuid) to authenticated;

revoke execute on function public.marketplace_reject_submission(uuid,text,uuid) from public, anon, authenticated;
alter function public.marketplace_reject_submission(uuid,text,uuid)
  rename to marketplace_reject_submission_checkpoint2_base;

create or replace function public.marketplace_reject_submission(
  p_submission_id uuid,
  p_feedback text,
  p_idempotency_key uuid
) returns jsonb
language plpgsql
security definer set search_path = '' as $$
declare
  v_actor uuid := private.marketplace_assert_content_admin();
  v_replay jsonb;
begin
  v_replay := private.marketplace_replay_metadata(v_actor, 'SUBMISSION_REJECTED', p_idempotency_key);
  if v_replay is not null then
    return jsonb_build_object('submissionId', p_submission_id, 'state', 'REJECTED', 'replayed', true);
  end if;
  return public.marketplace_reject_submission_checkpoint2_base(p_submission_id, p_feedback, p_idempotency_key);
end;
$$;

revoke all on function public.marketplace_reject_submission_checkpoint2_base(uuid,text,uuid) from public, anon, authenticated;
revoke all on function public.marketplace_reject_submission(uuid,text,uuid) from public, anon;
grant execute on function public.marketplace_reject_submission(uuid,text,uuid) to authenticated;

revoke execute on function public.marketplace_suspend_business(uuid,text,uuid) from public, anon, authenticated;
alter function public.marketplace_suspend_business(uuid,text,uuid)
  rename to marketplace_suspend_business_checkpoint2_base;

create or replace function public.marketplace_suspend_business(
  p_business_id uuid,
  p_reason text,
  p_idempotency_key uuid
) returns jsonb
language plpgsql
security definer set search_path = '' as $$
declare
  v_actor uuid := private.marketplace_assert_moderator();
  v_replay jsonb;
begin
  v_replay := private.marketplace_replay_metadata(v_actor, 'SUSPENDED', p_idempotency_key);
  if v_replay is not null then
    return jsonb_build_object('businessId', p_business_id, 'state', 'SUSPENDED', 'replayed', true);
  end if;
  return public.marketplace_suspend_business_checkpoint2_base(p_business_id, p_reason, p_idempotency_key);
end;
$$;

revoke all on function public.marketplace_suspend_business_checkpoint2_base(uuid,text,uuid) from public, anon, authenticated;
revoke all on function public.marketplace_suspend_business(uuid,text,uuid) from public, anon;
grant execute on function public.marketplace_suspend_business(uuid,text,uuid) to authenticated;

revoke execute on function public.marketplace_reinstate_business(uuid,uuid) from public, anon, authenticated;
alter function public.marketplace_reinstate_business(uuid,uuid)
  rename to marketplace_reinstate_business_checkpoint2_base;

create or replace function public.marketplace_reinstate_business(
  p_business_id uuid,
  p_idempotency_key uuid
) returns jsonb
language plpgsql
security definer set search_path = '' as $$
declare
  v_actor uuid := private.marketplace_assert_moderator();
  v_replay jsonb;
begin
  v_replay := private.marketplace_replay_metadata(v_actor, 'REINSTATED', p_idempotency_key);
  if v_replay is not null then
    return jsonb_build_object('businessId', p_business_id, 'state', 'PUBLISHED', 'replayed', true);
  end if;
  return public.marketplace_reinstate_business_checkpoint2_base(p_business_id, p_idempotency_key);
end;
$$;

revoke all on function public.marketplace_reinstate_business_checkpoint2_base(uuid,uuid) from public, anon, authenticated;
revoke all on function public.marketplace_reinstate_business(uuid,uuid) from public, anon;
grant execute on function public.marketplace_reinstate_business(uuid,uuid) to authenticated;

commit;
