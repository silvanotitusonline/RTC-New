begin;
set local search_path = extensions, public, pg_catalog;

select plan(12);

select ok(
  to_regclass('public.community_events') is not null,
  'Community Events table exists'
);

select is(
  (select relrowsecurity from pg_class where oid = 'public.community_events'::regclass),
  true,
  'Community Events keeps RLS enabled'
);

select is(
  has_table_privilege('anon', 'public.community_events', 'SELECT'),
  false,
  'anon cannot directly read Community Events'
);

select is(
  has_table_privilege('authenticated', 'public.community_events', 'INSERT'),
  false,
  'authenticated users cannot directly create Community Events'
);

select ok(
  exists(
    select 1 from pg_policy
    where polrelid = 'public.community_events'::regclass
      and polname = 'community_events_rpc_only_deny_direct_client_access'
      and polpermissive = false
  ),
  'Community Events has an explicit restrictive RPC-only policy'
);

select ok(
  to_regprocedure('public.community_events_page(integer,integer,text)') is not null,
  'Resident Event listing RPC exists'
);

select ok(
  to_regprocedure('public.community_events_admin_page(integer,integer)') is not null,
  'Administrator Event listing RPC exists'
);

select ok(
  to_regprocedure('public.upsert_community_event(uuid,text,text,timestamp with time zone,timestamp with time zone,text,text,text,boolean)') is not null,
  'Administrator Event editor RPC exists'
);

select ok(
  to_regprocedure('public.publish_community_event(uuid)') is not null,
  'Administrator Event publish RPC exists'
);

select ok(
  to_regprocedure('public.cancel_community_event(uuid,text)') is not null,
  'Administrator Event cancellation RPC exists'
);

select is(
  has_function_privilege('authenticated', 'public.community_events_page(integer,integer,text)', 'EXECUTE'),
  true,
  'Authenticated residents can execute the Event listing RPC'
);

select is(
  has_function_privilege('anon', 'public.community_events_page(integer,integer,text)', 'EXECUTE'),
  false,
  'anon cannot execute the Event listing RPC'
);

select * from finish();
rollback;
