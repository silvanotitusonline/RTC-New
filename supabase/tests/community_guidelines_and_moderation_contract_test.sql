begin;
set local search_path = extensions, public, pg_catalog;

select plan(8);

select ok(
  to_regprocedure('public.accept_community_guidelines_v2()') is not null,
  'Boolean-returning Community guideline acceptance RPC exists'
);

select ok(
  to_regprocedure('public.moderate_community_comment_v1(uuid,text)') is not null,
  'Community comment moderation RPC exists'
);

select is(
  has_function_privilege('authenticated', 'public.accept_community_guidelines_v2()', 'EXECUTE'),
  true,
  'Authenticated accounts can accept the current Community guidelines'
);

select is(
  has_function_privilege('anon', 'public.accept_community_guidelines_v2()', 'EXECUTE'),
  false,
  'Anonymous callers cannot accept Community guidelines'
);

select is(
  has_function_privilege('authenticated', 'public.moderate_community_comment_v1(uuid,text)', 'EXECUTE'),
  true,
  'Authenticated staff can reach the role-enforcing moderation RPC'
);

select is(
  has_function_privilege('anon', 'public.moderate_community_comment_v1(uuid,text)', 'EXECUTE'),
  false,
  'Anonymous callers cannot moderate Community comments'
);

select is(
  (select count(*)::integer
     from pg_proc p
     join pg_namespace n on n.oid = p.pronamespace
    where n.nspname = 'public'
      and p.proname in ('accept_community_guidelines_v2', 'moderate_community_comment_v1')
      and p.prosecdef
      and not exists (
        select 1
          from unnest(coalesce(p.proconfig, '{}'::text[])) cfg
         where cfg like 'search_path=%'
      )),
  0,
  'Production-beta SECURITY DEFINER RPCs use an empty search path'
);

select is(
  (select count(*)::integer
     from pg_proc p
     join pg_namespace n on n.oid = p.pronamespace
    where n.nspname = 'public'
      and p.proname in ('accept_community_guidelines_v2', 'moderate_community_comment_v1')
      and has_function_privilege('public', p.oid, 'EXECUTE')),
  0,
  'Production-beta Community RPCs do not retain PUBLIC execute'
);

select * from finish();
rollback;
