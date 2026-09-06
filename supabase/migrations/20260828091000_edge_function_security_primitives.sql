-- Purpose: Add a small database-backed rate-limit claim for authenticated Edge Functions.
-- Affected objects: public.edge_function_rate_limits, public.claim_edge_function_rate_limit(...).
-- Grants/RLS effect: state table is RPC-only and inaccessible to anon/authenticated; claim is service_role-only.
-- Rollback consideration: use a new forward migration to revoke/drop the function and table after Edge callers are migrated.

begin;

create table if not exists public.edge_function_rate_limits (
  scope text not null,
  actor_id uuid not null references auth.users(id) on delete cascade,
  window_started_at timestamptz not null,
  request_count integer not null default 0 check (request_count >= 0),
  updated_at timestamptz not null default now(),
  primary key (scope, actor_id),
  constraint edge_function_rate_limits_scope_check
    check (char_length(scope) between 1 and 80 and scope ~ '^[a-z0-9][a-z0-9_.:-]*$')
);

alter table public.edge_function_rate_limits enable row level security;
revoke all on table public.edge_function_rate_limits from public, anon, authenticated;

drop policy if exists rpc_only_deny_direct_client_access on public.edge_function_rate_limits;
create policy rpc_only_deny_direct_client_access
on public.edge_function_rate_limits
as restrictive
for all
to anon, authenticated
using (false)
with check (false);

comment on table public.edge_function_rate_limits is
  'Server-only fixed-window Edge Function rate-limit state. Direct client access is intentionally denied.';

create or replace function public.claim_edge_function_rate_limit(
  p_scope text,
  p_actor_id uuid,
  p_max_requests integer,
  p_window_seconds integer
)
returns boolean
language plpgsql
security definer
set search_path = ''
as $function$
declare
  v_scope text := lower(trim(coalesce(p_scope, '')));
  v_window_start timestamptz;
  v_request_count integer;
begin
  if p_actor_id is null
     or char_length(v_scope) not between 1 and 80
     or v_scope !~ '^[a-z0-9][a-z0-9_.:-]*$'
     or p_max_requests not between 1 and 120
     or p_window_seconds not between 1 and 3600 then
    raise exception 'Invalid Edge rate-limit claim.' using errcode = '22023';
  end if;

  v_window_start := to_timestamp(
    floor(extract(epoch from clock_timestamp()) / p_window_seconds) * p_window_seconds
  );

  insert into public.edge_function_rate_limits(
    scope,
    actor_id,
    window_started_at,
    request_count,
    updated_at
  ) values (
    v_scope,
    p_actor_id,
    v_window_start,
    1,
    clock_timestamp()
  )
  on conflict (scope, actor_id) do update
  set window_started_at = excluded.window_started_at,
      request_count = case
        when public.edge_function_rate_limits.window_started_at = excluded.window_started_at
          then public.edge_function_rate_limits.request_count + 1
        else 1
      end,
      updated_at = clock_timestamp()
  returning request_count into v_request_count;

  return v_request_count <= p_max_requests;
end
$function$;

revoke all on function public.claim_edge_function_rate_limit(text, uuid, integer, integer)
  from public, anon, authenticated;
grant execute on function public.claim_edge_function_rate_limit(text, uuid, integer, integer)
  to service_role;

comment on function public.claim_edge_function_rate_limit(text, uuid, integer, integer) is
  'Claims a fixed-window Edge Function request budget. SECURITY DEFINER is intentional; execution is service_role-only and inputs are bounded.';

commit;
