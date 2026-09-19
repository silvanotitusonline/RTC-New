-- Moderator post lifecycle hardening.
-- HIDE remains reversible moderation. REMOVE is irreversible content removal
-- and is explicitly audited; moderation records are retained as the decision trail.

alter table public.community_moderation_actions
  drop constraint if exists community_moderation_actions_action_type_check;
alter table public.community_moderation_actions
  add constraint community_moderation_actions_action_type_check
  check (action_type in ('HIDE', 'RESTORE', 'LOCK', 'UNLOCK', 'PIN', 'UNPIN', 'REMOVE', 'REMOVE_MEDIA'));

create or replace function private.purge_community_post_dependents(p_post_id uuid, p_preserve_moderation boolean default false)
returns void
language plpgsql
security definer
set search_path = public, pg_temp
as $function$
begin
  -- Generic subject tables also store comment subjects and therefore cannot
  -- all use a direct FK. Remove only post subjects here. Audit events and
  -- moderation actions remain as the non-content record of the decision.
  delete from public.community_reactions where subject_type = 'POST' and subject_id = p_post_id;
  delete from public.community_reports where subject_type = 'POST' and subject_id = p_post_id;
  delete from public.moderation_appeals where subject_type = 'POST' and subject_id = p_post_id;
  if p_preserve_moderation then
    update public.operational_work_items
       set state = 'RESOLVED', resolved_at = now(), updated_at = now()
     where source_type = 'MODERATION_REPORT'
       and source_id in (select id from public.moderation_items where subject_type = 'POST' and subject_id = p_post_id);
  else
    delete from public.operational_work_items
     where source_type = 'MODERATION_REPORT'
       and source_id in (select id from public.moderation_items where subject_type = 'POST' and subject_id = p_post_id);
    delete from public.moderation_items where subject_type = 'POST' and subject_id = p_post_id;
  end if;
end;
$function$;
revoke all on function private.purge_community_post_dependents(uuid, boolean) from public, anon, authenticated;

create or replace function public.delete_community_post(p_post_id uuid)
returns void
language plpgsql
security definer
set search_path = public, pg_temp
as $function$
begin
  if auth.uid() is null then raise exception 'AUTH_REQUIRED'; end if;
  if not exists (select 1 from public.community_posts where id = p_post_id and author_id = auth.uid() and deleted_at is null) then
    raise exception 'DELETE_NOT_ALLOWED';
  end if;
  perform private.purge_community_post_dependents(p_post_id);
  delete from public.community_posts where id = p_post_id and author_id = auth.uid() and deleted_at is null;
  if not found then raise exception 'DELETE_NOT_ALLOWED'; end if;
  insert into public.audit_events(actor_id, event_type, entity_type, entity_id, result, metadata, source)
  values (auth.uid(), 'COMMUNITY_POST_DELETED', 'community_post', p_post_id, 'SUCCESS', jsonb_build_object('hardDelete', true), 'community-rpc');
end;
$function$;
revoke all on function public.delete_community_post(uuid) from public, anon;
grant execute on function public.delete_community_post(uuid) to authenticated, service_role;

create or replace function public.moderate_community_item(
  p_subject_type text,
  p_subject_id uuid,
  p_action_type text,
  p_reason text,
  p_pinned_until timestamptz default null
)
returns void
language plpgsql
security definer
set search_path = public, pg_temp
as $function$
declare
  v_actor uuid := private.ops_assert_staff();
  v_action text := upper(trim(coalesce(p_action_type, '')));
  v_subject text := upper(trim(coalesce(p_subject_type, '')));
begin
  if not private.community_staff() then raise exception 'MODERATOR_REQUIRED'; end if;
  if char_length(trim(coalesce(p_reason, ''))) not between 3 and 1000 then raise exception 'MODERATION_REASON_REQUIRED'; end if;

  if v_subject = 'POST' then
    if v_action = 'REMOVE' then
      perform private.purge_community_post_dependents(p_subject_id, true);
      delete from public.community_posts where id = p_subject_id;
    elsif v_action = 'HIDE' then
      update public.community_posts set state = 'HIDDEN_BY_MODERATION', updated_at = now() where id = p_subject_id;
    elsif v_action = 'RESTORE' then
      update public.community_posts set state = 'PUBLISHED', deleted_at = null, updated_at = now() where id = p_subject_id;
    elsif v_action = 'LOCK' then
      update public.community_posts set is_locked = true, state = 'LOCKED', updated_at = now() where id = p_subject_id;
    elsif v_action = 'UNLOCK' then
      update public.community_posts set is_locked = false, state = 'PUBLISHED', updated_at = now() where id = p_subject_id;
    elsif v_action = 'PIN' then
      update public.community_posts set pinned_until = p_pinned_until, updated_at = now() where id = p_subject_id and p_pinned_until > now();
    elsif v_action = 'UNPIN' then
      update public.community_posts set pinned_until = null, updated_at = now() where id = p_subject_id;
    else
      raise exception 'INVALID_MODERATION_ACTION';
    end if;
  elsif v_subject = 'COMMENT' then
    if v_action = 'HIDE' then
      update public.community_comments set state = 'HIDDEN_BY_MODERATION', updated_at = now() where id = p_subject_id;
    elsif v_action = 'RESTORE' then
      update public.community_comments set state = 'PUBLISHED', deleted_at = null, updated_at = now() where id = p_subject_id;
    else
      raise exception 'INVALID_MODERATION_ACTION';
    end if;
  else
    raise exception 'INVALID_SUBJECT';
  end if;

  if not found then raise exception 'SUBJECT_UNAVAILABLE'; end if;
  perform private.ensure_community_profile_for_account(v_actor);
  insert into public.community_moderation_actions(subject_type, subject_id, action_type, reason, pinned_until, actor_id)
  values (v_subject, p_subject_id, v_action, trim(p_reason), p_pinned_until, v_actor);
  perform private.ops_log_audit(v_actor, 'COMMUNITY_MODERATION_' || v_action, lower(v_subject), p_subject_id, 'SUCCESS', jsonb_build_object('reason', trim(p_reason), 'hardDelete', v_action = 'REMOVE'), 'community-rpc');
end;
$function$;
revoke all on function public.moderate_community_item(text, uuid, text, text, timestamptz) from public, anon;
grant execute on function public.moderate_community_item(text, uuid, text, text, timestamptz) to authenticated, service_role;

create or replace function public.moderation_decide_report(p_report_id uuid, p_decision text, p_reason text)
returns void
language plpgsql
security definer
set search_path = public, pg_temp
as $function$
declare
  v_actor uuid := private.ops_assert_staff();
  v_report public.moderation_items%rowtype;
  v_decision text := upper(trim(coalesce(p_decision, '')));
  v_reason text := trim(coalesce(p_reason, ''));
  v_active_count integer;
begin
  if not private.is_moderation_authority() then raise exception 'Moderator access is required.'; end if;
  if v_decision not in ('DISMISS', 'HIDE', 'LOCK', 'REMOVE') then raise exception 'Select a valid moderation decision.'; end if;
  if char_length(v_reason) not between 3 and 1000 then raise exception 'Provide a decision reason between 3 and 1000 characters.'; end if;
  select * into v_report from public.moderation_items where id = p_report_id for update;
  if not found or v_report.subject_type <> 'POST' or v_report.state not in ('OPEN', 'UNDER_REVIEW') then raise exception 'This moderation report is not available.'; end if;

  if v_decision = 'DISMISS' then
    update public.moderation_items set state = 'DISMISSED', resolution_note = v_reason, resolved_by = v_actor where id = v_report.id;
    select count(distinct reporter_id)::integer into v_active_count from public.moderation_items where subject_type = 'POST' and subject_id = v_report.subject_id and state in ('OPEN', 'UNDER_REVIEW');
    update public.community_posts set report_count = v_active_count, state = case when v_active_count < 3 and state = 'LIMITED_PENDING_REVIEW' then 'PUBLISHED' else state end, updated_at = now() where id = v_report.subject_id;
  else
    perform public.moderate_community_item('POST', v_report.subject_id, case when v_decision = 'LOCK' then 'LOCK' when v_decision = 'REMOVE' then 'REMOVE' else 'HIDE' end, v_reason, null);
    update public.moderation_items set state = 'RESOLVED', resolution_note = v_reason, resolved_by = v_actor where subject_type = 'POST' and subject_id = v_report.subject_id and state in ('OPEN', 'UNDER_REVIEW');
    update public.community_posts set report_count = 0, updated_at = now() where id = v_report.subject_id;
  end if;

  update public.operational_work_items set state = 'RESOLVED', resolved_at = now(), updated_at = now() where source_type = 'MODERATION_REPORT' and source_id = v_report.id and state not in ('RESOLVED', 'CANCELLED');
  perform private.ops_log_audit(v_actor, 'MODERATION_REPORT_' || v_decision, 'MODERATION_ITEM', v_report.id, 'SUCCESS', jsonb_build_object('post_id', v_report.subject_id, 'reason', v_reason, 'hardDelete', v_decision = 'REMOVE'));
end;
$function$;
revoke all on function public.moderation_decide_report(uuid, text, text) from public, anon;
grant execute on function public.moderation_decide_report(uuid, text, text) to authenticated, service_role;

comment on function public.moderate_community_item(text, uuid, text, text, timestamptz) is 'Staff moderation action; REMOVE hard-deletes the post and cascade-owned records, while retaining moderation/audit records.';
