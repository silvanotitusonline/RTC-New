begin;

-- RTC Community Service Centre MVP.
-- This domain is intentionally isolated from Marketplace publication ownership.
-- Client access is RPC-only; all authoritative identity and lifecycle decisions are server-side.

create table public.service_centre_provider_profiles (
  user_id uuid primary key references public.profiles(id) on delete cascade,
  primary_category_id uuid not null references public.marketplace_categories(id),
  marketplace_business_id uuid null references public.marketplace_businesses(id) on delete set null,
  locality text not null check (char_length(trim(locality)) between 2 and 120),
  coordinates extensions.geography(Point,4326) not null,
  service_radius_km integer not null default 25 check (service_radius_km between 1 and 50),
  starting_price numeric(10,2) not null check (starting_price between 0 and 999999.99),
  currency_code char(3) not null default 'ZAR' check (currency_code = 'ZAR'),
  is_active boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create unique index service_centre_provider_business_unique_idx
  on public.service_centre_provider_profiles(marketplace_business_id)
  where marketplace_business_id is not null;
create index service_centre_provider_coordinates_idx
  on public.service_centre_provider_profiles using gist(coordinates);
create index service_centre_provider_category_active_idx
  on public.service_centre_provider_profiles(primary_category_id, is_active);
create index service_centre_provider_locality_active_idx
  on public.service_centre_provider_profiles(lower(locality), is_active);

create table public.service_centre_bookings (
  id uuid primary key default gen_random_uuid(),
  customer_user_id uuid not null references public.profiles(id),
  provider_user_id uuid not null references public.profiles(id),
  category_id uuid not null references public.marketplace_categories(id),
  category_name_snapshot text not null check (char_length(trim(category_name_snapshot)) between 1 and 120),
  marketplace_business_id uuid null references public.marketplace_businesses(id) on delete set null,
  marketplace_offering_id uuid null references public.marketplace_offerings(id) on delete set null,
  requested_start_at timestamptz not null,
  service_location_text text not null check (char_length(trim(service_location_text)) between 3 and 240),
  offer_amount numeric(10,2) not null check (offer_amount between 1 and 999999.99),
  currency_code char(3) not null default 'ZAR' check (currency_code = 'ZAR'),
  status text not null default 'PENDING_PROVIDER' check (
    status in ('PENDING_PROVIDER','ACCEPTED_AWAITING_PAYMENT','CONFIRMED','COMPLETED','DECLINED','CANCELLED')
  ),
  commitment_fee_amount numeric(10,2) null check (commitment_fee_amount is null or commitment_fee_amount between 0 and 999999.99),
  create_idempotency_key uuid not null,
  accepted_at timestamptz null,
  declined_at timestamptz null,
  confirmed_at timestamptz null,
  completed_at timestamptz null,
  cancelled_at timestamptz null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  check (customer_user_id <> provider_user_id),
  unique(customer_user_id, create_idempotency_key)
);

create index service_centre_bookings_customer_state_idx
  on public.service_centre_bookings(customer_user_id, status, created_at desc);
create index service_centre_bookings_provider_state_idx
  on public.service_centre_bookings(provider_user_id, status, created_at desc);
create index service_centre_bookings_requested_start_idx
  on public.service_centre_bookings(requested_start_at);

create table public.service_centre_booking_messages (
  id uuid primary key default gen_random_uuid(),
  booking_id uuid not null references public.service_centre_bookings(id) on delete cascade,
  sender_user_id uuid not null references public.profiles(id),
  body text not null check (char_length(trim(body)) between 1 and 1000),
  idempotency_key uuid not null,
  created_at timestamptz not null default now(),
  unique(sender_user_id, idempotency_key)
);

create index service_centre_messages_booking_created_idx
  on public.service_centre_booking_messages(booking_id, created_at desc);
create index service_centre_messages_sender_rate_idx
  on public.service_centre_booking_messages(sender_user_id, created_at desc);

create table public.service_centre_booking_payments (
  id uuid primary key default gen_random_uuid(),
  booking_id uuid not null references public.service_centre_bookings(id) on delete cascade,
  provider text not null default 'YOCO' check (provider = 'YOCO'),
  purpose text not null default 'COMMITMENT_FEE' check (purpose = 'COMMITMENT_FEE'),
  amount numeric(10,2) not null check (amount between 0 and 999999.99),
  currency_code char(3) not null default 'ZAR' check (currency_code = 'ZAR'),
  status text not null default 'CREATED' check (status in ('CREATED','PENDING','PAID','FAILED','CANCELLED','REFUNDED')),
  external_checkout_id text null,
  external_payment_id text null,
  idempotency_key uuid not null,
  initiated_by uuid not null references public.profiles(id),
  paid_at timestamptz null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique(booking_id, idempotency_key)
);

create unique index service_centre_payment_checkout_unique_idx
  on public.service_centre_booking_payments(external_checkout_id)
  where external_checkout_id is not null;
create unique index service_centre_payment_external_payment_unique_idx
  on public.service_centre_booking_payments(external_payment_id)
  where external_payment_id is not null;
create unique index service_centre_payment_single_active_idx
  on public.service_centre_booking_payments(booking_id)
  where status in ('CREATED','PENDING','PAID');
create index service_centre_payment_booking_idx
  on public.service_centre_booking_payments(booking_id, created_at desc);

create table public.service_centre_booking_events (
  id uuid primary key default gen_random_uuid(),
  booking_id uuid not null references public.service_centre_bookings(id) on delete cascade,
  actor_user_id uuid null references public.profiles(id),
  event_type text not null check (char_length(trim(event_type)) between 1 and 80),
  from_status text null,
  to_status text null,
  idempotency_key uuid null,
  metadata jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now()
);

create index service_centre_events_booking_created_idx
  on public.service_centre_booking_events(booking_id, created_at);
create unique index service_centre_event_actor_idempotency_idx
  on public.service_centre_booking_events(actor_user_id, event_type, idempotency_key)
  where actor_user_id is not null and idempotency_key is not null;

alter table public.service_centre_provider_profiles enable row level security;
alter table public.service_centre_provider_profiles force row level security;
alter table public.service_centre_bookings enable row level security;
alter table public.service_centre_bookings force row level security;
alter table public.service_centre_booking_messages enable row level security;
alter table public.service_centre_booking_messages force row level security;
alter table public.service_centre_booking_payments enable row level security;
alter table public.service_centre_booking_payments force row level security;
alter table public.service_centre_booking_events enable row level security;
alter table public.service_centre_booking_events force row level security;

revoke all on table public.service_centre_provider_profiles from anon, authenticated;
revoke all on table public.service_centre_provider_profiles from public;
revoke all on table public.service_centre_bookings from anon, authenticated;
revoke all on table public.service_centre_bookings from public;
revoke all on table public.service_centre_booking_messages from anon, authenticated;
revoke all on table public.service_centre_booking_messages from public;
revoke all on table public.service_centre_booking_payments from anon, authenticated;
revoke all on table public.service_centre_booking_payments from public;
revoke all on table public.service_centre_booking_events from anon, authenticated;
revoke all on table public.service_centre_booking_events from public;

grant select, insert, update, delete on table public.service_centre_provider_profiles to service_role;
grant select, insert, update, delete on table public.service_centre_bookings to service_role;
grant select, insert, update, delete on table public.service_centre_booking_messages to service_role;
grant select, insert, update, delete on table public.service_centre_booking_payments to service_role;
grant select, insert, update, delete on table public.service_centre_booking_events to service_role;

create or replace function private.service_centre_actor()
returns uuid
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_actor uuid := (select auth.uid());
begin
  if v_actor is null then
    raise exception 'Authentication is required.' using errcode = '42501';
  end if;
  if not exists(select 1 from public.profiles p where p.id = v_actor) then
    raise exception 'A valid RTC profile is required.' using errcode = '42501';
  end if;
  return v_actor;
end;
$$;
revoke all on function private.service_centre_actor() from public, anon, authenticated;

create or replace function private.service_centre_append_event(
  p_booking_id uuid,
  p_actor_user_id uuid,
  p_event_type text,
  p_from_status text,
  p_to_status text,
  p_idempotency_key uuid,
  p_metadata jsonb default '{}'::jsonb
)
returns void
language plpgsql
security definer
set search_path = ''
as $$
begin
  insert into public.service_centre_booking_events(
    booking_id, actor_user_id, event_type, from_status, to_status, idempotency_key, metadata
  ) values (
    p_booking_id, p_actor_user_id, p_event_type, p_from_status, p_to_status, p_idempotency_key,
    coalesce(p_metadata, '{}'::jsonb)
  );
end;
$$;
revoke all on function private.service_centre_append_event(uuid,uuid,text,text,text,uuid,jsonb) from public, anon, authenticated;

create or replace function private.service_centre_booking_payload(p_booking_id uuid, p_actor uuid)
returns jsonb
language sql
security definer
set search_path = ''
as $$
  select jsonb_build_object(
    'id', b.id,
    'customerUserId', b.customer_user_id,
    'providerUserId', b.provider_user_id,
    'actorRole', case when b.customer_user_id = p_actor then 'CUSTOMER' else 'PROVIDER' end,
    'counterpartyUserId', case when b.customer_user_id = p_actor then b.provider_user_id else b.customer_user_id end,
    'counterpartyDisplayName', case when b.customer_user_id = p_actor then pp.display_name else cp.display_name end,
    'counterpartyAvatarUrl', case when b.customer_user_id = p_actor then pp.avatar_url else cp.avatar_url end,
    'categoryId', b.category_id,
    'categoryName', b.category_name_snapshot,
    'marketplaceBusinessId', b.marketplace_business_id,
    'marketplaceOfferingId', b.marketplace_offering_id,
    'requestedStartAt', b.requested_start_at,
    'serviceLocationText', b.service_location_text,
    'offerAmount', b.offer_amount::text,
    'currencyCode', b.currency_code,
    'status', b.status,
    'commitmentFeeAmount', case when b.commitment_fee_amount is null then null else b.commitment_fee_amount::text end,
    'acceptedAt', b.accepted_at,
    'declinedAt', b.declined_at,
    'confirmedAt', b.confirmed_at,
    'completedAt', b.completed_at,
    'cancelledAt', b.cancelled_at,
    'createdAt', b.created_at,
    'updatedAt', b.updated_at
  )
  from public.service_centre_bookings b
  join public.profiles cp on cp.id = b.customer_user_id
  join public.profiles pp on pp.id = b.provider_user_id
  where b.id = p_booking_id
    and p_actor in (b.customer_user_id, b.provider_user_id)
$$;
revoke all on function private.service_centre_booking_payload(uuid,uuid) from public, anon, authenticated;

create or replace function public.service_centre_categories()
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_actor uuid := private.service_centre_actor();
  v_result jsonb;
begin
  select coalesce(jsonb_agg(jsonb_build_object(
      'id', c.id, 'name', c.name, 'slug', c.slug, 'iconKey', c.icon_key
    ) order by c.display_order, c.name), '[]'::jsonb)
  into v_result
  from public.marketplace_categories c
  where c.is_active = true;
  return v_result;
end;
$$;

create or replace function public.service_centre_local_radar(
  p_category_id uuid default null,
  p_origin_latitude double precision default null,
  p_origin_longitude double precision default null,
  p_locality text default null,
  p_radius_metres integer default 25000,
  p_limit integer default 20,
  p_offset integer default 0
)
returns table(
  provider_user_id uuid,
  display_name text,
  avatar_url text,
  category_id uuid,
  category_name text,
  locality text,
  distance_metres integer,
  starting_price numeric,
  currency_code text,
  marketplace_business_id uuid,
  rating_average numeric,
  review_count integer,
  verified boolean
)
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_actor uuid := private.service_centre_actor();
  v_origin extensions.geography;
  v_locality text := nullif(trim(coalesce(p_locality, '')), '');
  v_limit integer := greatest(1, least(coalesce(p_limit, 20), 50));
  v_offset integer := greatest(0, coalesce(p_offset, 0));
  v_radius integer := greatest(100, least(coalesce(p_radius_metres, 25000), 50000));
begin
  if char_length(coalesce(p_locality, '')) > 120 then
    raise exception 'Locality is too long.' using errcode = '22023';
  end if;
  if v_offset > 5000 then
    raise exception 'Provider offset is outside the supported window.' using errcode = '22023';
  end if;
  if (p_origin_latitude is null) <> (p_origin_longitude is null) then
    raise exception 'Provide both latitude and longitude or neither.' using errcode = '22023';
  end if;
  if p_origin_latitude is not null then
    if p_origin_latitude not between -90 and 90 or p_origin_longitude not between -180 and 180 then
      raise exception 'Use valid coordinates.' using errcode = '22023';
    end if;
    v_origin := extensions.ST_SetSRID(extensions.ST_MakePoint(p_origin_longitude, p_origin_latitude), 4326)::extensions.geography;
  end if;
  if p_category_id is not null and not exists(
    select 1 from public.marketplace_categories c where c.id = p_category_id and c.is_active = true
  ) then
    raise exception 'Choose an active service category.' using errcode = '22023';
  end if;

  return query
  select
    sp.user_id,
    p.display_name,
    p.avatar_url,
    sp.primary_category_id,
    c.name,
    sp.locality,
    case when v_origin is null then null else round(extensions.ST_Distance(sp.coordinates, v_origin))::integer end,
    sp.starting_price,
    sp.currency_code::text,
    sp.marketplace_business_id,
    case when sp.marketplace_business_id is null then null else rs.rating_average end,
    case when sp.marketplace_business_id is null then null else rs.review_count end,
    case when sp.marketplace_business_id is null then false else exists(
      select 1 from public.marketplace_business_verifications bv
      where bv.business_id = sp.marketplace_business_id
        and bv.state = 'VERIFIED'
        and (bv.expires_at is null or bv.expires_at > now())
    ) end
  from public.service_centre_provider_profiles sp
  join public.profiles p on p.id = sp.user_id
  join public.marketplace_categories c on c.id = sp.primary_category_id and c.is_active = true
  left join public.marketplace_business_rating_stats rs on rs.business_id = sp.marketplace_business_id
  where sp.is_active = true
    and sp.user_id <> v_actor
    and (p_category_id is null or sp.primary_category_id = p_category_id)
    and (
      (v_origin is not null and extensions.ST_DWithin(
        sp.coordinates,
        v_origin,
        least(v_radius::double precision, (sp.service_radius_km * 1000)::double precision)
      ))
      or (v_origin is null and v_locality is not null and lower(sp.locality) = lower(v_locality))
      or (v_origin is null and v_locality is null)
    )
  order by
    case when v_origin is null then null else extensions.ST_Distance(sp.coordinates, v_origin) end asc nulls last,
    p.display_name asc,
    sp.user_id
  offset v_offset
  limit v_limit;
end;
$$;

create or replace function public.service_centre_my_provider_profile()
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_actor uuid := private.service_centre_actor();
  v_result jsonb;
begin
  select jsonb_build_object(
    'userId', sp.user_id,
    'primaryCategoryId', sp.primary_category_id,
    'categoryName', c.name,
    'marketplaceBusinessId', sp.marketplace_business_id,
    'locality', sp.locality,
    'serviceRadiusKm', sp.service_radius_km,
    'startingPrice', sp.starting_price::text,
    'currencyCode', sp.currency_code,
    'isActive', sp.is_active,
    'createdAt', sp.created_at,
    'updatedAt', sp.updated_at
  ) into v_result
  from public.service_centre_provider_profiles sp
  join public.marketplace_categories c on c.id = sp.primary_category_id
  where sp.user_id = v_actor;
  return v_result;
end;
$$;

create or replace function public.service_centre_provider_detail(p_provider_reference text)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_actor uuid := private.service_centre_actor();
  v_reference text := trim(coalesce(p_provider_reference, ''));
  v_result jsonb;
begin
  if v_reference = '' or char_length(v_reference) > 128 then
    raise exception 'Choose a valid provider.' using errcode = '22023';
  end if;
  select jsonb_build_object(
    'providerUserId', sp.user_id,
    'displayName', p.display_name,
    'avatarUrl', p.avatar_url,
    'categoryId', sp.primary_category_id,
    'categoryName', c.name,
    'locality', sp.locality,
    'startingPrice', sp.starting_price::text,
    'currencyCode', sp.currency_code,
    'marketplaceBusinessId', sp.marketplace_business_id,
    'ratingAverage', case when sp.marketplace_business_id is null then null else rs.rating_average end,
    'reviewCount', case when sp.marketplace_business_id is null then null else rs.review_count end,
    'verified', case when sp.marketplace_business_id is null then false else exists(
      select 1 from public.marketplace_business_verifications bv
      where bv.business_id = sp.marketplace_business_id
        and bv.state = 'VERIFIED'
        and (bv.expires_at is null or bv.expires_at > now())
    ) end,
    'isActive', sp.is_active
  ) into v_result
  from public.service_centre_provider_profiles sp
  join public.profiles p on p.id = sp.user_id
  join public.marketplace_categories c on c.id = sp.primary_category_id and c.is_active = true
  left join public.marketplace_business_rating_stats rs on rs.business_id = sp.marketplace_business_id
  where (sp.user_id::text = v_reference or sp.marketplace_business_id::text = v_reference)
    and (sp.is_active = true or sp.user_id = v_actor)
  limit 1;
  if v_result is null then
    raise exception 'This provider is not available for Service Centre bookings.' using errcode = 'P0002';
  end if;
  return v_result;
end;
$$;

create or replace function public.service_centre_upsert_provider_profile(
  p_primary_category_id uuid,
  p_locality text,
  p_latitude double precision,
  p_longitude double precision,
  p_starting_price numeric,
  p_service_radius_km integer default 25,
  p_marketplace_business_id uuid default null
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_actor uuid := private.service_centre_actor();
  v_locality text := trim(coalesce(p_locality, ''));
  v_radius integer := coalesce(p_service_radius_km, 25);
  v_result jsonb;
begin
  if not exists(select 1 from public.marketplace_categories c where c.id = p_primary_category_id and c.is_active = true) then
    raise exception 'Choose an active service category.' using errcode = '22023';
  end if;
  if char_length(v_locality) not between 2 and 120 then
    raise exception 'Enter a service area.' using errcode = '22023';
  end if;
  if p_latitude is null or p_longitude is null or p_latitude not between -90 and 90 or p_longitude not between -180 and 180 then
    raise exception 'Use a valid service-area location.' using errcode = '22023';
  end if;
  if v_radius not between 1 and 50 then
    raise exception 'Travel distance must be between 1 and 50 km.' using errcode = '22023';
  end if;
  if p_starting_price is null or p_starting_price not between 0 and 999999.99 then
    raise exception 'Starting price is outside the supported range.' using errcode = '22023';
  end if;
  if p_marketplace_business_id is not null and not exists(
    select 1
    from public.marketplace_businesses b
    where b.id = p_marketplace_business_id
      and b.lifecycle_state = 'PUBLISHED'
      and (
        b.created_by = v_actor
        or exists(
          select 1 from public.marketplace_business_members bm
          where bm.business_id = b.id and bm.user_id = v_actor and bm.role = 'OWNER' and bm.state = 'ACTIVE'
        )
      )
  ) then
    raise exception 'Only the owner of a published Marketplace business may link it.' using errcode = '42501';
  end if;

  insert into public.service_centre_provider_profiles(
    user_id, primary_category_id, marketplace_business_id, locality, coordinates,
    service_radius_km, starting_price, currency_code, is_active, created_at, updated_at
  ) values (
    v_actor, p_primary_category_id, p_marketplace_business_id, v_locality,
    extensions.ST_SetSRID(extensions.ST_MakePoint(p_longitude, p_latitude), 4326)::extensions.geography,
    v_radius, p_starting_price, 'ZAR', true, now(), now()
  )
  on conflict(user_id) do update set
    primary_category_id = excluded.primary_category_id,
    marketplace_business_id = excluded.marketplace_business_id,
    locality = excluded.locality,
    coordinates = excluded.coordinates,
    service_radius_km = excluded.service_radius_km,
    starting_price = excluded.starting_price,
    currency_code = 'ZAR',
    updated_at = now();

  select public.service_centre_my_provider_profile() into v_result;
  return v_result;
end;
$$;

create or replace function public.service_centre_set_provider_active(p_active boolean)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_actor uuid := private.service_centre_actor();
  v_result jsonb;
begin
  update public.service_centre_provider_profiles
  set is_active = coalesce(p_active, false), updated_at = now()
  where user_id = v_actor;
  if not found then
    raise exception 'Create a provider profile first.' using errcode = 'P0002';
  end if;
  select public.service_centre_my_provider_profile() into v_result;
  return v_result;
end;
$$;

create or replace function public.service_centre_create_booking(
  p_provider_user_id uuid,
  p_requested_start_at timestamptz,
  p_service_location_text text,
  p_offer_amount numeric,
  p_idempotency_key uuid,
  p_marketplace_business_id uuid default null,
  p_marketplace_offering_id uuid default null
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_actor uuid := private.service_centre_actor();
  v_provider public.service_centre_provider_profiles%rowtype;
  v_category_name text;
  v_location text := trim(coalesce(p_service_location_text, ''));
  v_existing uuid;
  v_booking_id uuid;
begin
  if p_idempotency_key is null then raise exception 'Idempotency key is required.' using errcode = '22023'; end if;
  select b.id into v_existing from public.service_centre_bookings b
  where b.customer_user_id = v_actor and b.create_idempotency_key = p_idempotency_key;
  if v_existing is not null then return private.service_centre_booking_payload(v_existing, v_actor); end if;
  if p_provider_user_id is null or p_provider_user_id = v_actor then
    raise exception 'Choose another provider.' using errcode = '22023';
  end if;
  select * into v_provider from public.service_centre_provider_profiles sp
  where sp.user_id = p_provider_user_id and sp.is_active = true;
  if not found then raise exception 'This provider is not currently accepting bookings.' using errcode = 'P0002'; end if;
  if p_requested_start_at is null or p_requested_start_at <= now() then
    raise exception 'Choose a future booking time.' using errcode = '22023';
  end if;
  if char_length(v_location) not between 3 and 240 then
    raise exception 'Enter a valid service location.' using errcode = '22023';
  end if;
  if p_offer_amount is null or p_offer_amount not between 1 and 999999.99 then
    raise exception 'Offer amount is outside the supported range.' using errcode = '22023';
  end if;
  if p_marketplace_business_id is not null and v_provider.marketplace_business_id is distinct from p_marketplace_business_id then
    raise exception 'The selected business is not linked to this provider.' using errcode = '22023';
  end if;
  if p_marketplace_offering_id is not null then
    if p_marketplace_business_id is null then raise exception 'A Marketplace business is required for this offering.' using errcode = '22023'; end if;
    if not exists(
      select 1
      from public.marketplace_offerings o
      join public.marketplace_businesses b on b.current_public_revision_id = o.revision_id
      where o.id = p_marketplace_offering_id
        and o.offering_type = 'SERVICE'
        and b.id = p_marketplace_business_id
        and b.lifecycle_state = 'PUBLISHED'
    ) then
      raise exception 'Choose a published service offering from this business.' using errcode = '22023';
    end if;
  end if;
  select c.name into v_category_name from public.marketplace_categories c where c.id = v_provider.primary_category_id and c.is_active = true;
  if v_category_name is null then raise exception 'This provider category is unavailable.' using errcode = '22023'; end if;

  insert into public.service_centre_bookings(
    customer_user_id, provider_user_id, category_id, category_name_snapshot,
    marketplace_business_id, marketplace_offering_id, requested_start_at,
    service_location_text, offer_amount, currency_code, status, create_idempotency_key
  ) values (
    v_actor, p_provider_user_id, v_provider.primary_category_id, v_category_name,
    p_marketplace_business_id, p_marketplace_offering_id, p_requested_start_at,
    v_location, p_offer_amount, 'ZAR', 'PENDING_PROVIDER', p_idempotency_key
  ) returning id into v_booking_id;

  perform private.service_centre_append_event(
    v_booking_id, v_actor, 'BOOKING_CREATED', null, 'PENDING_PROVIDER', p_idempotency_key,
    jsonb_build_object('offerAmount', p_offer_amount::text, 'currencyCode', 'ZAR')
  );
  return private.service_centre_booking_payload(v_booking_id, v_actor);
end;
$$;

create or replace function public.service_centre_accept_booking(p_booking_id uuid, p_idempotency_key uuid)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_actor uuid := private.service_centre_actor();
  v_booking public.service_centre_bookings%rowtype;
begin
  if p_idempotency_key is null then raise exception 'Idempotency key is required.' using errcode = '22023'; end if;
  if exists(select 1 from public.service_centre_booking_events e where e.actor_user_id=v_actor and e.event_type='BOOKING_ACCEPTED' and e.idempotency_key=p_idempotency_key) then
    return private.service_centre_booking_payload(p_booking_id, v_actor);
  end if;
  select * into v_booking from public.service_centre_bookings b where b.id=p_booking_id for update;
  if not found or v_booking.provider_user_id <> v_actor then raise exception 'Only the booking provider may accept this request.' using errcode='42501'; end if;
  if v_booking.status <> 'PENDING_PROVIDER' then raise exception 'This booking is no longer pending.' using errcode='22023'; end if;
  update public.service_centre_bookings
  set status='ACCEPTED_AWAITING_PAYMENT', commitment_fee_amount=100.00, accepted_at=now(), updated_at=now()
  where id=p_booking_id;
  perform private.service_centre_append_event(p_booking_id,v_actor,'BOOKING_ACCEPTED','PENDING_PROVIDER','ACCEPTED_AWAITING_PAYMENT',p_idempotency_key,jsonb_build_object('commitmentFeeAmount','100.00','currencyCode','ZAR'));
  return private.service_centre_booking_payload(p_booking_id,v_actor);
end;
$$;

create or replace function public.service_centre_decline_booking(p_booking_id uuid, p_idempotency_key uuid)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_actor uuid := private.service_centre_actor();
  v_booking public.service_centre_bookings%rowtype;
begin
  if p_idempotency_key is null then raise exception 'Idempotency key is required.' using errcode='22023'; end if;
  if exists(select 1 from public.service_centre_booking_events e where e.actor_user_id=v_actor and e.event_type='BOOKING_DECLINED' and e.idempotency_key=p_idempotency_key) then
    return private.service_centre_booking_payload(p_booking_id,v_actor);
  end if;
  select * into v_booking from public.service_centre_bookings b where b.id=p_booking_id for update;
  if not found or v_booking.provider_user_id <> v_actor then raise exception 'Only the booking provider may decline this request.' using errcode='42501'; end if;
  if v_booking.status <> 'PENDING_PROVIDER' then raise exception 'This booking is no longer pending.' using errcode='22023'; end if;
  update public.service_centre_bookings set status='DECLINED',declined_at=now(),updated_at=now() where id=p_booking_id;
  perform private.service_centre_append_event(p_booking_id,v_actor,'BOOKING_DECLINED','PENDING_PROVIDER','DECLINED',p_idempotency_key,'{}'::jsonb);
  return private.service_centre_booking_payload(p_booking_id,v_actor);
end;
$$;

create or replace function public.service_centre_cancel_booking(p_booking_id uuid, p_idempotency_key uuid)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_actor uuid := private.service_centre_actor();
  v_booking public.service_centre_bookings%rowtype;
  v_from text;
begin
  if p_idempotency_key is null then raise exception 'Idempotency key is required.' using errcode='22023'; end if;
  if exists(select 1 from public.service_centre_booking_events e where e.actor_user_id=v_actor and e.event_type='BOOKING_CANCELLED' and e.idempotency_key=p_idempotency_key) then
    return private.service_centre_booking_payload(p_booking_id,v_actor);
  end if;
  select * into v_booking from public.service_centre_bookings b where b.id=p_booking_id for update;
  if not found or v_actor not in (v_booking.customer_user_id,v_booking.provider_user_id) then raise exception 'Only a booking participant may cancel.' using errcode='42501'; end if;
  if v_booking.status not in ('PENDING_PROVIDER','ACCEPTED_AWAITING_PAYMENT') then raise exception 'This booking can no longer be cancelled in the MVP flow.' using errcode='22023'; end if;
  v_from := v_booking.status;
  update public.service_centre_bookings set status='CANCELLED',cancelled_at=now(),updated_at=now() where id=p_booking_id;
  update public.service_centre_booking_payments set status='CANCELLED',updated_at=now() where booking_id=p_booking_id and status in ('CREATED','PENDING');
  perform private.service_centre_append_event(p_booking_id,v_actor,'BOOKING_CANCELLED',v_from,'CANCELLED',p_idempotency_key,'{}'::jsonb);
  return private.service_centre_booking_payload(p_booking_id,v_actor);
end;
$$;

create or replace function public.service_centre_complete_booking(p_booking_id uuid, p_idempotency_key uuid)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_actor uuid := private.service_centre_actor();
  v_booking public.service_centre_bookings%rowtype;
begin
  if p_idempotency_key is null then raise exception 'Idempotency key is required.' using errcode='22023'; end if;
  if exists(select 1 from public.service_centre_booking_events e where e.actor_user_id=v_actor and e.event_type='BOOKING_COMPLETED' and e.idempotency_key=p_idempotency_key) then
    return private.service_centre_booking_payload(p_booking_id,v_actor);
  end if;
  select * into v_booking from public.service_centre_bookings b where b.id=p_booking_id for update;
  if not found or v_booking.provider_user_id <> v_actor then raise exception 'Only the provider may complete this booking.' using errcode='42501'; end if;
  if v_booking.status <> 'CONFIRMED' then raise exception 'Only a secured booking can be completed.' using errcode='22023'; end if;
  if v_booking.requested_start_at > now() then raise exception 'The service date has not arrived yet.' using errcode='22023'; end if;
  update public.service_centre_bookings set status='COMPLETED',completed_at=now(),updated_at=now() where id=p_booking_id;
  perform private.service_centre_append_event(p_booking_id,v_actor,'BOOKING_COMPLETED','CONFIRMED','COMPLETED',p_idempotency_key,'{}'::jsonb);
  return private.service_centre_booking_payload(p_booking_id,v_actor);
end;
$$;

create or replace function public.service_centre_my_bookings()
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_actor uuid := private.service_centre_actor();
  v_result jsonb;
begin
  select coalesce(jsonb_agg(private.service_centre_booking_payload(x.id,v_actor) order by x.created_at desc),'[]'::jsonb)
  into v_result
  from public.service_centre_bookings x
  where v_actor in (x.customer_user_id,x.provider_user_id);
  return v_result;
end;
$$;

create or replace function public.service_centre_booking_detail(p_booking_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_actor uuid := private.service_centre_actor();
  v_result jsonb;
begin
  select private.service_centre_booking_payload(p_booking_id,v_actor) into v_result;
  if v_result is null then raise exception 'Booking not found.' using errcode='P0002'; end if;
  return v_result;
end;
$$;

create or replace function public.service_centre_booking_messages(p_booking_id uuid, p_limit integer default 100)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_actor uuid := private.service_centre_actor();
  v_limit integer := greatest(1,least(coalesce(p_limit,100),200));
  v_result jsonb;
begin
  if not exists(select 1 from public.service_centre_bookings b where b.id=p_booking_id and v_actor in (b.customer_user_id,b.provider_user_id)) then
    raise exception 'Only booking participants may read this chat.' using errcode='42501';
  end if;
  select coalesce(jsonb_agg(jsonb_build_object(
      'id',m.id,'bookingId',m.booking_id,'senderUserId',m.sender_user_id,
      'senderDisplayName',p.display_name,'senderAvatarUrl',p.avatar_url,
      'body',m.body,'isMine',m.sender_user_id=v_actor,'createdAt',m.created_at
    ) order by m.created_at,m.id),'[]'::jsonb)
  into v_result
  from (
    select * from public.service_centre_booking_messages
    where booking_id=p_booking_id order by created_at desc,id desc limit v_limit
  ) m
  join public.profiles p on p.id=m.sender_user_id;
  return v_result;
end;
$$;

create or replace function public.service_centre_send_message(
  p_booking_id uuid,
  p_body text,
  p_idempotency_key uuid
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_actor uuid := private.service_centre_actor();
  v_body text := trim(coalesce(p_body,''));
  v_message public.service_centre_booking_messages%rowtype;
  v_count integer;
begin
  if p_idempotency_key is null then raise exception 'Idempotency key is required.' using errcode='22023'; end if;
  select * into v_message from public.service_centre_booking_messages m where m.sender_user_id=v_actor and m.idempotency_key=p_idempotency_key;
  if found then return jsonb_build_object('id',v_message.id,'bookingId',v_message.booking_id,'senderUserId',v_message.sender_user_id,'body',v_message.body,'createdAt',v_message.created_at); end if;
  if char_length(v_body) not between 1 and 1000 then raise exception 'Message must be between 1 and 1000 characters.' using errcode='22023'; end if;
  if not exists(select 1 from public.service_centre_bookings b where b.id=p_booking_id and v_actor in (b.customer_user_id,b.provider_user_id)) then
    raise exception 'Only booking participants may send messages.' using errcode='42501';
  end if;
  select count(*) into v_count from public.service_centre_booking_messages m where m.sender_user_id=v_actor and m.created_at > now()-interval '1 minute';
  if v_count >= 20 then raise exception 'Too many messages. Try again shortly.' using errcode='P0001'; end if;
  insert into public.service_centre_booking_messages(booking_id,sender_user_id,body,idempotency_key)
  values(p_booking_id,v_actor,v_body,p_idempotency_key) returning * into v_message;
  return jsonb_build_object('id',v_message.id,'bookingId',v_message.booking_id,'senderUserId',v_message.sender_user_id,'body',v_message.body,'createdAt',v_message.created_at);
end;
$$;

create or replace function public.service_centre_prepare_commitment_payment(p_booking_id uuid, p_idempotency_key uuid)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_actor uuid := private.service_centre_actor();
  v_booking public.service_centre_bookings%rowtype;
  v_payment public.service_centre_booking_payments%rowtype;
begin
  if p_idempotency_key is null then raise exception 'Idempotency key is required.' using errcode='22023'; end if;
  select * into v_booking from public.service_centre_bookings b where b.id=p_booking_id for update;
  if not found or v_booking.customer_user_id <> v_actor then raise exception 'Only the customer may secure this booking.' using errcode='42501'; end if;
  if v_booking.status <> 'ACCEPTED_AWAITING_PAYMENT' or v_booking.commitment_fee_amount is null then raise exception 'This booking is not awaiting a commitment fee.' using errcode='22023'; end if;
  select * into v_payment from public.service_centre_booking_payments p
  where p.booking_id=p_booking_id and p.status in ('CREATED','PENDING','PAID') order by p.created_at desc limit 1 for update;
  if not found then
    insert into public.service_centre_booking_payments(booking_id,provider,purpose,amount,currency_code,status,idempotency_key,initiated_by)
    values(p_booking_id,'YOCO','COMMITMENT_FEE',v_booking.commitment_fee_amount,'ZAR','CREATED',p_idempotency_key,v_actor)
    returning * into v_payment;
    perform private.service_centre_append_event(p_booking_id,v_actor,'PAYMENT_CREATED',v_booking.status,v_booking.status,p_idempotency_key,jsonb_build_object('paymentId',v_payment.id));
  end if;
  return jsonb_build_object(
    'paymentId',v_payment.id,'bookingId',v_payment.booking_id,
    'amount',v_payment.amount::text,'amountCents',round(v_payment.amount*100)::integer,
    'currencyCode',v_payment.currency_code,'status',v_payment.status,
    'externalCheckoutId',v_payment.external_checkout_id,'idempotencyKey',v_payment.idempotency_key
  );
end;
$$;

create or replace function public.service_centre_attach_commitment_checkout(p_payment_id uuid, p_external_checkout_id text)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_payment public.service_centre_booking_payments%rowtype;
  v_checkout text := trim(coalesce(p_external_checkout_id,''));
begin
  if current_user not in ('postgres','service_role') then raise exception 'Backend authority is required.' using errcode='42501'; end if;
  if char_length(v_checkout) not between 3 and 160 then raise exception 'Invalid checkout identifier.' using errcode='22023'; end if;
  select * into v_payment from public.service_centre_booking_payments p where p.id=p_payment_id for update;
  if not found then raise exception 'Payment not found.' using errcode='P0002'; end if;
  if v_payment.status not in ('CREATED','PENDING') then raise exception 'Payment cannot accept a checkout in its current state.' using errcode='22023'; end if;
  if v_payment.external_checkout_id is not null and v_payment.external_checkout_id <> v_checkout then raise exception 'Payment is already bound to another checkout.' using errcode='22023'; end if;
  update public.service_centre_booking_payments set external_checkout_id=v_checkout,status='PENDING',updated_at=now() where id=p_payment_id;
  return jsonb_build_object('paymentId',p_payment_id,'externalCheckoutId',v_checkout,'status','PENDING');
end;
$$;

create or replace function public.service_centre_confirm_commitment_payment(
  p_external_checkout_id text,
  p_external_payment_id text,
  p_amount_cents integer,
  p_currency_code text
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_payment public.service_centre_booking_payments%rowtype;
  v_booking public.service_centre_bookings%rowtype;
  v_category text;
begin
  if current_user not in ('postgres','service_role') then raise exception 'Backend authority is required.' using errcode='42501'; end if;
  if trim(coalesce(p_external_checkout_id,''))='' or trim(coalesce(p_external_payment_id,''))='' then raise exception 'Payment identifiers are required.' using errcode='22023'; end if;
  if p_amount_cents is null or p_amount_cents < 0 or upper(trim(coalesce(p_currency_code,''))) <> 'ZAR' then raise exception 'Invalid payment amount or currency.' using errcode='22023'; end if;
  select * into v_payment from public.service_centre_booking_payments p where p.external_checkout_id=p_external_checkout_id for update;
  if not found then raise exception 'Payment checkout not found.' using errcode='P0002'; end if;
  if round(v_payment.amount*100)::integer <> p_amount_cents or v_payment.currency_code <> 'ZAR' then raise exception 'Payment amount does not match the booking commitment fee.' using errcode='22023'; end if;
  select * into v_booking from public.service_centre_bookings b where b.id=v_payment.booking_id for update;
  if not found then raise exception 'Booking not found.' using errcode='P0002'; end if;
  if v_payment.status='PAID' then
    if v_payment.external_payment_id is distinct from p_external_payment_id then raise exception 'Payment was already confirmed by another provider payment.' using errcode='22023'; end if;
  else
    if v_payment.status not in ('CREATED','PENDING') or v_booking.status <> 'ACCEPTED_AWAITING_PAYMENT' then raise exception 'Payment or booking is not awaiting confirmation.' using errcode='22023'; end if;
    update public.service_centre_booking_payments set status='PAID',external_payment_id=p_external_payment_id,paid_at=now(),updated_at=now() where id=v_payment.id;
    update public.service_centre_bookings set status='CONFIRMED',confirmed_at=now(),updated_at=now() where id=v_booking.id;
    perform private.service_centre_append_event(v_booking.id,null,'PAYMENT_CONFIRMED','ACCEPTED_AWAITING_PAYMENT','CONFIRMED',null,jsonb_build_object('paymentId',v_payment.id,'externalPaymentId',p_external_payment_id));
  end if;
  select c.name into v_category from public.marketplace_categories c where c.id=v_booking.category_id;
  return jsonb_build_object(
    'bookingId',v_booking.id,'paymentId',v_payment.id,'status','CONFIRMED',
    'customerUserId',v_booking.customer_user_id,'providerUserId',v_booking.provider_user_id,
    'categoryName',v_category,'requestedStartAt',v_booking.requested_start_at
  );
end;
$$;

create or replace function public.service_centre_notification_context(
  p_booking_id uuid,
  p_event_type text,
  p_message_id uuid default null
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_actor uuid := private.service_centre_actor();
  v_booking public.service_centre_bookings%rowtype;
  v_event text := upper(trim(coalesce(p_event_type,'')));
  v_recipient uuid;
  v_actor_name text;
  v_title text;
  v_body text;
begin
  select * into v_booking from public.service_centre_bookings b where b.id=p_booking_id;
  if not found or v_actor not in (v_booking.customer_user_id,v_booking.provider_user_id) then raise exception 'Only a booking participant may create its notification.' using errcode='42501'; end if;
  select p.display_name into v_actor_name from public.profiles p where p.id=v_actor;
  v_recipient := case when v_actor=v_booking.customer_user_id then v_booking.provider_user_id else v_booking.customer_user_id end;

  if v_event='SERVICE_BOOKING_NEW' then
    if v_actor<>v_booking.customer_user_id or v_booking.status<>'PENDING_PROVIDER' then raise exception 'Invalid new-booking notification.' using errcode='42501'; end if;
    v_title:='New booking request'; v_body:=v_actor_name||' sent a '||v_booking.category_name_snapshot||' booking offer.';
  elsif v_event='SERVICE_BOOKING_ACCEPTED' then
    if v_actor<>v_booking.provider_user_id or v_booking.status<>'ACCEPTED_AWAITING_PAYMENT' then raise exception 'Invalid accepted-booking notification.' using errcode='42501'; end if;
    v_title:='Booking accepted'; v_body:='Your provider accepted the booking. Secure the date with the commitment fee.';
  elsif v_event='SERVICE_BOOKING_DECLINED' then
    if v_actor<>v_booking.provider_user_id or v_booking.status<>'DECLINED' then raise exception 'Invalid declined-booking notification.' using errcode='42501'; end if;
    v_title:='Booking declined'; v_body:='The provider declined this booking request.';
  elsif v_event='SERVICE_BOOKING_MESSAGE' then
    if p_message_id is null or not exists(select 1 from public.service_centre_booking_messages m where m.id=p_message_id and m.booking_id=p_booking_id and m.sender_user_id=v_actor) then raise exception 'Invalid booking-message notification.' using errcode='42501'; end if;
    v_title:='New booking message'; v_body:=v_actor_name||' sent you a message.';
  elsif v_event='SERVICE_BOOKING_COMPLETED' then
    if v_actor<>v_booking.provider_user_id or v_booking.status<>'COMPLETED' then raise exception 'Invalid completed-booking notification.' using errcode='42501'; end if;
    v_title:='Booking completed'; v_body:='Your '||v_booking.category_name_snapshot||' booking was marked completed.';
  elsif v_event='SERVICE_BOOKING_CANCELLED' then
    if v_booking.status<>'CANCELLED' then raise exception 'Invalid cancelled-booking notification.' using errcode='42501'; end if;
    v_title:='Booking cancelled'; v_body:=v_actor_name||' cancelled the booking.';
  else
    raise exception 'Unsupported Service Centre notification event.' using errcode='22023';
  end if;

  return jsonb_build_object(
    'recipientUserId',v_recipient,'title',v_title,'body',v_body,
    'payload',jsonb_build_object(
      'notification_type',v_event,
      'booking_id',p_booking_id::text,
      'route','rtc://service-centre/booking/'||p_booking_id::text,
      'message_id',case when p_message_id is null then '' else p_message_id::text end
    )
  );
end;
$$;

-- Public client RPCs are authenticated-only. Backend payment mutations remain service-role-only.
revoke all on function public.service_centre_categories() from public, anon;
grant execute on function public.service_centre_categories() to authenticated;
revoke all on function public.service_centre_local_radar(uuid,double precision,double precision,text,integer,integer,integer) from public, anon;
grant execute on function public.service_centre_local_radar(uuid,double precision,double precision,text,integer,integer,integer) to authenticated;
revoke all on function public.service_centre_my_provider_profile() from public, anon;
grant execute on function public.service_centre_my_provider_profile() to authenticated;
revoke all on function public.service_centre_provider_detail(text) from public, anon;
grant execute on function public.service_centre_provider_detail(text) to authenticated;
revoke all on function public.service_centre_upsert_provider_profile(uuid,text,double precision,double precision,numeric,integer,uuid) from public, anon;
grant execute on function public.service_centre_upsert_provider_profile(uuid,text,double precision,double precision,numeric,integer,uuid) to authenticated;
revoke all on function public.service_centre_set_provider_active(boolean) from public, anon;
grant execute on function public.service_centre_set_provider_active(boolean) to authenticated;
revoke all on function public.service_centre_create_booking(uuid,timestamptz,text,numeric,uuid,uuid,uuid) from public, anon;
grant execute on function public.service_centre_create_booking(uuid,timestamptz,text,numeric,uuid,uuid,uuid) to authenticated;
revoke all on function public.service_centre_accept_booking(uuid,uuid) from public, anon;
grant execute on function public.service_centre_accept_booking(uuid,uuid) to authenticated;
revoke all on function public.service_centre_decline_booking(uuid,uuid) from public, anon;
grant execute on function public.service_centre_decline_booking(uuid,uuid) to authenticated;
revoke all on function public.service_centre_cancel_booking(uuid,uuid) from public, anon;
grant execute on function public.service_centre_cancel_booking(uuid,uuid) to authenticated;
revoke all on function public.service_centre_complete_booking(uuid,uuid) from public, anon;
grant execute on function public.service_centre_complete_booking(uuid,uuid) to authenticated;
revoke all on function public.service_centre_my_bookings() from public, anon;
grant execute on function public.service_centre_my_bookings() to authenticated;
revoke all on function public.service_centre_booking_detail(uuid) from public, anon;
grant execute on function public.service_centre_booking_detail(uuid) to authenticated;
revoke all on function public.service_centre_booking_messages(uuid,integer) from public, anon;
grant execute on function public.service_centre_booking_messages(uuid,integer) to authenticated;
revoke all on function public.service_centre_send_message(uuid,text,uuid) from public, anon;
grant execute on function public.service_centre_send_message(uuid,text,uuid) to authenticated;
revoke all on function public.service_centre_prepare_commitment_payment(uuid,uuid) from public, anon;
grant execute on function public.service_centre_prepare_commitment_payment(uuid,uuid) to authenticated;
revoke all on function public.service_centre_notification_context(uuid,text,uuid) from public, anon;
grant execute on function public.service_centre_notification_context(uuid,text,uuid) to authenticated;

revoke all on function public.service_centre_attach_commitment_checkout(uuid,text) from public, anon, authenticated;
grant execute on function public.service_centre_attach_commitment_checkout(uuid,text) to service_role;
revoke all on function public.service_centre_confirm_commitment_payment(text,text,integer,text) from public, anon, authenticated;
grant execute on function public.service_centre_confirm_commitment_payment(text,text,integer,text) to service_role;

commit;
