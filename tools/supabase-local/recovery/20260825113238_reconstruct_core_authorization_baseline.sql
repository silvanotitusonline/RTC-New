-- Reconstructed from read-only production catalog metadata on 2026-08-25.
-- Scope is intentionally limited to the core authorization prerequisites that the retained migrations
-- already reference. This migration contains no user data, no service credentials and no seeds.

create schema if not exists private;

do $$
begin
  create type public.app_role as enum (
    'RESIDENT',
    'CASE_STAFF',
    'CONTENT_EDITOR',
    'MODERATOR',
    'EVIDENCE_REVIEWER',
    'SYSTEM_ADMIN'
  );
exception
  when duplicate_object then null;
end
$$;

create table if not exists public.user_roles (
  user_id uuid not null references auth.users(id) on delete cascade,
  role public.app_role not null,
  granted_by uuid references auth.users(id),
  granted_at timestamptz not null default now(),
  primary key (user_id, role)
);

alter table public.user_roles enable row level security;
revoke all on table public.user_roles from anon, authenticated;
grant select on table public.user_roles to authenticated;

-- A current session is required even where a valid but revoked JWT is presented.
create or replace function private.access_session_is_current()
returns boolean
language sql
stable
security definer
set search_path = auth, public, pg_temp
as $$
  select exists (
    select 1
    from auth.sessions session_row
    where session_row.id = nullif(auth.jwt() ->> 'session_id', '')::uuid
      and session_row.user_id = auth.uid()
      and (session_row.not_after is null or session_row.not_after > now())
  );
$$;

create or replace function private.has_role(required_role public.app_role)
returns boolean
language sql
stable
security definer
set search_path = public, pg_temp
as $$
  select private.access_session_is_current() and exists (
    select 1
    from public.user_roles ur
    where ur.user_id = auth.uid() and ur.role = required_role
  );
$$;

create or replace function private.has_any_role(required_roles public.app_role[])
returns boolean
language sql
stable
security definer
set search_path = public, pg_temp
as $$
  select private.access_session_is_current() and exists (
    select 1
    from public.user_roles ur
    where ur.user_id = auth.uid() and ur.role = any(required_roles)
  );
$$;

revoke all on function private.access_session_is_current() from public;
revoke all on function private.has_role(public.app_role) from public;
revoke all on function private.has_any_role(public.app_role[]) from public;
grant execute on function private.access_session_is_current() to authenticated;
grant execute on function private.has_role(public.app_role) to authenticated;
grant execute on function private.has_any_role(public.app_role[]) to authenticated;

-- Recovered production policy: an account may see its own role assignments; verified
-- System Administrators may read assignments through the security-definer helper.
drop policy if exists user_roles_select_self_or_admin on public.user_roles;
create policy user_roles_select_self_or_admin on public.user_roles
  for select to authenticated
  using (
    (select auth.uid()) = user_id
    or private.has_role('SYSTEM_ADMIN'::public.app_role)
  );
