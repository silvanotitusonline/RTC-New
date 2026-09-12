
begin;

-- Add detailed account status and security fields
alter table public.profiles 
add column if not exists account_status text default 'ACTIVE', -- 'ACTIVE', 'SUSPENDED', 'BANNED', 'SHADOWBANNED'
add column if not exists last_login_at timestamp with time zone,
add column if not exists login_count integer default 0,
add column if not exists preferred_language text default 'en',
add column if not exists timezone text default 'UTC';

-- Create an Account Security Log for audit trails (Login/Password change/MFA change)
create table if not exists public.account_security_logs (
    id uuid primary key default gen_random_uuid(),
    user_id uuid references auth.users(id) on delete cascade,
    event_type text not null, -- 'LOGIN', 'PASSWORD_CHANGE', 'MFA_TOGGLE', 'DEVICE_REGISTER'
    ip_address text,
    user_agent text,
    created_at timestamp with time zone default now()
);

-- Create a User Preferences table for granular control
create table if not exists public.user_account_settings (
    user_id uuid primary key references auth.users(id) on delete cascade,
    notifications_enabled boolean default true,
    marketing_emails boolean default false,
    profile_visibility text default 'PUBLIC', -- 'PUBLIC', 'PRIVATE', 'HIDDEN'
    dm_settings text default 'EVERYONE', -- 'EVERYONE', 'FOLLOWERS', 'NONE'
    updated_at timestamp with time zone default now()
);

commit;
