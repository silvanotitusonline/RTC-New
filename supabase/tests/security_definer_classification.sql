-- SECURITY DEFINER classification gate for resident modernisation.
--
-- This test intentionally locks the reviewed project-defined SECURITY DEFINER surface
-- executable by `authenticated`. A new/removed/renamed signature changes the fingerprint
-- and fails closed until the full set is reviewed and this baseline is deliberately updated.
-- Extension-owned functions are excluded because their lifecycle belongs to the extension.
--
-- Seven deterministic reviewed surfaces are accepted:
--   * DEPLOYED_CANONICAL: the current Non-Production/deployed canonical catalogue.
--   * ISOLATED_LOCAL_REPLAY: the source-reproducible foundation subset reconstructed by local CI.
--   * ISOLATED_LOCAL_EVENTS_INBOX: local replay plus the reviewed Events/Inbox RPC surface.
--   * ISOLATED_LOCAL_PUBLIC_REPORTS: local replay plus the reviewed Public Reports RPC surface.
--   * ISOLATED_LOCAL_COMBINED: Public Reports and Events/Inbox together after A -> B integration.
--   * PRODUCTION_BETA_COMBINED: the combined surface after verified-only reads and
--     idempotent Community guideline/moderation controls.
--   * PUBLIC_REPORTS_RPC_READ_BOUNDARY: the reviewed production-beta surface after moving
--     sanitized Public Reports reads behind explicit SECURITY DEFINER RPC boundaries.
-- All are pinned by count AND signature fingerprint. Arbitrary local/deployed drift remains
-- UNREVIEWED and fails this gate.
--
-- Disposition policy:
-- INTENTIONAL = reviewed least-privilege use.
-- HARDEN      = keep SECURITY DEFINER but narrow authority/input semantics forward-only.
-- REVOKE      = remove authenticated EXECUTE in a forward migration when not intended.
-- REPLACE     = replace a broken/missing trust primitive with least-privilege behavior.
-- UNREVIEWED  = the exposed function set no longer matches a reviewed baseline; fail.

begin;
set local search_path = extensions, public, pg_catalog;

select plan(12);

create temporary table resident_security_definer_registry on commit drop as
with exposed_definers as (
  select
    p.oid as function_oid,
    n.nspname as schema_name,
    p.proname as function_name,
    pg_get_function_identity_arguments(p.oid) as identity_arguments,
    pg_get_function_result(p.oid) as function_result,
    coalesce(array_to_string(p.proconfig, ','), '') as function_config,
    pg_get_functiondef(p.oid) as definition
  from pg_proc p
  join pg_namespace n on n.oid = p.pronamespace
  where p.prosecdef
    and n.nspname not in ('pg_catalog', 'information_schema')
    and has_function_privilege('authenticated', p.oid, 'EXECUTE')
    and not exists (
      select 1
      from pg_depend d
      join pg_extension e on e.oid = d.refobjid
      where d.classid = 'pg_proc'::regclass
        and d.objid = p.oid
        and d.deptype = 'e'
    )
), normalized as (
  select
    function_oid,
    schema_name,
    function_name,
    identity_arguments,
    function_result,
    function_config,
    definition,
    schema_name || '.' || function_name || '(' || identity_arguments || ')' as signature,
    (definition ~* 'set[[:space:]]+search_path') as fixed_search_path,
    (
      definition ~* 'auth\.uid\(\)|auth\.jwt\(\)|private\.(access_|marketplace_|has_any_role|has_role|is_any_staff|community_|can_view_|ui_configuration_|civic_report_|assert_community_event_manager|resident_inbox_source_visible)'
    ) as contains_caller_or_authority_guard
  from exposed_definers
), reviewed_set as (
  select
    count(*)::bigint as exposed_count,
    md5(string_agg(signature, E'\n' order by signature)) as exposed_fingerprint
  from normalized
), approved_baselines(exposed_count, exposed_fingerprint) as (
  values
    (182::bigint, '66cda900664e76083670f8186087aece'::text),
    (142::bigint, '3e6d958c14e0fa7c1509490af1ca7fff'::text),
    (149::bigint, '89b13d92160e4f33af98f41a715a0e27'::text),
    (152::bigint, 'e6de756df6b23c7a27bea99bc0d62672'::text),
    (159::bigint, '96c90025230ec19ee868ca38219e3632'::text),
    (162::bigint, '9e419d099be445182a8514f5e045b144'::text),
    (170::bigint, 'bbf47cab25e2ff0e4c20a5917c8358dc'::text)
)
select
  n.function_oid,
  n.schema_name,
  n.function_name,
  n.identity_arguments,
  n.function_result,
  n.signature,
  n.function_config,
  n.fixed_search_path,
  n.contains_caller_or_authority_guard,
  case
    when not exists (
      select 1
      from approved_baselines a
      where a.exposed_count = r.exposed_count
        and a.exposed_fingerprint = r.exposed_fingerprint
    ) then 'UNREVIEWED'
    else 'INTENTIONAL'
  end as disposition
from normalized n
cross join reviewed_set r;

with registry_stats as (
  select
    count(*)::bigint as exposed_count,
    md5(string_agg(signature, E'\n' order by signature)) as exposed_fingerprint,
    count(*) filter (where not fixed_search_path)::bigint as missing_fixed_search_path,
    count(*) filter (where disposition = 'UNREVIEWED')::bigint as unreviewed_count,
    count(*) filter (where disposition = 'HARDEN')::bigint as harden_count
  from resident_security_definer_registry
)
select diag(
  format(
    'authenticated SECURITY DEFINER replay surface: count=%s fingerprint=%s',
    exposed_count,
    exposed_fingerprint
  )
)
from registry_stats;

with registry_stats as (
  select
    count(*)::bigint as exposed_count,
    md5(string_agg(signature, E'\n' order by signature)) as exposed_fingerprint
  from resident_security_definer_registry
)
select ok(
  exposed_count in (182::bigint, 142::bigint, 149::bigint, 152::bigint, 159::bigint, 162::bigint, 170::bigint),
  'authenticated SECURITY DEFINER count matches a reviewed surface'
)
from registry_stats;

with registry_stats as (
  select
    count(*)::bigint as exposed_count,
    md5(string_agg(signature, E'\n' order by signature)) as exposed_fingerprint
  from resident_security_definer_registry
)
select ok(
  (exposed_count = 182::bigint and exposed_fingerprint = '66cda900664e76083670f8186087aece'::text)
  or
  (exposed_count = 142::bigint and exposed_fingerprint = '3e6d958c14e0fa7c1509490af1ca7fff'::text)
  or
  (exposed_count = 149::bigint and exposed_fingerprint = '89b13d92160e4f33af98f41a715a0e27'::text)
  or
  (exposed_count = 152::bigint and exposed_fingerprint = 'e6de756df6b23c7a27bea99bc0d62672'::text)
  or
  (exposed_count = 159::bigint and exposed_fingerprint = '96c90025230ec19ee868ca38219e3632'::text)
  or
  (exposed_count = 162::bigint and exposed_fingerprint = '9e419d099be445182a8514f5e045b144'::text)
  or
  (exposed_count = 170::bigint and exposed_fingerprint = 'bbf47cab25e2ff0e4c20a5917c8358dc'::text),
  'authenticated SECURITY DEFINER fingerprint matches its reviewed surface'
)
from registry_stats;

select is(
  (select count(*)::bigint from resident_security_definer_registry where not fixed_search_path),
  0::bigint,
  'every reviewed authenticated SECURITY DEFINER function fixes search_path'
);

select is(
  (select count(*)::bigint from resident_security_definer_registry where disposition = 'UNREVIEWED'),
  0::bigint,
  'no authenticated SECURITY DEFINER function bypasses explicit set review'
);

select is(
  (select count(*)::bigint from resident_security_definer_registry where disposition = 'HARDEN'),
  0::bigint,
  'no reviewed authenticated SECURITY DEFINER function remains marked for hardening'
);

select is(
  (select count(*)::bigint
   from resident_security_definer_registry
   where schema_name = 'public'
     and function_name in (
       'civic_report_create_v1',
       'civic_report_set_vote_v1',
       'civic_report_add_comment_v1',
       'civic_report_finalize_evidence_v1',
       'civic_report_my_page_v1',
       'civic_report_owner_private_details_v1',
       'admin_civic_report_page_v1',
       'admin_civic_report_set_verification_v1',
       'admin_civic_report_transition_v1',
       'civic_report_withdraw_v1'
     )
     and fixed_search_path
     and contains_caller_or_authority_guard),
  10::bigint,
  'Public Reports authenticated SECURITY DEFINER mutation/private RPCs are explicitly guarded on reviewed local surfaces'
);

select is(
  (select count(*)::bigint
   from resident_security_definer_registry
   where schema_name = 'public'
     and signature in (
       'public.civic_report_page_v1(p_scope text, p_urgency text, p_category_slug text, p_verified_only boolean, p_sort text, p_cursor_created_at timestamp with time zone, p_cursor_id uuid, p_limit integer, p_now timestamp with time zone)',
       'public.civic_report_page_v2(p_scope text, p_urgency text, p_category_slug text, p_sort text, p_cursor_created_at timestamp with time zone, p_cursor_id uuid, p_limit integer, p_now timestamp with time zone)',
       'public.civic_report_get_v1(p_report_id uuid)',
       'public.civic_report_comment_page_v1(p_report_id uuid, p_cursor_created_at timestamp with time zone, p_cursor_id uuid, p_limit integer)',
       'public.civic_report_dashboard_v1()',
       'public.civic_report_dashboard_v2()',
       'public.civic_report_categories_v1()',
       'public.civic_report_timeline_v1(p_report_id uuid)'
     )
     and fixed_search_path),
  8::bigint,
  'Public Reports public-read SECURITY DEFINER RPC boundary contains the eight deliberately reviewed signatures'
);

select is(
  (select count(*)::bigint
   from resident_security_definer_registry r
   where r.schema_name = 'public'
     and r.function_name in (
       'civic_report_page_v1',
       'civic_report_page_v2',
       'civic_report_get_v1',
       'civic_report_comment_page_v1',
       'civic_report_dashboard_v1',
       'civic_report_dashboard_v2',
       'civic_report_categories_v1',
       'civic_report_timeline_v1'
     )
     and exists (
       select 1
       from aclexplode(coalesce((select p.proacl from pg_proc p where p.oid = r.function_oid), acldefault('f', (select p.proowner from pg_proc p where p.oid = r.function_oid)))) acl
       where acl.grantee = 0
         and acl.privilege_type = 'EXECUTE'
     )),
  0::bigint,
  'Public Reports public-read RPCs revoke implicit PUBLIC execute'
);

select is(
  (select count(*)::bigint
   from resident_security_definer_registry r
   where r.schema_name = 'public'
     and r.function_name in (
       'civic_report_page_v1',
       'civic_report_page_v2',
       'civic_report_get_v1',
       'civic_report_comment_page_v1',
       'civic_report_dashboard_v1',
       'civic_report_dashboard_v2',
       'civic_report_categories_v1',
       'civic_report_timeline_v1'
     )
     and has_function_privilege('anon', r.function_oid, 'EXECUTE')
     and has_function_privilege('authenticated', r.function_oid, 'EXECUTE')),
  8::bigint,
  'Public Reports public-read RPCs grant execute only to intended client roles after PUBLIC revocation'
);

select is(
  (select count(*)::bigint
   from resident_security_definer_registry
   where schema_name = 'public'
     and function_name in (
       'civic_report_page_v1',
       'civic_report_page_v2',
       'civic_report_get_v1',
       'civic_report_comment_page_v1',
       'civic_report_dashboard_v1',
       'civic_report_dashboard_v2',
       'civic_report_categories_v1',
       'civic_report_timeline_v1'
     )
     and function_result ~* '(reporter_id|exact_address|storage_path|private_note|actor_id|moderation_note|staff_note|internal_actor)'),
  0::bigint,
  'Public Reports public-read RPC return contracts exclude private reporter, location, evidence-path, moderation, actor, and staff-only fields'
);

select is(
  (select count(*)::bigint
   from (values
     ('civic_report_categories_public'::text),
     ('civic_reports_public'::text),
     ('civic_report_status_history_public'::text),
     ('civic_report_comments_public'::text)
   ) v(view_name)
   where has_table_privilege('anon', format('public.%I', v.view_name), 'SELECT')
      or has_table_privilege('authenticated', format('public.%I', v.view_name), 'SELECT')),
  0::bigint,
  'Public Reports projection views expose zero direct client SELECT grants'
);

select is(
  (select count(*)::bigint
   from resident_security_definer_registry
   where schema_name = 'public'
     and function_name in (
       'community_events_page',
       'community_events_admin_page',
       'upsert_community_event',
       'publish_community_event',
       'cancel_community_event',
       'resident_inbox_page',
       'mark_resident_inbox_item_read'
     )
     and fixed_search_path
     and contains_caller_or_authority_guard),
  7::bigint,
  'Events and Inbox authenticated SECURITY DEFINER RPCs are explicitly guarded on reviewed local surfaces'
);

select * from finish();
rollback;
