
begin;

-- ==========================================
-- 1. THE TRUST LAYER (Verification & Reputation)
-- ==========================================
create type verification_status as enum ('UNVERIFIED', 'PENDING', 'VERIFIED', 'REJECTED');

create table if not exists public.user_verifications (
    id uuid primary key default gen_random_uuid(),
    user_id uuid references auth.users(id) on delete cascade,
    verification_type text not null, -- 'GOVERNMENT_ID', 'BUSINESS_LICENSE', 'RESIDENCY_PROOF'
    status verification_status default 'UNVERIFIED',
    document_url text,
    verified_at timestamp with time zone,
    unique(user_id, verification_type)
);

create table if not exists public.user_reputation (
    user_id uuid primary key references auth.users(id) on delete cascade,
    trust_score float default 50.0, -- Baseline 50
    total_vouches integer default 0,
    reliability_index float default 1.0,
    updated_at timestamp with time zone default now()
);

-- ==========================================
-- 2. THE ECONOMY LAYER (Monetization)
-- ==========================================
create type subscription_tier as enum ('FREE', 'PREMIUM', 'ENTERPRISE');

alter table public.profiles 
add column if not exists subscription_tier subscription_tier default 'FREE',
add column if not exists java_id text; -- For external payment IDs

create table if not exists public.promoted_content (
    id uuid primary key default gen_random_uuid(),
    post_id uuid references public.community_posts(id) on delete cascade,
    bid_amount decimal default 0.0,
    target_radius_meters integer default 5000,
    start_date timestamp with time zone,
    end_date timestamp with time zone,
    impressions integer default 0,
    is_active boolean default true
);

-- ==========================================
-- 3. SPATIAL SOCIAL (Geo-Fencing)
-- ==========================================
-- Using PostGIS for spatial queries
create extension if not exists postgis;

alter table public.profiles 
add column if not exists last_known_location geography(POINT, 4326);

create table if not exists public.neighborhood_circles (
    id uuid primary key default gen_random_uuid(),
    name text not null,
    boundary geography(POLYGON, 4326) not null,
    created_at timestamp with time zone default now()
);

-- ==========================================
-- 4. REPUTATION ENGINE (Automation)
-- ==========================================
create or replace function public.calculate_trust_score()
returns trigger as $$
begin
    -- Logic: Increase score on positive reviews, decrease on reports
    update public.user_reputation 
    set trust_score = trust_score + 1.5
    where user_id = NEW.user_id;
    return NEW;
end;
$$ language plpgsql security definer;

create trigger tr_update_trust_on_review
after insert on public.marketplace_reviews
for each row execute function public.calculate_trust_score();

commit;
