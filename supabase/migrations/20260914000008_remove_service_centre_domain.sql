begin;

-- Service Centre and Bookings were permanently removed from the RTC resident product.
-- Preserve the historical migrations for reproducibility; this forward migration removes
-- the now-obsolete database surface from environments where it was previously applied.

-- Remove every Service Centre RPC/helper regardless of overloaded argument signature.
do $$
declare
  v_function record;
begin
  for v_function in
    select p.oid::regprocedure as signature
    from pg_proc p
    join pg_namespace n on n.oid = p.pronamespace
    where n.nspname in ('public', 'private')
      and p.proname like 'service_centre\_%' escape '\'
  loop
    execute format('drop function if exists %s cascade', v_function.signature);
  end loop;
end;
$$;

-- Children first keeps the migration explicit and makes repeated local verification safe.
drop table if exists public.service_centre_booking_messages cascade;
drop table if exists public.service_centre_booking_events cascade;
drop table if exists public.service_centre_booking_payments cascade;
drop table if exists public.service_centre_bookings cascade;
drop table if exists public.service_centre_provider_profiles cascade;

commit;
