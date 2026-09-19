-- Read-only production inventory. Do not revoke grants from this report automatically.
-- Classification is a review aid; each legacy candidate requires owner confirmation.
with funcs as (
  select p.oid, p.proname, pg_get_function_identity_arguments(p.oid) as identity_args,
         has_function_privilege('anon', p.oid, 'EXECUTE') as anon_execute,
         has_function_privilege('authenticated', p.oid, 'EXECUTE') as authenticated_execute,
         has_function_privilege('service_role', p.oid, 'EXECUTE') as service_execute
    from pg_proc p join pg_namespace n on n.oid=p.pronamespace
   where n.nspname='public' and p.prosecdef
)
select case
         when anon_execute then 'public'
         when proname ~ '(moderate|report|admin|review|audit)' then 'moderator_admin'
         when service_execute and not authenticated_execute then 'service_role'
         when proname ~ '(legacy|deprecated|old|tmp|test)' then 'legacy_candidate'
         else 'authenticated_user'
       end as classification,
       count(*)::bigint as function_count
  from funcs group by 1 order by 1;

-- Review candidates with no caller grant before revoking anything.
with funcs as (
  select p.oid, p.proname, pg_get_function_identity_arguments(p.oid) as identity_args,
         has_function_privilege('anon', p.oid, 'EXECUTE') as anon_execute,
         has_function_privilege('authenticated', p.oid, 'EXECUTE') as authenticated_execute,
         has_function_privilege('service_role', p.oid, 'EXECUTE') as service_execute
    from pg_proc p join pg_namespace n on n.oid=p.pronamespace
   where n.nspname='public' and p.prosecdef
)
select proname, identity_args, anon_execute, authenticated_execute, service_execute
  from funcs
 where not anon_execute and not authenticated_execute and not service_execute
 order by proname, identity_args
 limit 200;
