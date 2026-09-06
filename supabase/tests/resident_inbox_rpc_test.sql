begin;
set local search_path = extensions, public, pg_catalog;

select plan(11);

select ok(
  to_regclass('public.resident_inbox_read_markers') is not null,
  'Resident Inbox read-marker table exists'
);

select is(
  (select relrowsecurity from pg_class where oid = 'public.resident_inbox_read_markers'::regclass),
  true,
  'Resident Inbox read markers keep RLS enabled'
);

select is(
  has_table_privilege('anon', 'public.resident_inbox_read_markers', 'SELECT'),
  false,
  'anon cannot directly read resident Inbox markers'
);

select is(
  has_table_privilege('authenticated', 'public.resident_inbox_read_markers', 'INSERT'),
  false,
  'authenticated users cannot directly write resident Inbox markers'
);

select ok(
  exists(
    select 1 from pg_policy
    where polrelid = 'public.resident_inbox_read_markers'::regclass
      and polname = 'resident_inbox_read_markers_rpc_only_deny_direct_client_access'
      and polpermissive = false
  ),
  'Resident Inbox read markers have an explicit restrictive RPC-only policy'
);

select ok(
  to_regprocedure('public.resident_inbox_page(text,integer,integer)') is not null,
  'Resident Inbox page RPC exists'
);

select ok(
  to_regprocedure('public.mark_resident_inbox_item_read(text,uuid)') is not null,
  'Resident Inbox read-marker RPC exists'
);

select is(
  has_function_privilege('authenticated', 'public.resident_inbox_page(text,integer,integer)', 'EXECUTE'),
  true,
  'Authenticated residents can load their Inbox'
);

select is(
  has_function_privilege('authenticated', 'public.mark_resident_inbox_item_read(text,uuid)', 'EXECUTE'),
  true,
  'Authenticated residents can mark their own Inbox item as read'
);

select is(
  has_function_privilege('anon', 'public.resident_inbox_page(text,integer,integer)', 'EXECUTE'),
  false,
  'anon cannot load the resident Inbox'
);

select is(
  has_function_privilege('anon', 'public.mark_resident_inbox_item_read(text,uuid)', 'EXECUTE'),
  false,
  'anon cannot mark Inbox items as read'
);

select * from finish();
rollback;
