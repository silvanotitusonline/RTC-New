-- Migration: App configuration and system audit logs
-- Provides app_configuration with namespace and created_at indexing
-- Provides system_audit_logs with namespace and created_at indexing
-- Provides baseline moderation tables (reports, business_submissions, support_requests) for AdminDashboard aggregation

begin;

-- 1. App Configuration table
create table if not exists public.app_configuration (
  id uuid primary key default gen_random_uuid(),
  namespace text not null default 'global',
  key text not null,
  value jsonb not null default '{}'::jsonb,
  is_enabled boolean not null default true,
  description text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint app_configuration_namespace_key_unique unique (namespace, key)
);

-- Indexing on namespace and created_at for performant lookups
create index if not exists app_configuration_namespace_idx 
  on public.app_configuration(namespace);

create index if not exists app_configuration_created_at_idx 
  on public.app_configuration(created_at desc);

create index if not exists app_configuration_lookup_idx 
  on public.app_configuration(namespace, key);

-- 2. System Audit Logs table
create table if not exists public.system_audit_logs (
  id uuid primary key default gen_random_uuid(),
  namespace text not null default 'system',
  action text not null,
  actor_id uuid references auth.users(id) on delete set null,
  actor_role text,
  target_type text,
  target_id text,
  details jsonb not null default '{}'::jsonb,
  ip_address text,
  user_agent text,
  created_at timestamptz not null default now()
);

-- Indexing on namespace and created_at for performant lookups
create index if not exists system_audit_logs_namespace_idx 
  on public.system_audit_logs(namespace);

create index if not exists system_audit_logs_created_at_idx 
  on public.system_audit_logs(created_at desc);

create index if not exists system_audit_logs_namespace_created_at_idx 
  on public.system_audit_logs(namespace, created_at desc);

create index if not exists system_audit_logs_actor_idx 
  on public.system_audit_logs(actor_id);

-- 3. Moderation tables for real-time admin dashboard aggregation

-- reports table
create table if not exists public.reports (
  id uuid primary key default gen_random_uuid(),
  status text not null default 'pending',
  source text not null default 'community',
  target_type text not null default 'post',
  target_id text,
  reporter_id uuid references auth.users(id) on delete set null,
  reason text,
  details jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create index if not exists reports_status_idx 
  on public.reports(status, created_at desc);

create index if not exists reports_created_at_idx 
  on public.reports(created_at desc);

-- business_submissions table
create table if not exists public.business_submissions (
  id uuid primary key default gen_random_uuid(),
  status text not null default 'pending',
  business_name text not null default 'Untitled Business',
  category text,
  submitter_id uuid references auth.users(id) on delete set null,
  details jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create index if not exists business_submissions_status_idx 
  on public.business_submissions(status, created_at desc);

create index if not exists business_submissions_created_at_idx 
  on public.business_submissions(created_at desc);

-- support_requests table
create table if not exists public.support_requests (
  id uuid primary key default gen_random_uuid(),
  status text not null default 'pending',
  subject text not null default 'Support Inquiry',
  user_id uuid references auth.users(id) on delete set null,
  priority smallint not null default 3,
  details jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create index if not exists support_requests_status_idx 
  on public.support_requests(status, created_at desc);

create index if not exists support_requests_created_at_idx 
  on public.support_requests(created_at desc);

-- 4. Enable Row Level Security (RLS)
alter table public.app_configuration enable row level security;
alter table public.system_audit_logs enable row level security;
alter table public.reports enable row level security;
alter table public.business_submissions enable row level security;
alter table public.support_requests enable row level security;

-- Policies for app_configuration: Public/authenticated can read, staff/admins can modify
drop policy if exists app_configuration_read_policy on public.app_configuration;
create policy app_configuration_read_policy
  on public.app_configuration
  for select
  to anon, authenticated
  using (true);

drop policy if exists app_configuration_write_policy on public.app_configuration;
create policy app_configuration_write_policy
  on public.app_configuration
  for all
  to authenticated
  using (
    (auth.jwt() -> 'user_metadata' ->> 'is_admin')::boolean is true
    or (auth.jwt() -> 'app_metadata' ->> 'is_admin')::boolean is true
    or (auth.jwt() -> 'user_metadata' ->> 'role') in ('admin', 'system_admin')
  );

-- Policies for system_audit_logs: Admins can read, authenticated can insert
drop policy if exists system_audit_logs_read_policy on public.system_audit_logs;
create policy system_audit_logs_read_policy
  on public.system_audit_logs
  for select
  to authenticated
  using (
    (auth.jwt() -> 'user_metadata' ->> 'is_admin')::boolean is true
    or (auth.jwt() -> 'app_metadata' ->> 'is_admin')::boolean is true
    or (auth.jwt() -> 'user_metadata' ->> 'role') in ('admin', 'system_admin')
  );

drop policy if exists system_audit_logs_insert_policy on public.system_audit_logs;
create policy system_audit_logs_insert_policy
  on public.system_audit_logs
  for insert
  to authenticated
  with check (true);

-- Policies for moderation tables (reports, business_submissions, support_requests)
drop policy if exists reports_staff_read_policy on public.reports;
create policy reports_staff_read_policy
  on public.reports
  for select
  to authenticated
  using (true);

drop policy if exists business_submissions_staff_read_policy on public.business_submissions;
create policy business_submissions_staff_read_policy
  on public.business_submissions
  for select
  to authenticated
  using (true);

drop policy if exists support_requests_staff_read_policy on public.support_requests;
create policy support_requests_staff_read_policy
  on public.support_requests
  for select
  to authenticated
  using (true);

-- Permissions
grant select on public.app_configuration to anon, authenticated;
grant all on public.app_configuration to authenticated;

grant select, insert on public.system_audit_logs to authenticated;
grant all on public.system_audit_logs to service_role;

grant select, insert, update on public.reports to authenticated;
grant select, insert, update on public.business_submissions to authenticated;
grant select, insert, update on public.support_requests to authenticated;

-- 5. Seed default feature configuration
insert into public.app_configuration (namespace, key, value, description)
values
  ('features', 'marketplace', '{"enabled": true, "version": "1.0"}'::jsonb, 'Local marketplace and business directory'),
  ('features', 'public_reports', '{"enabled": true, "version": "1.0"}'::jsonb, 'Civic reports and municipal issue tracking'),
  ('features', 'service_centre', '{"enabled": true, "version": "1.0"}'::jsonb, 'Community service centre bookings'),
  ('features', 'community_events', '{"enabled": true, "version": "1.0"}'::jsonb, 'Community calendar and event notices'),
  ('features', 'admin_control_plane', '{"enabled": true, "version": "1.0"}'::jsonb, 'Administrative governance and moderation hub'),
  ('features', 'ai_assistant', '{"enabled": true, "version": "1.0"}'::jsonb, 'RTC AI Assistant proposals')
on conflict (namespace, key) do update
set value = excluded.value,
    description = excluded.description,
    updated_at = now();

commit;
