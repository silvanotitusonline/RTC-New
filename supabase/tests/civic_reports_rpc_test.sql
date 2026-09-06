begin;
set local search_path = extensions, public, pg_catalog;

select plan(34);

select ok(to_regprocedure('public.civic_report_create_v1(uuid,text,text,timestamptz,uuid,text,text,text,text,double precision,double precision,text,text,boolean,text)') is not null, 'Resident idempotent create RPC exists');
select ok(to_regprocedure('public.civic_report_page_v1(text,text,text,boolean,text,timestamptz,uuid,integer,timestamptz)') is not null, 'Sanitized keyset report page RPC exists');
select ok(to_regprocedure('public.civic_report_get_v1(uuid)') is not null, 'Sanitized report detail RPC exists');
select ok(to_regprocedure('public.civic_report_categories_v1()') is not null, 'Sanitized category catalogue RPC exists');
select ok(to_regprocedure('public.civic_report_timeline_v1(uuid)') is not null, 'Sanitized report timeline RPC exists');
select ok(to_regprocedure('public.civic_report_set_vote_v1(uuid,smallint)') is not null, 'Authoritative vote set/clear RPC exists');
select ok(to_regprocedure('public.civic_report_comment_page_v1(uuid,timestamptz,uuid,integer)') is not null, 'Keyset comment page RPC exists');
select ok(to_regprocedure('public.civic_report_add_comment_v1(uuid,text,uuid)') is not null, 'Idempotent comment RPC exists');
select ok(to_regprocedure('public.civic_report_finalize_evidence_v1(uuid,text,text,text,bigint,integer,integer,integer,smallint,uuid)') is not null, 'Evidence finalization RPC exists');
select ok(to_regprocedure('public.civic_report_dashboard_v1()') is not null, 'Four-count dashboard RPC exists');
select ok(to_regprocedure('public.civic_report_my_page_v1(timestamptz,uuid,integer)') is not null, 'Owner-authorized My Reports RPC exists');
select ok(to_regprocedure('public.civic_report_owner_private_details_v1(uuid)') is not null, 'Owner-authorized private detail RPC exists');
select ok(to_regprocedure('public.admin_civic_report_page_v1(text,text,text,boolean,integer)') is not null, 'Staff review queue RPC exists');
select ok(to_regprocedure('public.admin_civic_report_set_verification_v1(uuid,boolean,text,uuid)') is not null, 'Evidence-review verification RPC exists');
select ok(to_regprocedure('public.admin_civic_report_transition_v1(uuid,text,text,text,uuid,uuid)') is not null, 'Lifecycle transition RPC exists');

select ok(
  not has_function_privilege('anon','public.civic_report_create_v1(uuid,text,text,timestamptz,uuid,text,text,text,text,double precision,double precision,text,text,boolean,text)','EXECUTE')
  and has_function_privilege('authenticated','public.civic_report_create_v1(uuid,text,text,timestamptz,uuid,text,text,text,text,double precision,double precision,text,text,boolean,text)','EXECUTE'),
  'Create is authenticated-only'
);

select ok(
  has_function_privilege('anon','public.civic_report_page_v1(text,text,text,boolean,text,timestamptz,uuid,integer,timestamptz)','EXECUTE')
  and has_function_privilege('authenticated','public.civic_report_page_v1(text,text,text,boolean,text,timestamptz,uuid,integer,timestamptz)','EXECUTE'),
  'Public report page is available to anon and authenticated'
);

select ok(
  has_function_privilege('anon','public.civic_report_get_v1(uuid)','EXECUTE')
  and has_function_privilege('authenticated','public.civic_report_get_v1(uuid)','EXECUTE'),
  'Public report detail is available to anon and authenticated'
);

select ok(
  has_function_privilege('anon','public.civic_report_categories_v1()','EXECUTE')
  and has_function_privilege('authenticated','public.civic_report_categories_v1()','EXECUTE'),
  'Public category catalogue is RPC-only for anon and authenticated'
);

select ok(
  has_function_privilege('anon','public.civic_report_timeline_v1(uuid)','EXECUTE')
  and has_function_privilege('authenticated','public.civic_report_timeline_v1(uuid)','EXECUTE'),
  'Public status timeline is RPC-only for anon and authenticated'
);

select ok(
  not has_function_privilege('anon','public.civic_report_set_vote_v1(uuid,smallint)','EXECUTE')
  and has_function_privilege('authenticated','public.civic_report_set_vote_v1(uuid,smallint)','EXECUTE'),
  'Vote mutation is authenticated-only'
);

select ok(
  not has_function_privilege('anon','public.civic_report_add_comment_v1(uuid,text,uuid)','EXECUTE')
  and has_function_privilege('authenticated','public.civic_report_add_comment_v1(uuid,text,uuid)','EXECUTE'),
  'Comment mutation is authenticated-only'
);

select ok(
  not has_function_privilege('anon','public.civic_report_finalize_evidence_v1(uuid,text,text,text,bigint,integer,integer,integer,smallint,uuid)','EXECUTE')
  and has_function_privilege('authenticated','public.civic_report_finalize_evidence_v1(uuid,text,text,text,bigint,integer,integer,integer,smallint,uuid)','EXECUTE'),
  'Evidence finalization is authenticated-only'
);

select ok(
  not has_function_privilege('anon','public.civic_report_my_page_v1(timestamptz,uuid,integer)','EXECUTE')
  and has_function_privilege('authenticated','public.civic_report_my_page_v1(timestamptz,uuid,integer)','EXECUTE'),
  'My Reports is owner-authenticated only'
);

select ok(
  not has_function_privilege('anon','public.civic_report_owner_private_details_v1(uuid)','EXECUTE')
  and has_function_privilege('authenticated','public.civic_report_owner_private_details_v1(uuid)','EXECUTE'),
  'Private details RPC is never anonymous'
);

select ok(
  not has_function_privilege('anon','public.admin_civic_report_page_v1(text,text,text,boolean,integer)','EXECUTE')
  and has_function_privilege('authenticated','public.admin_civic_report_page_v1(text,text,text,boolean,integer)','EXECUTE'),
  'Admin review queue requires an authenticated session and in-function role checks'
);

select ok(
  not has_function_privilege('anon','public.admin_civic_report_set_verification_v1(uuid,boolean,text,uuid)','EXECUTE')
  and has_function_privilege('authenticated','public.admin_civic_report_set_verification_v1(uuid,boolean,text,uuid)','EXECUTE'),
  'Verification RPC requires authenticated session plus evidence-review role'
);

select ok(
  not has_function_privilege('anon','public.admin_civic_report_transition_v1(uuid,text,text,text,uuid,uuid)','EXECUTE')
  and has_function_privilege('authenticated','public.admin_civic_report_transition_v1(uuid,text,text,text,uuid,uuid)','EXECUTE'),
  'Lifecycle RPC requires authenticated session plus case/evidence/admin role'
);

select is(
  (select count(*)::integer from pg_proc p join pg_namespace n on n.oid=p.pronamespace
   where n.nspname='public' and p.proname like '%civic_report%'
     and p.prosecdef
     and not exists(select 1 from unnest(coalesce(p.proconfig,'{}'::text[])) cfg where cfg like 'search_path=%')),
  0,
  'Every civic SECURITY DEFINER function fixes search_path'
);

select is(
  (select count(*)::integer from pg_proc p join pg_namespace n on n.oid=p.pronamespace
   where n.nspname='public' and p.proname like '%civic_report%'
     and p.prosecdef and has_function_privilege('public',p.oid,'EXECUTE')),
  0,
  'No civic SECURITY DEFINER function retains PUBLIC execute'
);

select ok(
  exists(select 1 from pg_constraint c join pg_class t on t.oid=c.conrelid where t.relname='civic_reports' and c.contype='u' and pg_get_constraintdef(c.oid) ilike '%reporter_id%client_request_id%'),
  'Create retries are idempotent by reporter/client request UUID'
);

select ok(
  exists(select 1 from pg_constraint c join pg_class t on t.oid=c.conrelid where t.relname='civic_report_comments' and c.contype='u' and pg_get_constraintdef(c.oid) ilike '%author_id%client_request_id%'),
  'Comment retries are idempotent by author/client request UUID'
);

select ok(
  exists(select 1 from pg_constraint c join pg_class t on t.oid=c.conrelid where t.relname='civic_report_votes' and c.contype='p' and pg_get_constraintdef(c.oid) ilike '%report_id%voter_id%'),
  'Vote state is one row per report/user'
);

select ok(
  exists(
    select 1
    from pg_constraint c
    join pg_class t on t.oid=c.conrelid
    join pg_namespace n on n.oid=t.relnamespace
    where n.nspname='public'
      and t.relname='civic_reports'
      and c.conname='civic_reports_public_window'
      and c.contype='c'
  )
  and exists(
    select 1
    from pg_attrdef d
    join pg_class t on t.oid=d.adrelid
    join pg_namespace n on n.oid=t.relnamespace
    join pg_attribute a on a.attrelid=t.oid and a.attnum=d.adnum
    where n.nspname='public' and t.relname='civic_reports' and a.attname='public_until'
  ),
  'Public availability has a server-derived default plus an enforced window constraint'
);

select * from finish();
rollback;
