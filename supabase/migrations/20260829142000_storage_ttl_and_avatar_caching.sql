-- Migration: Storage TTL Extension and Avatar URL Caching
-- Resolves Gap 1.3A and Gap 3.1A

begin;

alter table public.community_post_media
    add column if not exists signed_url_expires_at timestamptz;

alter table public.community_profiles
    add column if not exists avatar_signed_url text,
    add column if not exists avatar_signed_url_expires_at timestamptz;

-- Function to return cached avatar url or path with TTL check
create or replace function public.get_avatar_url(
    p_user_id uuid,
    p_force_refresh boolean default false
)
returns text
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    v_path text;
    v_expires timestamptz;
    v_signed_url text;
begin
    select avatar_path, avatar_signed_url_expires_at, avatar_signed_url
    into v_path, v_expires, v_signed_url
    from public.community_profiles
    where id = p_user_id;

    if v_path is null then
        return null;
    end if;

    if not p_force_refresh and v_expires is not null and v_expires > now() and v_signed_url is not null then
        return v_signed_url;
    end if;

    return v_path;
end;
$$;

revoke all on function public.get_avatar_url(uuid, boolean) from public;
grant execute on function public.get_avatar_url(uuid, boolean) to anon, authenticated, service_role;

commit;
