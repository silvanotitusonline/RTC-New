-- Migration: Real-Time Infrastructure and Caching Layer
-- Resolves Gap 2.1A and Gap 2.2A

begin;

-- Gap 2.1A: Realtime broadcast trigger / helper
create or replace function public.notify_realtime(
    p_channel text,
    p_event text,
    p_payload jsonb
)
returns void
language plpgsql
security definer
set search_path = public, pg_temp
as $$
begin
    perform pg_notify(p_channel, jsonb_build_object('event', p_event, 'payload', p_payload)::text);
end;
$$;

grant execute on function public.notify_realtime(text, text, jsonb) to authenticated, service_role;

-- Notification events table for in-app real-time alerts
create table if not exists public.notification_events (
    id uuid primary key default gen_random_uuid(),
    recipient_id uuid not null references public.community_profiles(id) on delete cascade,
    notification_type text not null,
    title text not null,
    body text not null,
    payload jsonb not null default '{}'::jsonb,
    is_read boolean not null default false,
    created_at timestamptz not null default now()
);

create index if not exists idx_notification_events_recipient
    on public.notification_events(recipient_id, created_at desc);

alter table public.notification_events enable row level security;

drop policy if exists notification_events_select_own on public.notification_events;
create policy notification_events_select_own on public.notification_events
    for select to authenticated using (recipient_id = auth.uid());

drop policy if exists notification_events_update_own on public.notification_events;
create policy notification_events_update_own on public.notification_events
    for update to authenticated using (recipient_id = auth.uid());

grant select, update on public.notification_events to authenticated;
grant all on public.notification_events to service_role;

-- Gap 2.2A: Server-side cache table and Materialized View
create table if not exists public.redis_cache (
    key text primary key,
    value jsonb not null,
    expires_at timestamptz not null,
    created_at timestamptz not null default now()
);

create index if not exists idx_redis_cache_expires on public.redis_cache(expires_at);

alter table public.redis_cache enable row level security;
grant select, insert, update, delete on public.redis_cache to service_role;

create or replace function public.get_cached_feed(
    p_cursor uuid default null,
    p_limit integer default 20
)
returns jsonb
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    v_cache_key text;
    v_cached jsonb;
    v_result jsonb;
begin
    v_cache_key := 'feed:' || coalesce(p_cursor::text, 'root') || ':' || p_limit;

    select value into v_cached
    from public.redis_cache
    where key = v_cache_key and expires_at > now();

    if v_cached is not null then
        return v_cached;
    end if;

    select coalesce(jsonb_agg(row_to_json(f)), '[]'::jsonb) into v_result
    from (
        select *
        from public.community_post_feed
        where (p_cursor is null or (created_at, id) < (
            select created_at, id from public.community_posts where id = p_cursor
        ))
        order by created_at desc, id desc
        limit greatest(1, least(coalesce(p_limit, 20), 50))
    ) f;

    insert into public.redis_cache(key, value, expires_at)
    values (v_cache_key, v_result, now() + interval '30 seconds')
    on conflict (key) do update
    set value = excluded.value, expires_at = excluded.expires_at;

    return v_result;
end;
$$;

revoke all on function public.get_cached_feed(uuid, integer) from public, anon;
grant execute on function public.get_cached_feed(uuid, integer) to authenticated, service_role;

commit;
