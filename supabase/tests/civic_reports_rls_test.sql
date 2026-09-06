begin;
set local search_path = extensions, public, pg_catalog;

select plan(25);

select is(
  (select count(*)::integer from pg_class where relnamespace = 'public'::regnamespace and relname in (
    'civic_report_categories',
    'civic_reports',
    'civic_report_private_details',
    'civic_report_evidence',
    'civic_report_votes',
    'civic_report_comments',
    'civic_report_status_history'
  ) and relkind = 'r'),
  7,
  'Public Reports creates all seven civic domain tables'
);

select is(
  (select count(*)::integer from pg_class where relnamespace = 'public'::regnamespace and relname in (
    'civic_report_categories',
    'civic_reports',
    'civic_report_private_details',
    'civic_report_evidence',
    'civic_report_votes',
    'civic_report_comments',
    'civic_report_status_history'
  ) and relrowsecurity),
  7,
  'Every civic domain table has RLS enabled'
);

select ok(to_regclass('public.civic_report_categories_public') is not null, 'Active category public projection exists');
select ok(to_regclass('public.civic_reports_public') is not null, 'Sanitized report public projection exists');
select ok(to_regclass('public.civic_report_status_history_public') is not null, 'Sanitized public status timeline exists');

select is(
  (select count(*)::integer from information_schema.role_table_grants
   where grantee in ('anon','authenticated')
     and table_schema='public'
     and table_name in (
       'civic_reports','civic_report_private_details','civic_report_evidence',
       'civic_report_votes','civic_report_comments','civic_report_status_history'
     )
     and privilege_type in ('INSERT','UPDATE','DELETE')),
  0,
  'Clients receive no direct civic mutation grants'
);

select is(
  (select count(*)::integer from information_schema.role_table_grants
   where grantee in ('anon','authenticated')
     and table_schema='public'
     and table_name in ('civic_report_private_details','civic_report_evidence')
     and privilege_type='SELECT'),
  0,
  'Private details and evidence metadata have no direct client SELECT grant'
);

select is(
  (select count(*)::integer from information_schema.role_table_grants
   where grantee='anon' and table_schema='public'
     and table_name in ('civic_reports','civic_report_votes','civic_report_comments','civic_report_status_history')
     and privilege_type='SELECT'),
  0,
  'Anonymous users cannot bypass sanitized projections with direct table reads'
);

select is(
  (select count(*)::integer
   from information_schema.role_table_grants
   where grantee in ('PUBLIC','anon','authenticated')
     and table_schema='public'
     and table_name in (
       'civic_report_categories_public','civic_reports_public',
       'civic_report_status_history_public','civic_report_comments_public'
     )
     and privilege_type='SELECT'),
  0,
  'Public report views are not directly readable by client roles'
);

select is(
  (select count(*)::integer
   from pg_class c
   join pg_namespace n on n.oid=c.relnamespace
   where n.nspname='public'
     and c.relname in (
       'civic_report_categories_public','civic_reports_public',
       'civic_report_status_history_public','civic_report_comments_public'
     )
     and 'security_invoker=true'=any(coalesce(c.reloptions,'{}'::text[]))),
  4,
  'All retained public report projection views are security invoker'
);

select ok(
  has_function_privilege('anon','public.civic_report_categories_v1()','EXECUTE')
  and has_function_privilege('authenticated','public.civic_report_categories_v1()','EXECUTE')
  and has_function_privilege('anon','public.civic_report_timeline_v1(uuid)','EXECUTE')
  and has_function_privilege('authenticated','public.civic_report_timeline_v1(uuid)','EXECUTE'),
  'Sanitized category and timeline reads are RPC-only for client roles'
);

select ok(
  not exists(
    select 1 from information_schema.columns
    where table_schema='public' and table_name='civic_reports_public'
      and column_name in ('reporter_id','exact_address','latitude','longitude','storage_path','private_note','no_evidence_reason')
  ),
  'Public report projection contains no reporter/private-location/evidence-path/private-note columns'
);

select ok(
  exists(select 1 from information_schema.columns where table_schema='public' and table_name='civic_reports_public' and column_name='author_display_name'),
  'Public projection exposes only the sanitized author display field'
);

select ok(
  exists(select 1 from storage.buckets where id='civic-report-evidence' and public=false),
  'Civic evidence storage bucket is private'
);

select ok(
  exists(select 1 from storage.buckets where id='civic-report-evidence' and file_size_limit=20971520),
  'Evidence bucket enforces the 20 MB maximum object envelope'
);

select ok(
  exists(select 1 from pg_policies where schemaname='storage' and tablename='objects' and policyname='civic_report_evidence_owner_insert'),
  'Owner-scoped evidence insert policy exists'
);

select ok(
  exists(select 1 from pg_policies where schemaname='storage' and tablename='objects' and policyname='civic_report_evidence_owner_update'),
  'Owner-scoped evidence update policy exists'
);

select ok(
  exists(select 1 from pg_policies where schemaname='storage' and tablename='objects' and policyname='civic_report_evidence_owner_delete'),
  'Owner-scoped evidence delete policy exists'
);

select is(
  (select count(*)::integer from pg_policies where schemaname='storage' and tablename='objects' and policyname like 'civic_report_evidence%' and 'anon'=any(roles)),
  0,
  'No civic evidence storage policy grants anon direct object access'
);

select ok(
  exists(select 1 from pg_indexes where schemaname='public' and tablename='civic_reports' and indexdef ilike '%created_at%id%'),
  'Visible report keyset ordering has a created_at/id index'
);

select ok(
  exists(select 1 from pg_indexes where schemaname='public' and tablename='civic_report_votes' and indexdef ilike '%report_id%updated_at%'),
  'Vote activity has report/update index for Hot sorting'
);

select ok(
  exists(select 1 from pg_indexes where schemaname='public' and tablename='civic_report_comments' and indexdef ilike '%report_id%state%created_at%id%'),
  'Comment feed has report/state/created_at/id index'
);

select ok(
  not exists(
    select 1
    from pg_constraint c
    join pg_class t on t.oid=c.conrelid
    join pg_namespace n on n.oid=t.relnamespace
    where n.nspname='public' and t.relname like 'civic_report%'
      and c.contype='f'
      and not exists (
        select 1 from pg_index i
        where i.indrelid=c.conrelid and i.indisvalid and i.indisready
          and (i.indkey::smallint[])[0:cardinality(c.conkey)-1] @> c.conkey
      )
  ),
  'Every civic foreign key is covered by an index'
);

select is(
  (select count(*)::integer from public.civic_report_categories_v1() where is_active),
  11,
  'Eleven active Public Report categories are seeded'
);

select ok(
  not exists(select 1 from public.civic_report_categories_v1() where slug is null or label is null),
  'Category catalogue exposes stable slugs and labels'
);

select * from finish();
rollback;
