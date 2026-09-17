begin;

-- The dashboard receives only an invalidation signal. It always re-reads the
-- guarded aggregate RPC, so row payloads from operational tables are never
-- broadcast to staff clients.
create table if not exists public.admin_dashboard_invalidations (
  id bigint generated always as identity primary key,
  source_table text not null check (source_table in (
    'civic_reports',
    'official_notices',
    'community_events',
    'operational_work_items'
  )),
  changed_at timestamptz not null default now()
);

alter table public.admin_dashboard_invalidations enable row level security;
drop policy if exists admin_dashboard_invalidations_staff_read on public.admin_dashboard_invalidations;
create policy admin_dashboard_invalidations_staff_read
  on public.admin_dashboard_invalidations
  for select to authenticated
  using (private.is_any_staff());

revoke all on public.admin_dashboard_invalidations from public, anon;
grant select on public.admin_dashboard_invalidations to authenticated;
grant all on public.admin_dashboard_invalidations to service_role;

create or replace function private.emit_admin_dashboard_invalidation()
returns trigger
language plpgsql
security definer
set search_path = public, pg_temp
as $$
begin
  insert into public.admin_dashboard_invalidations(source_table)
  values (tg_table_name);
  return coalesce(new, old);
end;
$$;

revoke all on function private.emit_admin_dashboard_invalidation() from public, anon, authenticated;

drop trigger if exists trg_admin_dashboard_civic_reports on public.civic_reports;
create trigger trg_admin_dashboard_civic_reports
after insert or update or delete on public.civic_reports
for each row execute function private.emit_admin_dashboard_invalidation();

drop trigger if exists trg_admin_dashboard_official_notices on public.official_notices;
create trigger trg_admin_dashboard_official_notices
after insert or update or delete on public.official_notices
for each row execute function private.emit_admin_dashboard_invalidation();

drop trigger if exists trg_admin_dashboard_community_events on public.community_events;
create trigger trg_admin_dashboard_community_events
after insert or update or delete on public.community_events
for each row execute function private.emit_admin_dashboard_invalidation();

drop trigger if exists trg_admin_dashboard_operational_work_items on public.operational_work_items;
create trigger trg_admin_dashboard_operational_work_items
after insert or update or delete on public.operational_work_items
for each row execute function private.emit_admin_dashboard_invalidation();

do $$
begin
  if not exists (
    select 1
    from pg_publication_rel pr
    join pg_class c on c.oid = pr.prrelid
    join pg_namespace n on n.oid = c.relnamespace
    join pg_publication p on p.oid = pr.prpubid
    where p.pubname = 'supabase_realtime'
      and n.nspname = 'public'
      and c.relname = 'admin_dashboard_invalidations'
  ) then
    execute 'alter publication supabase_realtime add table public.admin_dashboard_invalidations';
  end if;
end;
$$;

commit;
