begin;
set local search_path = extensions, public, pg_catalog;
select plan(12);

select ok(to_regclass('public.daily_post_comments') is not null, 'Daily Post comments table exists');
select ok(to_regclass('public.daily_post_comment_reports') is not null, 'Daily Post comment reports table exists');
select ok(
  exists(select 1 from pg_publication_rel pr join pg_class c on c.oid = pr.prrelid join pg_namespace n on n.oid = c.relnamespace join pg_publication p on p.oid = pr.prpubid where p.pubname = 'supabase_realtime' and n.nspname = 'public' and c.relname = 'daily_post_comments'),
  'Daily Post comments are in the Realtime publication'
);
select ok(
  exists(select 1 from pg_indexes where schemaname = 'public' and tablename = 'daily_post_comments' and indexdef ilike '%(post_id, created_at DESC, id DESC)%'),
  'Daily Post comments have a post/cursor covering index'
);
select ok(
  exists(select 1 from pg_indexes where schemaname = 'public' and tablename = 'daily_post_comment_reports' and indexname = 'daily_post_comment_reports_queue_idx'),
  'Report queue has a status/time index'
);
select ok(
  exists(select 1 from pg_trigger where tgrelid = 'public.daily_post_comments'::regclass and tgname = 'daily_post_comment_count_sync'),
  'Visibility count trigger exists'
);
select ok(
  exists(select 1 from pg_proc p join pg_namespace n on n.oid = p.pronamespace where n.nspname = 'public' and p.proname = 'daily_post_comment_count_sync' and p.prosecdef and coalesce(array_to_string(p.proconfig, ','), '') ilike '%search_path%'),
  'Count trigger is a hardened SECURITY DEFINER'
);
select ok(
  has_function_privilege('authenticated', 'public.daily_post_comment_report_v1(uuid,text,text)', 'EXECUTE')
  and not has_function_privilege('anon', 'public.daily_post_comment_report_v1(uuid,text,text)', 'EXECUTE'),
  'Comment reporting is authenticated-only'
);
select ok(
  not has_table_privilege('authenticated', 'public.daily_post_comment_reports', 'SELECT')
  and not has_table_privilege('authenticated', 'public.daily_post_comment_reports', 'INSERT'),
  'Report table has no direct authenticated table access'
);
select ok(
  exists(select 1 from pg_policies where schemaname = 'public' and tablename = 'daily_post_comment_reports' and policyname = 'daily_post_comment_reports_rpc_only'),
  'Report table has an explicit restrictive RPC-only policy'
);
select ok(
  pg_get_functiondef('public.daily_post_comments_page_v1(uuid,timestamptz,uuid,integer)'::regprocedure) like '%c.state = ''VISIBLE''%'
  and pg_get_functiondef('public.daily_post_comments_page_v2(uuid,timestamptz,uuid,integer)'::regprocedure) like '%c.state = ''VISIBLE''%',
  'Both comment cursors return visible comments only'
);
select ok(
  pg_get_functiondef('public.daily_post_comment_report_v1(uuid,text,text)'::regprocedure) like '%recent_count >= 10%'
  and pg_get_functiondef('public.daily_post_comment_report_v1(uuid,text,text)'::regprocedure) like '%COMMENT_REPORTED%',
  'Reporting is rate limited and audited'
);

select * from finish();
rollback;
