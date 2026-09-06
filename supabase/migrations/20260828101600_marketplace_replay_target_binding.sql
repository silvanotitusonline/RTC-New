begin;

-- Bind a replay key to the exact target that originally produced its audit event.
-- This prevents a caller from reusing a valid key for another business/entity and
-- receiving a misleading replay response instead of executing the requested mutation.

create or replace function private.marketplace_assert_replay_target(
  p_replay jsonb,
  p_business_id uuid default null,
  p_metadata_key text default null,
  p_target_id uuid default null,
  p_error text default 'Marketplace idempotency key was already used for another target.'
) returns void
language plpgsql
stable
security definer set search_path = '' as $$
declare
  v_business uuid;
  v_target uuid;
begin
  if p_replay is null then
    return;
  end if;

  if p_business_id is not null then
    v_business := nullif(p_replay->>'businessId', '')::uuid;
    if v_business is distinct from p_business_id then
      raise exception '%', p_error;
    end if;
  end if;

  if p_metadata_key is not null and p_target_id is not null then
    v_target := nullif((p_replay->'metadata')->>p_metadata_key, '')::uuid;
    if v_target is distinct from p_target_id then
      raise exception '%', p_error;
    end if;
  end if;
end;
$$;

revoke all on function private.marketplace_assert_replay_target(jsonb,uuid,text,uuid,text)
  from public, anon, authenticated;

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
  perform private.marketplace_assert_replay_target(
    v_replay, p_business_id, null, null,
    'Marketplace idempotency key was already used for another business.'
  );
  if p_location_id is not null then
    perform private.marketplace_assert_replay_target(
      v_replay, null, 'location_id', p_location_id,
      'Marketplace idempotency key was already used for another location.'
    );
  end if;
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
  perform private.marketplace_assert_replay_target(
    v_replay, p_business_id, null, null,
    'Marketplace idempotency key was already used for another business.'
  );
  if p_offering_id is not null then
    perform private.marketplace_assert_replay_target(
      v_replay, null, 'offering_id', p_offering_id,
      'Marketplace idempotency key was already used for another offering.'
    );
  end if;
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
  perform private.marketplace_assert_replay_target(
    v_replay, p_business_id, null, null,
    'Marketplace idempotency key was already used for another business.'
  );
  perform private.marketplace_assert_replay_target(
    v_replay, null, 'asset_id', p_asset_id,
    'Marketplace idempotency key was already used for another media asset.'
  );
  if v_replay is not null then
    return jsonb_build_object('assetId', p_asset_id, 'state', 'DELETED', 'replayed', true);
  end if;
  return public.marketplace_delete_draft_media_checkpoint2_base(
    p_business_id, p_asset_id, p_idempotency_key
  );
end;
$$;

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
  perform private.marketplace_assert_replay_target(
    v_replay, null, 'review_id', p_review_id,
    'Marketplace idempotency key was already used for another review.'
  );
  if v_replay is not null then
    return jsonb_build_object('reviewId', p_review_id, 'deleted', true, 'replayed', true);
  end if;
  return public.marketplace_delete_review_checkpoint2_base(p_review_id, p_idempotency_key);
end;
$$;

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
  perform private.marketplace_assert_replay_target(
    v_replay, p_business_id, null, null,
    'Marketplace idempotency key was already used for another business.'
  );
  if v_replay is not null then
    return jsonb_build_object('businessId', p_business_id, 'state', 'ARCHIVED', 'replayed', true);
  end if;
  return public.marketplace_archive_business_checkpoint2_base(p_business_id, p_idempotency_key);
end;
$$;

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
  perform private.marketplace_assert_replay_target(
    v_replay, null, 'submission_id', p_submission_id,
    'Marketplace idempotency key was already used for another submission.'
  );
  if v_replay is not null then
    return jsonb_build_object('submissionId', p_submission_id, 'state', 'CHANGES_REQUESTED', 'replayed', true);
  end if;
  return public.marketplace_request_changes_checkpoint2_base(p_submission_id, p_feedback, p_idempotency_key);
end;
$$;

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
  perform private.marketplace_assert_replay_target(
    v_replay, null, 'submission_id', p_submission_id,
    'Marketplace idempotency key was already used for another submission.'
  );
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
  perform private.marketplace_assert_replay_target(
    v_replay, null, 'submission_id', p_submission_id,
    'Marketplace idempotency key was already used for another submission.'
  );
  if v_replay is not null then
    return jsonb_build_object('submissionId', p_submission_id, 'state', 'REJECTED', 'replayed', true);
  end if;
  return public.marketplace_reject_submission_checkpoint2_base(p_submission_id, p_feedback, p_idempotency_key);
end;
$$;

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
  perform private.marketplace_assert_replay_target(
    v_replay, p_business_id, null, null,
    'Marketplace idempotency key was already used for another business.'
  );
  if v_replay is not null then
    return jsonb_build_object('businessId', p_business_id, 'state', 'SUSPENDED', 'replayed', true);
  end if;
  return public.marketplace_suspend_business_checkpoint2_base(p_business_id, p_reason, p_idempotency_key);
end;
$$;

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
  perform private.marketplace_assert_replay_target(
    v_replay, p_business_id, null, null,
    'Marketplace idempotency key was already used for another business.'
  );
  if v_replay is not null then
    return jsonb_build_object('businessId', p_business_id, 'state', 'PUBLISHED', 'replayed', true);
  end if;
  return public.marketplace_reinstate_business_checkpoint2_base(p_business_id, p_idempotency_key);
end;
$$;

-- Redundant with the previous migration, but intentionally explicit after replacement.
revoke all on function public.marketplace_upsert_location(uuid,uuid,jsonb,uuid) from public, anon;
grant execute on function public.marketplace_upsert_location(uuid,uuid,jsonb,uuid) to authenticated;
revoke all on function public.marketplace_upsert_offering(uuid,uuid,jsonb,uuid) from public, anon;
grant execute on function public.marketplace_upsert_offering(uuid,uuid,jsonb,uuid) to authenticated;
revoke all on function public.marketplace_delete_draft_media(uuid,uuid,uuid) from public, anon;
grant execute on function public.marketplace_delete_draft_media(uuid,uuid,uuid) to authenticated;
revoke all on function public.marketplace_delete_review(uuid,uuid) from public, anon;
grant execute on function public.marketplace_delete_review(uuid,uuid) to authenticated;
revoke all on function public.marketplace_archive_business(uuid,uuid) from public, anon;
grant execute on function public.marketplace_archive_business(uuid,uuid) to authenticated;
revoke all on function public.marketplace_request_changes(uuid,text,uuid) from public, anon;
grant execute on function public.marketplace_request_changes(uuid,text,uuid) to authenticated;
revoke all on function public.marketplace_approve_and_publish(uuid,uuid) from public, anon;
grant execute on function public.marketplace_approve_and_publish(uuid,uuid) to authenticated;
revoke all on function public.marketplace_reject_submission(uuid,text,uuid) from public, anon;
grant execute on function public.marketplace_reject_submission(uuid,text,uuid) to authenticated;
revoke all on function public.marketplace_suspend_business(uuid,text,uuid) from public, anon;
grant execute on function public.marketplace_suspend_business(uuid,text,uuid) to authenticated;
revoke all on function public.marketplace_reinstate_business(uuid,uuid) from public, anon;
grant execute on function public.marketplace_reinstate_business(uuid,uuid) to authenticated;

commit;
