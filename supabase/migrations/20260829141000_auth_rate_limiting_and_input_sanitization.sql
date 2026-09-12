-- Migration: Auth rate limiting and input sanitization
-- Resolves Gap 1.2A and Gap 1.2B

begin;

-- Gap 1.2A: Auth rate limiting table and functions
create table if not exists public.auth_rate_limits (
    identifier text not null,
    event_name text not null,
    window_started_at timestamptz not null,
    request_count integer not null default 0,
    updated_at timestamptz not null default now(),
    primary key (identifier, event_name)
);

create index if not exists idx_auth_rate_limits_window
    on public.auth_rate_limits(identifier, event_name, window_started_at);

alter table public.auth_rate_limits enable row level security;
revoke all on public.auth_rate_limits from public, anon, authenticated;
grant select, insert, update on public.auth_rate_limits to service_role;

create or replace function public.check_auth_rate_limit(
    p_identifier text,
    p_event_name text,
    p_max_requests integer default 5,
    p_window_seconds integer default 3600
) returns boolean
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_window_start timestamptz;
    v_count integer;
begin
    v_window_start := to_timestamp(floor(extract(epoch from clock_timestamp()) / p_window_seconds) * p_window_seconds);
    insert into public.auth_rate_limits(identifier, event_name, window_started_at, request_count)
    values (p_identifier, p_event_name, v_window_start, 1)
    on conflict (identifier, event_name) do update
    set request_count = case
        when public.auth_rate_limits.window_started_at = v_window_start
        then public.auth_rate_limits.request_count + 1
        else 1 end,
        updated_at = clock_timestamp();

    select request_count into v_count from public.auth_rate_limits
    where identifier = p_identifier and event_name = p_event_name;

    return v_count <= p_max_requests;
end;
$$;

grant execute on function public.check_auth_rate_limit(text, text, integer, integer) to service_role;

create or replace function public.enforce_auth_signup_rate_limit()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
    if not public.check_auth_rate_limit(new.email, 'signup', 3, 3600) then
        raise exception 'RATE_LIMITED';
    end if;
    return new;
end;
$$;

-- Gap 1.2B: Input sanitization function and safe draft creation
create or replace function public.sanitize_input_text(p_input text)
returns text
language sql
immutable
set search_path = public, pg_temp
as $$
    select regexp_replace(
        regexp_replace(
            regexp_replace(
                trim(coalesce(p_input, '')),
                '<[^>]*>', '', 'g'
            ),
            E'[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]',
            '', 'g'
        ),
        '[\\x{2028}\\x{2029}]', '', 'g'
    );
$$;

grant execute on function public.sanitize_input_text(text) to anon, authenticated, service_role;

create or replace function public.create_community_post_draft(p_body text default '')
returns uuid
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_user_id uuid := auth.uid();
    v_post_id uuid;
    v_sanitized text;
begin
    if v_user_id is null then raise exception 'AUTH_REQUIRED'; end if;
    v_sanitized := public.sanitize_input_text(p_body);
    if char_length(v_sanitized) > 280 then raise exception 'INVALID_POST'; end if;
    perform private.ensure_community_profile_for_account(v_user_id);
    if not private.community_guidelines_accepted() then raise exception 'GUIDELINES_NOT_ACCEPTED'; end if;
    insert into public.community_posts(author_id, body, state, is_locked, report_count)
    values (v_user_id, v_sanitized, 'DRAFT', false, 0) returning id into v_post_id;
    return v_post_id;
end;
$$;

revoke all on function public.create_community_post_draft(text) from public, anon;
grant execute on function public.create_community_post_draft(text) to authenticated, service_role;

commit;
