begin;

-- Purpose: Make post-PR-#6 Marketplace and UI-configuration RPC-only boundaries explicit.
-- Affected objects: 24 Marketplace tables and 3 UI-configuration tables listed below.
-- Grants/RLS effect: RLS remains enabled; anon/authenticated direct table privileges are revoked;
--                    one restrictive false policy documents and enforces direct-client denial.
-- Rollback consideration: rollback requires a new forward migration after each table's RPC contract is reviewed.

do $rpc_only_hardening$
declare
  v_table text;
  v_tables constant text[] := array[
    'marketplace_audit_events',
    'marketplace_business_claims',
    'marketplace_business_invitations',
    'marketplace_business_locations',
    'marketplace_business_members',
    'marketplace_business_rating_stats',
    'marketplace_business_revisions',
    'marketplace_business_verifications',
    'marketplace_businesses',
    'marketplace_categories',
    'marketplace_favorites',
    'marketplace_featured_placements',
    'marketplace_location_hour_exceptions',
    'marketplace_location_hours',
    'marketplace_media_assets',
    'marketplace_offering_locations',
    'marketplace_offerings',
    'marketplace_review_helpful_votes',
    'marketplace_review_reports',
    'marketplace_review_responses',
    'marketplace_review_versions',
    'marketplace_reviews',
    'marketplace_revision_categories',
    'marketplace_submissions',
    'ui_configuration_assets',
    'ui_configuration_events',
    'ui_configuration_versions'
  ];
begin
  foreach v_table in array v_tables loop
    if to_regclass(format('public.%I', v_table)) is null then
      raise exception 'Expected RPC-only table public.% is missing; reconcile schema drift before hardening.', v_table;
    end if;

    execute format('alter table public.%I enable row level security', v_table);
    execute format('revoke all on table public.%I from anon, authenticated', v_table);
    execute format('drop policy if exists rpc_only_deny_direct_client_access on public.%I', v_table);
    execute format(
      'create policy rpc_only_deny_direct_client_access on public.%I as restrictive for all to anon, authenticated using (false) with check (false)',
      v_table
    );
    execute format(
      'comment on table public.%I is %L',
      v_table,
      'RPC-only security boundary. Direct anon/authenticated table access is intentionally denied; approved SECURITY DEFINER RPCs must authorize callers server-side.'
    );
  end loop;
end
$rpc_only_hardening$;

commit;
