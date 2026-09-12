-- Release candidate performance cleanup.
-- Preserves authorization semantics; does not remove indexes merely because they are young/unused.

-- RLS init-plan: ensure auth.uid() is evaluated once per statement.
drop policy if exists community_follows_own on public.community_topic_follows;
create policy community_follows_own on public.community_topic_follows
for select to authenticated
using (user_id = (select auth.uid()));

drop policy if exists community_poll_votes_own_or_count on public.community_poll_votes;
create policy community_poll_votes_own_or_count on public.community_poll_votes
for select to authenticated
using (
  voter_id = (select auth.uid())
  or exists (
    select 1 from public.community_polls p
    where p.post_id = community_poll_votes.poll_post_id
      and private.is_public_community_post(p.post_id)
  )
);

-- Avoid duplicate permissive SELECT policies. Read policies retain staff/editor visibility;
-- write policies are deliberately action-specific.
drop policy if exists app_content_admin_write on public.app_content;
create policy app_content_admin_insert on public.app_content for insert to authenticated
with check ((select private.has_any_role(array['CONTENT_EDITOR'::public.app_role,'SYSTEM_ADMIN'::public.app_role])));
create policy app_content_admin_update on public.app_content for update to authenticated
using ((select private.has_any_role(array['CONTENT_EDITOR'::public.app_role,'SYSTEM_ADMIN'::public.app_role])))
with check ((select private.has_any_role(array['CONTENT_EDITOR'::public.app_role,'SYSTEM_ADMIN'::public.app_role])));
create policy app_content_admin_delete on public.app_content for delete to authenticated
using ((select private.has_any_role(array['CONTENT_EDITOR'::public.app_role,'SYSTEM_ADMIN'::public.app_role])));

-- Directory read policies must continue exposing drafts to content authority.
do $$
declare t text;
begin
  foreach t in array array['directory_centres','directory_opportunities','directory_projects'] loop
    execute format('drop policy if exists %I on public.%I', t || '_manage_editor_or_admin', t);
    execute format('drop policy if exists %I on public.%I', t || '_public_read', t);
    execute format(
      'create policy %I on public.%I for select to anon,authenticated using (visibility = ''PUBLISHED'' or (select private.has_any_role(array[''CONTENT_EDITOR''::public.app_role,''SYSTEM_ADMIN''::public.app_role])))',
      t || '_public_read', t
    );
    execute format(
      'create policy %I on public.%I for insert to authenticated with check ((select private.has_any_role(array[''CONTENT_EDITOR''::public.app_role,''SYSTEM_ADMIN''::public.app_role])))',
      t || '_manage_insert', t
    );
    execute format(
      'create policy %I on public.%I for update to authenticated using ((select private.has_any_role(array[''CONTENT_EDITOR''::public.app_role,''SYSTEM_ADMIN''::public.app_role]))) with check ((select private.has_any_role(array[''CONTENT_EDITOR''::public.app_role,''SYSTEM_ADMIN''::public.app_role])))',
      t || '_manage_update', t
    );
    execute format(
      'create policy %I on public.%I for delete to authenticated using ((select private.has_any_role(array[''CONTENT_EDITOR''::public.app_role,''SYSTEM_ADMIN''::public.app_role])))',
      t || '_manage_delete', t
    );
  end loop;
end $$;

drop policy if exists help_articles_write_authority on public.help_articles;
create policy help_articles_insert_authority on public.help_articles for insert to authenticated
with check ((select private.is_content_authority()));
create policy help_articles_update_authority on public.help_articles for update to authenticated
using ((select private.is_content_authority())) with check ((select private.is_content_authority()));
create policy help_articles_delete_authority on public.help_articles for delete to authenticated
using ((select private.is_content_authority()));

drop policy if exists templates_write_content_authority on public.official_notice_templates;
create policy templates_insert_content_authority on public.official_notice_templates for insert to authenticated
with check ((select private.is_content_authority()));
create policy templates_update_content_authority on public.official_notice_templates for update to authenticated
using ((select private.is_content_authority())) with check ((select private.is_content_authority()));
create policy templates_delete_content_authority on public.official_notice_templates for delete to authenticated
using ((select private.is_content_authority()));

-- Remove only indexes confirmed byte-for-byte equivalent by the advisor / pg_indexes audit.
drop index if exists public.case_messages_case_idx;
drop index if exists public.community_cases_resident_idx;
-- Keep the unique-constraint backing index community_post_media_post_id_position_key.
drop index if exists public.community_post_media_post_position_unique;

-- Cover remaining advisor-identified FK paths used by lifecycle, moderation, directory and operations flows.
create index if not exists idx_bulk_action_items_batch_id on public.bulk_action_items(batch_id);
create index if not exists idx_community_moderation_actions_actor_id on public.community_moderation_actions(actor_id);
create index if not exists idx_content_media_deleted_by on public.content_media(deleted_by);
create index if not exists idx_content_media_uploaded_by on public.content_media(uploaded_by);
create index if not exists idx_directory_centres_created_by on public.directory_centres(created_by);
create index if not exists idx_directory_centres_updated_by on public.directory_centres(updated_by);
create index if not exists idx_directory_opportunities_created_by on public.directory_opportunities(created_by);
create index if not exists idx_directory_opportunities_updated_by on public.directory_opportunities(updated_by);
create index if not exists idx_directory_projects_created_by on public.directory_projects(created_by);
create index if not exists idx_directory_projects_updated_by on public.directory_projects(updated_by);
create index if not exists idx_help_articles_created_by on public.help_articles(created_by);
create index if not exists idx_help_articles_updated_by on public.help_articles(updated_by);
create index if not exists idx_moderation_appeals_appellant_id on public.moderation_appeals(appellant_id);
create index if not exists idx_moderation_appeals_decided_by on public.moderation_appeals(decided_by);
create index if not exists idx_moderation_items_reporter_id on public.moderation_items(reporter_id);
create index if not exists idx_moderation_items_resolved_by on public.moderation_items(resolved_by);
create index if not exists idx_official_notice_lifecycle_actor_id on public.official_notice_lifecycle_events(actor_id);
create index if not exists idx_official_notice_reviews_notice_id on public.official_notice_reviews(notice_id);
create index if not exists idx_official_notice_reviews_reviewer_id on public.official_notice_reviews(reviewer_id);
create index if not exists idx_official_notice_templates_created_by on public.official_notice_templates(created_by);
create index if not exists idx_official_notices_corrects_notice_id on public.official_notices(corrects_notice_id);
create index if not exists idx_official_notices_moderated_by on public.official_notices(moderated_by);
create index if not exists idx_official_notices_published_by on public.official_notices(published_by);
create index if not exists idx_official_notices_reviewed_by on public.official_notices(reviewed_by);
create index if not exists idx_operational_control_events_actor_id on public.operational_control_events(actor_id);
create index if not exists idx_operational_control_events_control_id on public.operational_control_events(control_id);
create index if not exists idx_operational_controls_ended_by on public.operational_controls(ended_by);
create index if not exists idx_operational_controls_incident_id on public.operational_controls(incident_id);
create index if not exists idx_operational_controls_opened_by on public.operational_controls(opened_by);
create index if not exists idx_operational_incidents_opened_by on public.operational_incidents(opened_by);
create index if not exists idx_operational_incidents_owner_id on public.operational_incidents(owner_id);
create index if not exists idx_operational_incidents_resolved_by on public.operational_incidents(resolved_by);
create index if not exists idx_operational_work_assign_actor_id on public.operational_work_assignment_events(actor_id);
create index if not exists idx_operational_work_assign_new_owner_id on public.operational_work_assignment_events(new_owner_id);
create index if not exists idx_operational_work_assign_previous_owner_id on public.operational_work_assignment_events(previous_owner_id);
create index if not exists idx_user_feedback_reporter_id on public.user_feedback(reporter_id);
create index if not exists idx_user_roles_granted_by on public.user_roles(granted_by);
