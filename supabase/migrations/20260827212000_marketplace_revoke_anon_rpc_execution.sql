begin;

do $$
declare function_name text;
begin
  for function_name in
    select p.oid::regprocedure::text
    from pg_proc p
    join pg_namespace n on n.oid = p.pronamespace
    where n.nspname = 'public' and p.proname like 'marketplace_%'
  loop
    execute format('revoke execute on function %s from public, anon', function_name);
  end loop;
end;
$$;

-- The feature contract is authenticated-only; individual SECURITY DEFINER functions still
-- perform internal membership/role checks before returning or mutating Marketplace data.
grant execute on function public.marketplace_create_business_draft(text,uuid), public.marketplace_save_identity(uuid,jsonb,uuid), public.marketplace_upsert_location(uuid,uuid,jsonb,uuid), public.marketplace_replace_location_hours(uuid,uuid,jsonb,jsonb,uuid), public.marketplace_upsert_offering(uuid,uuid,jsonb,uuid), public.marketplace_begin_media_upload(uuid,text,text,bigint,text,uuid), public.marketplace_finalize_media_upload(uuid,uuid,integer,integer,jsonb,uuid), public.marketplace_delete_draft_media(uuid,uuid,uuid), public.marketplace_submit_business(uuid,uuid), public.marketplace_cancel_submission(uuid,uuid), public.marketplace_save_business(uuid,boolean), public.marketplace_submit_review(uuid,smallint,text,text,uuid), public.marketplace_delete_review(uuid,uuid), public.marketplace_submit_owner_response(uuid,text,uuid), public.marketplace_report_review(uuid,uuid,text,text), public.marketplace_vote_review_helpful(uuid,boolean), public.marketplace_invite_member(uuid,text,text,text,uuid), public.marketplace_accept_invitation(text), public.marketplace_revoke_member(uuid,uuid,uuid), public.marketplace_transfer_ownership(uuid,uuid,uuid), public.marketplace_assign_submission(uuid,uuid), public.marketplace_request_changes(uuid,text,uuid), public.marketplace_approve_and_publish(uuid,uuid), public.marketplace_reject_submission(uuid,text,uuid), public.marketplace_suspend_business(uuid,text,uuid), public.marketplace_reinstate_business(uuid,uuid), public.marketplace_archive_business(uuid,uuid), public.marketplace_schedule_featured(uuid,timestamptz,timestamptz,integer,uuid), public.marketplace_cancel_featured(uuid,uuid), public.marketplace_home(text,double precision,double precision), public.marketplace_nearby_businesses(double precision,double precision,integer,integer), public.marketplace_search_businesses(text,uuid,text,double precision,double precision,integer,numeric,boolean,text,integer), public.marketplace_businesses_in_view(double precision,double precision,double precision,double precision,integer), public.marketplace_business_detail(text,double precision,double precision), public.marketplace_business_reviews(uuid,integer), public.marketplace_my_businesses(), public.marketplace_business_editor(uuid), public.marketplace_submission_status(uuid), public.marketplace_my_reviews(), public.marketplace_admin_queue(integer), public.marketplace_admin_submission_detail(uuid), public.marketplace_admin_moderate_review(uuid,text,text), public.marketplace_saved_businesses(), public.marketplace_my_invitations() to authenticated;

commit;
