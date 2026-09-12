begin;

-- RTC Community Marketplace v1. This migration is additive and intended for the
-- non-production project only until an explicit production promotion is authorised.
create extension if not exists postgis with schema extensions;
create extension if not exists pg_trgm with schema extensions;

alter table public.operational_work_items
  drop constraint if exists operational_work_items_source_type_check;
alter table public.operational_work_items
  add constraint operational_work_items_source_type_check check (source_type in (
    'MODERATION_REPORT', 'NOTICE_REVIEW', 'ALERT_DELIVERY_FAILURE', 'OPERATIONAL_INCIDENT',
    'CONTROL_EXPIRY', 'MARKETPLACE_SUBMISSION', 'MARKETPLACE_REVIEW_REPORT', 'MARKETPLACE_VERIFICATION'
  ));

create table if not exists public.marketplace_categories (
  id uuid primary key default gen_random_uuid(),
  parent_id uuid references public.marketplace_categories(id) on delete restrict,
  name text not null check (char_length(trim(name)) between 2 and 80),
  slug text not null unique check (slug ~ '^[a-z0-9]+(?:-[a-z0-9]+)*$'),
  description text not null default '',
  icon_key text not null default 'storefront',
  regulated boolean not null default false,
  is_active boolean not null default true,
  display_order integer not null default 0,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique(parent_id, name)
);

create table if not exists public.marketplace_businesses (
  id uuid primary key default gen_random_uuid(),
  slug text not null unique check (slug ~ '^[a-z0-9]+(?:-[a-z0-9]+)*$'),
  created_by uuid not null references auth.users(id) on delete restrict,
  lifecycle_state text not null default 'DRAFT' check (lifecycle_state in ('DRAFT', 'PENDING_REVIEW', 'PUBLISHED', 'SUSPENDED', 'ARCHIVED')),
  current_public_revision_id uuid,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  published_at timestamptz,
  suspended_at timestamptz,
  archived_at timestamptz,
  create_idempotency_key uuid unique
);

create table if not exists public.marketplace_business_revisions (
  id uuid primary key default gen_random_uuid(),
  business_id uuid not null references public.marketplace_businesses(id) on delete cascade,
  revision_number integer not null,
  state text not null default 'DRAFT' check (state in ('DRAFT', 'SUBMITTED', 'CHANGES_REQUESTED', 'REJECTED', 'PUBLISHED', 'ARCHIVED')),
  display_name text not null default '' check (char_length(display_name) <= 120),
  legal_name text,
  business_type text not null default 'PHYSICAL' check (business_type in ('PHYSICAL', 'SERVICE_AREA', 'HYBRID')),
  registration_number text,
  owner_declaration_at timestamptz,
  tagline text not null default '' check (char_length(tagline) <= 180),
  description text not null default '' check (char_length(description) <= 6000),
  languages text[] not null default '{}',
  service_modes text[] not null default '{}',
  payment_methods text[] not null default '{}',
  public_phone text,
  whatsapp_enabled boolean not null default false,
  public_email text,
  website_url text,
  social_links jsonb not null default '{}'::jsonb,
  public_contact_consent_at timestamptz,
  public_address_consent_at timestamptz,
  terms_version text,
  privacy_version text,
  no_endorsement_acknowledged_at timestamptz,
  accuracy_declared_at timestamptz,
  submitted_at timestamptz,
  reviewed_at timestamptz,
  reviewed_by uuid references auth.users(id) on delete set null,
  review_feedback text,
  created_by uuid not null references auth.users(id) on delete restrict,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique(business_id, revision_number)
);
alter table public.marketplace_businesses
  add constraint marketplace_businesses_current_revision_fk foreign key (current_public_revision_id)
  references public.marketplace_business_revisions(id) on delete set null;

create table if not exists public.marketplace_business_members (
  id uuid primary key default gen_random_uuid(),
  business_id uuid not null references public.marketplace_businesses(id) on delete cascade,
  user_id uuid not null references auth.users(id) on delete cascade,
  role text not null check (role in ('OWNER', 'MANAGER', 'EDITOR')),
  state text not null default 'ACTIVE' check (state in ('ACTIVE', 'REVOKED')),
  invited_by uuid references auth.users(id) on delete set null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique(business_id, user_id)
);

create table if not exists public.marketplace_business_invitations (
  id uuid primary key default gen_random_uuid(),
  business_id uuid not null references public.marketplace_businesses(id) on delete cascade,
  email text not null check (position('@' in email) > 1),
  role text not null check (role in ('MANAGER', 'EDITOR')),
  token_hash text not null unique,
  state text not null default 'PENDING' check (state in ('PENDING', 'ACCEPTED', 'REVOKED', 'EXPIRED')),
  invited_by uuid not null references auth.users(id) on delete restrict,
  expires_at timestamptz not null default now() + interval '14 days',
  accepted_by uuid references auth.users(id) on delete set null,
  accepted_at timestamptz,
  created_at timestamptz not null default now()
);

create table if not exists public.marketplace_business_claims (
  id uuid primary key default gen_random_uuid(),
  business_id uuid not null references public.marketplace_businesses(id) on delete cascade,
  claimant_id uuid not null references auth.users(id) on delete cascade,
  state text not null default 'SUBMITTED' check (state in ('SUBMITTED', 'APPROVED', 'REJECTED', 'CANCELLED')),
  claim_note text,
  reviewed_by uuid references auth.users(id) on delete set null,
  reviewed_at timestamptz,
  created_at timestamptz not null default now(),
  unique(business_id, claimant_id, state)
);

create table if not exists public.marketplace_revision_categories (
  revision_id uuid not null references public.marketplace_business_revisions(id) on delete cascade,
  category_id uuid not null references public.marketplace_categories(id) on delete restrict,
  is_primary boolean not null default false,
  created_at timestamptz not null default now(),
  primary key(revision_id, category_id)
);
create unique index if not exists marketplace_one_primary_category_per_revision
  on public.marketplace_revision_categories(revision_id) where is_primary;

create table if not exists public.marketplace_business_locations (
  id uuid primary key default gen_random_uuid(),
  revision_id uuid not null references public.marketplace_business_revisions(id) on delete cascade,
  label text not null default '' check (char_length(label) <= 120),
  address_line1 text,
  address_line2 text,
  locality text not null default '' check (char_length(locality) <= 120),
  municipality text,
  province text,
  postal_code text,
  country_code text not null default 'ZA' check (country_code ~ '^[A-Z]{2}$'),
  location_type text not null default 'PHYSICAL' check (location_type in ('PHYSICAL', 'SERVICE_AREA')),
  address_visibility text not null default 'EXACT' check (address_visibility in ('EXACT', 'AREA_ONLY', 'HIDDEN')),
  latitude double precision,
  longitude double precision,
  private_point extensions.geography(Point, 4326),
  public_point extensions.geography(Point, 4326),
  service_radius_metres integer check (service_radius_metres between 100 and 100000),
  timezone text not null default 'Africa/Johannesburg',
  is_primary boolean not null default false,
  phone_override text,
  directions_note text,
  accessibility_features text[] not null default '{}',
  parking_note text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint marketplace_location_coordinates_consistent check (
    (latitude is null and longitude is null and private_point is null)
    or (latitude between -90 and 90 and longitude between -180 and 180 and private_point is not null)
  )
);
create unique index if not exists marketplace_one_primary_location_per_revision
  on public.marketplace_business_locations(revision_id) where is_primary;
create index if not exists marketplace_locations_private_point_gist
  on public.marketplace_business_locations using gist(private_point);
create index if not exists marketplace_locations_public_point_gist
  on public.marketplace_business_locations using gist(public_point);
create index if not exists marketplace_locations_locality_idx
  on public.marketplace_business_locations(locality, revision_id);

create table if not exists public.marketplace_location_hours (
  id uuid primary key default gen_random_uuid(),
  location_id uuid not null references public.marketplace_business_locations(id) on delete cascade,
  day_of_week smallint not null check (day_of_week between 0 and 6),
  interval_order smallint not null default 1 check (interval_order in (1, 2)),
  state text not null check (state in ('OPEN', 'CLOSED', 'OPEN_24_HOURS', 'APPOINTMENT_ONLY')),
  opens_at time,
  closes_at time,
  created_at timestamptz not null default now(),
  unique(location_id, day_of_week, interval_order),
  constraint marketplace_hours_times check (
    (state = 'OPEN' and opens_at is not null and closes_at is not null)
    or (state <> 'OPEN' and opens_at is null and closes_at is null)
  )
);

create table if not exists public.marketplace_location_hour_exceptions (
  id uuid primary key default gen_random_uuid(),
  location_id uuid not null references public.marketplace_business_locations(id) on delete cascade,
  exception_date date not null,
  state text not null check (state in ('OPEN', 'CLOSED', 'OPEN_24_HOURS', 'APPOINTMENT_ONLY')),
  opens_at time,
  closes_at time,
  note text,
  created_at timestamptz not null default now(),
  unique(location_id, exception_date),
  constraint marketplace_exception_times check (
    (state = 'OPEN' and opens_at is not null and closes_at is not null)
    or (state <> 'OPEN' and opens_at is null and closes_at is null)
  )
);

create table if not exists public.marketplace_offerings (
  id uuid primary key default gen_random_uuid(),
  revision_id uuid not null references public.marketplace_business_revisions(id) on delete cascade,
  offering_type text not null check (offering_type in ('SERVICE', 'PRODUCT')),
  title text not null check (char_length(trim(title)) between 2 and 160),
  description text not null default '' check (char_length(description) <= 3000),
  price_type text not null check (price_type in ('FIXED', 'FROM', 'RANGE', 'QUOTE', 'FREE')),
  currency_code text not null default 'ZAR' check (currency_code ~ '^[A-Z]{3}$'),
  price_min numeric(12,2) check (price_min >= 0),
  price_max numeric(12,2) check (price_max >= 0),
  duration_minutes integer check (duration_minutes between 1 and 10080),
  availability_note text,
  display_order integer not null default 0,
  image_asset_id uuid,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint marketplace_offering_price_check check (
    (price_type = 'FREE' and price_min is null and price_max is null)
    or (price_type = 'QUOTE' and price_min is null and price_max is null)
    or (price_type in ('FIXED', 'FROM') and price_min is not null and price_max is null)
    or (price_type = 'RANGE' and price_min is not null and price_max is not null and price_max >= price_min)
  )
);

create table if not exists public.marketplace_offering_locations (
  offering_id uuid not null references public.marketplace_offerings(id) on delete cascade,
  location_id uuid not null references public.marketplace_business_locations(id) on delete cascade,
  primary key(offering_id, location_id)
);

create table if not exists public.marketplace_media_assets (
  id uuid primary key default gen_random_uuid(),
  revision_id uuid not null references public.marketplace_business_revisions(id) on delete cascade,
  offering_id uuid references public.marketplace_offerings(id) on delete set null,
  asset_type text not null check (asset_type in ('LOGO', 'COVER', 'GALLERY', 'OFFERING')),
  object_path text not null unique,
  content_type text not null check (content_type in ('image/jpeg', 'image/png', 'image/webp')),
  byte_size bigint not null check (byte_size between 1 and 10485760),
  width integer check (width between 1 and 6000),
  height integer check (height between 1 and 6000),
  alt_text text not null default '' check (char_length(alt_text) <= 280),
  focal_point jsonb not null default '{}'::jsonb,
  display_order integer not null default 0,
  state text not null default 'STAGED' check (state in ('STAGED', 'FINALIZED', 'REJECTED', 'DELETED')),
  uploaded_by uuid not null references auth.users(id) on delete restrict,
  upload_key uuid not null,
  created_at timestamptz not null default now(),
  finalized_at timestamptz,
  unique(revision_id, asset_type, upload_key)
);
alter table public.marketplace_offerings
  add constraint marketplace_offerings_image_asset_fk foreign key (image_asset_id)
  references public.marketplace_media_assets(id) on delete set null;

create table if not exists public.marketplace_submissions (
  id uuid primary key default gen_random_uuid(),
  business_id uuid not null references public.marketplace_businesses(id) on delete cascade,
  revision_id uuid not null references public.marketplace_business_revisions(id) on delete cascade,
  state text not null default 'SUBMITTED' check (state in ('SUBMITTED', 'ASSIGNED', 'CHANGES_REQUESTED', 'APPROVED', 'REJECTED', 'CANCELLED')),
  submitted_by uuid not null references auth.users(id) on delete restrict,
  assigned_to uuid references auth.users(id) on delete set null,
  feedback text,
  correlation_key uuid not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  decided_at timestamptz,
  decided_by uuid references auth.users(id) on delete set null,
  unique(revision_id),
  unique(submitted_by, correlation_key)
);

create table if not exists public.marketplace_business_verifications (
  id uuid primary key default gen_random_uuid(),
  business_id uuid not null references public.marketplace_businesses(id) on delete cascade,
  revision_id uuid not null references public.marketplace_business_revisions(id) on delete cascade,
  method text not null check (method in ('DECLARATION', 'REGISTRATION', 'DOCUMENT', 'MANUAL')),
  evidence_object_path text,
  state text not null default 'PENDING' check (state in ('PENDING', 'VERIFIED', 'REJECTED', 'EXPIRED')),
  expires_at timestamptz,
  reviewed_by uuid references auth.users(id) on delete set null,
  reviewed_at timestamptz,
  reviewer_note text,
  created_by uuid not null references auth.users(id) on delete restrict,
  created_at timestamptz not null default now()
);

create table if not exists public.marketplace_featured_placements (
  id uuid primary key default gen_random_uuid(),
  business_id uuid not null references public.marketplace_businesses(id) on delete cascade,
  starts_at timestamptz not null,
  ends_at timestamptz not null,
  priority integer not null default 0 check (priority between 0 and 1000),
  state text not null default 'SCHEDULED' check (state in ('SCHEDULED', 'ACTIVE', 'CANCELLED', 'EXPIRED')),
  created_by uuid not null references auth.users(id) on delete restrict,
  cancelled_by uuid references auth.users(id) on delete set null,
  cancelled_at timestamptz,
  created_at timestamptz not null default now(),
  constraint marketplace_featured_dates check (ends_at > starts_at)
);
create index if not exists marketplace_featured_active_index
  on public.marketplace_featured_placements(state, starts_at, ends_at, priority desc);

create table if not exists public.marketplace_reviews (
  id uuid primary key default gen_random_uuid(),
  business_id uuid not null references public.marketplace_businesses(id) on delete cascade,
  author_id uuid not null references auth.users(id) on delete cascade,
  rating smallint not null check (rating between 1 and 5),
  title text not null default '' check (char_length(title) <= 120),
  body text not null default '' check (char_length(body) <= 4000),
  state text not null default 'PUBLISHED' check (state in ('PUBLISHED', 'HIDDEN', 'REMOVED')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz,
  unique(business_id, author_id)
);

create table if not exists public.marketplace_review_versions (
  id uuid primary key default gen_random_uuid(),
  review_id uuid not null references public.marketplace_reviews(id) on delete cascade,
  rating smallint not null check (rating between 1 and 5),
  title text not null default '',
  body text not null default '',
  changed_by uuid not null references auth.users(id) on delete restrict,
  reason text,
  created_at timestamptz not null default now()
);

create table if not exists public.marketplace_review_responses (
  id uuid primary key default gen_random_uuid(),
  review_id uuid not null unique references public.marketplace_reviews(id) on delete cascade,
  business_id uuid not null references public.marketplace_businesses(id) on delete cascade,
  author_id uuid not null references auth.users(id) on delete restrict,
  body text not null check (char_length(trim(body)) between 2 and 3000),
  state text not null default 'PUBLISHED' check (state in ('PUBLISHED', 'HIDDEN', 'REMOVED')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.marketplace_review_reports (
  id uuid primary key default gen_random_uuid(),
  review_id uuid references public.marketplace_reviews(id) on delete cascade,
  response_id uuid references public.marketplace_review_responses(id) on delete cascade,
  reporter_id uuid not null references auth.users(id) on delete cascade,
  reason_code text not null check (reason_code in ('SPAM', 'HARASSMENT', 'HATE', 'PRIVACY', 'MISINFORMATION', 'OTHER')),
  details text,
  state text not null default 'OPEN' check (state in ('OPEN', 'UNDER_REVIEW', 'CLOSED', 'ESCALATED')),
  created_at timestamptz not null default now(),
  resolved_at timestamptz,
  resolved_by uuid references auth.users(id) on delete set null,
  constraint marketplace_report_target check ((review_id is not null)::integer + (response_id is not null)::integer = 1)
);

create table if not exists public.marketplace_review_helpful_votes (
  review_id uuid not null references public.marketplace_reviews(id) on delete cascade,
  voter_id uuid not null references auth.users(id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key(review_id, voter_id)
);

create table if not exists public.marketplace_favorites (
  business_id uuid not null references public.marketplace_businesses(id) on delete cascade,
  user_id uuid not null references auth.users(id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key(business_id, user_id)
);

create table if not exists public.marketplace_business_rating_stats (
  business_id uuid primary key references public.marketplace_businesses(id) on delete cascade,
  review_count integer not null default 0 check (review_count >= 0),
  rating_average numeric(3,2) not null default 0 check (rating_average between 0 and 5),
  weighted_score numeric(4,3) not null default 0 check (weighted_score between 0 and 5),
  rating_distribution jsonb not null default '{"1":0,"2":0,"3":0,"4":0,"5":0}'::jsonb,
  updated_at timestamptz not null default now()
);

create table if not exists public.marketplace_audit_events (
  id uuid primary key default gen_random_uuid(),
  business_id uuid references public.marketplace_businesses(id) on delete set null,
  revision_id uuid references public.marketplace_business_revisions(id) on delete set null,
  actor_id uuid references auth.users(id) on delete set null,
  event_type text not null,
  correlation_key uuid,
  metadata jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now()
);
create unique index if not exists marketplace_audit_idempotency_index
  on public.marketplace_audit_events(actor_id, event_type, correlation_key)
  where correlation_key is not null;

create index if not exists marketplace_revisions_business_state_index on public.marketplace_business_revisions(business_id, state, revision_number desc);
create index if not exists marketplace_members_user_index on public.marketplace_business_members(user_id, state, business_id);
create index if not exists marketplace_submissions_queue_index on public.marketplace_submissions(state, created_at, assigned_to);
create index if not exists marketplace_reviews_business_state_index on public.marketplace_reviews(business_id, state, created_at desc);
create index if not exists marketplace_reviews_author_index on public.marketplace_reviews(author_id, updated_at desc);
create index if not exists marketplace_review_reports_queue_index on public.marketplace_review_reports(state, created_at);
create index if not exists marketplace_media_revision_state_index on public.marketplace_media_assets(revision_id, state, asset_type, display_order);
create index if not exists marketplace_search_revision_name_trgm on public.marketplace_business_revisions using gin(display_name extensions.gin_trgm_ops);
create index if not exists marketplace_search_revision_tagline_trgm on public.marketplace_business_revisions using gin(tagline extensions.gin_trgm_ops);

alter table public.marketplace_categories enable row level security;
alter table public.marketplace_businesses enable row level security;
alter table public.marketplace_business_members enable row level security;
alter table public.marketplace_business_invitations enable row level security;
alter table public.marketplace_business_claims enable row level security;
alter table public.marketplace_business_revisions enable row level security;
alter table public.marketplace_revision_categories enable row level security;
alter table public.marketplace_business_locations enable row level security;
alter table public.marketplace_location_hours enable row level security;
alter table public.marketplace_location_hour_exceptions enable row level security;
alter table public.marketplace_offerings enable row level security;
alter table public.marketplace_offering_locations enable row level security;
alter table public.marketplace_media_assets enable row level security;
alter table public.marketplace_submissions enable row level security;
alter table public.marketplace_business_verifications enable row level security;
alter table public.marketplace_featured_placements enable row level security;
alter table public.marketplace_reviews enable row level security;
alter table public.marketplace_review_versions enable row level security;
alter table public.marketplace_review_responses enable row level security;
alter table public.marketplace_review_reports enable row level security;
alter table public.marketplace_review_helpful_votes enable row level security;
alter table public.marketplace_favorites enable row level security;
alter table public.marketplace_business_rating_stats enable row level security;
alter table public.marketplace_audit_events enable row level security;

revoke all on public.marketplace_categories, public.marketplace_businesses, public.marketplace_business_members,
  public.marketplace_business_invitations, public.marketplace_business_claims, public.marketplace_business_revisions,
  public.marketplace_revision_categories, public.marketplace_business_locations, public.marketplace_location_hours,
  public.marketplace_location_hour_exceptions, public.marketplace_offerings, public.marketplace_offering_locations,
  public.marketplace_media_assets, public.marketplace_submissions, public.marketplace_business_verifications,
  public.marketplace_featured_placements, public.marketplace_reviews, public.marketplace_review_versions,
  public.marketplace_review_responses, public.marketplace_review_reports, public.marketplace_review_helpful_votes,
  public.marketplace_favorites, public.marketplace_business_rating_stats, public.marketplace_audit_events
  from anon, authenticated;

create or replace function private.marketplace_actor()
returns uuid language plpgsql stable security definer set search_path = '' as $$
declare v_actor uuid := auth.uid();
begin
  if v_actor is null then raise exception 'A signed-in account is required.'; end if;
  return v_actor;
end;
$$;

create or replace function private.marketplace_is_member(p_business_id uuid, p_user_id uuid default auth.uid())
returns boolean language sql stable security definer set search_path = '' as $$
  select exists(select 1 from public.marketplace_business_members m
    where m.business_id = p_business_id and m.user_id = p_user_id and m.state = 'ACTIVE');
$$;

create or replace function private.marketplace_is_editor(p_business_id uuid, p_user_id uuid default auth.uid())
returns boolean language sql stable security definer set search_path = '' as $$
  select exists(select 1 from public.marketplace_business_members m
    where m.business_id = p_business_id and m.user_id = p_user_id and m.state = 'ACTIVE'
      and m.role in ('OWNER', 'MANAGER', 'EDITOR'));
$$;

create or replace function private.marketplace_assert_editor(p_business_id uuid)
returns uuid language plpgsql security definer set search_path = '' as $$
declare v_actor uuid := private.marketplace_actor();
begin
  if not private.marketplace_is_editor(p_business_id, v_actor) then
    raise exception 'You do not have permission to edit this business.';
  end if;
  return v_actor;
end;
$$;

create or replace function private.marketplace_assert_content_admin()
returns uuid language plpgsql security definer set search_path = '' as $$
declare v_actor uuid := private.marketplace_actor();
begin
  if not private.has_any_role(array['CONTENT_EDITOR', 'SYSTEM_ADMIN']::public.app_role[]) then
    raise exception 'Marketplace publication authority is required.';
  end if;
  return v_actor;
end;
$$;

create or replace function private.marketplace_assert_moderator()
returns uuid language plpgsql security definer set search_path = '' as $$
declare v_actor uuid := private.marketplace_actor();
begin
  if not private.has_any_role(array['MODERATOR', 'SYSTEM_ADMIN']::public.app_role[]) then
    raise exception 'Marketplace moderation authority is required.';
  end if;
  return v_actor;
end;
$$;

create or replace function private.marketplace_audit(
  p_actor uuid, p_event text, p_business uuid default null, p_revision uuid default null,
  p_metadata jsonb default '{}'::jsonb, p_key uuid default null
) returns void language plpgsql security definer set search_path = '' as $$
begin
  insert into public.marketplace_audit_events(business_id, revision_id, actor_id, event_type, correlation_key, metadata)
  values (p_business, p_revision, p_actor, p_event, p_key, coalesce(p_metadata, '{}'::jsonb))
  on conflict (actor_id, event_type, correlation_key) where correlation_key is not null do nothing;
  insert into public.audit_events(actor_id, event_type, entity_type, entity_id, result, metadata, source)
  values (p_actor, 'MARKETPLACE_' || p_event, 'MARKETPLACE_BUSINESS', p_business, 'SUCCESS', coalesce(p_metadata, '{}'::jsonb), 'marketplace')
  on conflict do nothing;
end;
$$;

create or replace function private.marketplace_notification(p_recipient uuid, p_title text, p_body text, p_route text, p_business uuid)
returns void language plpgsql security definer set search_path = '' as $$
begin
  insert into public.notification_events(recipient_id, notification_type, title, body, payload)
  values (p_recipient, 'MARKETPLACE', p_title, p_body, jsonb_build_object('business_id', p_business, 'route', p_route));
end;
$$;

create or replace function private.marketplace_current_editable_revision(p_business_id uuid, p_actor uuid)
returns uuid language plpgsql security definer set search_path = '' as $$
declare v_revision uuid; v_next integer;
begin
  select r.id into v_revision from public.marketplace_business_revisions r
   where r.business_id = p_business_id and r.state in ('DRAFT', 'CHANGES_REQUESTED')
   order by r.revision_number desc limit 1 for update;
  if v_revision is not null then return v_revision; end if;
  select coalesce(max(revision_number), 0) + 1 into v_next from public.marketplace_business_revisions where business_id = p_business_id;
  insert into public.marketplace_business_revisions(
    business_id, revision_number, state, display_name, legal_name, business_type, registration_number,
    owner_declaration_at, tagline, description, languages, service_modes, payment_methods, public_phone,
    whatsapp_enabled, public_email, website_url, social_links, public_contact_consent_at,
    public_address_consent_at, terms_version, privacy_version, no_endorsement_acknowledged_at,
    accuracy_declared_at, created_by
  )
  select r.business_id, v_next, 'DRAFT', r.display_name, r.legal_name, r.business_type, r.registration_number,
    r.owner_declaration_at, r.tagline, r.description, r.languages, r.service_modes, r.payment_methods,
    r.public_phone, r.whatsapp_enabled, r.public_email, r.website_url, r.social_links,
    r.public_contact_consent_at, r.public_address_consent_at, r.terms_version, r.privacy_version,
    r.no_endorsement_acknowledged_at, r.accuracy_declared_at, p_actor
  from public.marketplace_businesses b join public.marketplace_business_revisions r on r.id = b.current_public_revision_id
  where b.id = p_business_id returning id into v_revision;
  if v_revision is null then raise exception 'No editable revision is available.'; end if;
  insert into public.marketplace_revision_categories(revision_id, category_id, is_primary)
    select v_revision, rc.category_id, rc.is_primary from public.marketplace_revision_categories rc
    join public.marketplace_businesses b on b.current_public_revision_id = rc.revision_id where b.id = p_business_id;
  return v_revision;
end;
$$;

create or replace function private.marketplace_slug(p_name text, p_business_id uuid default null)
returns text language plpgsql security definer set search_path = '' as $$
declare v_base text := trim(both '-' from regexp_replace(lower(trim(p_name)), '[^a-z0-9]+', '-', 'g'));
declare v_slug text; v_suffix integer := 0;
begin
  if char_length(v_base) < 2 then v_base := 'business'; end if;
  v_base := left(v_base, 55); v_slug := v_base;
  while exists(select 1 from public.marketplace_businesses where slug = v_slug and id is distinct from p_business_id) loop
    v_suffix := v_suffix + 1; v_slug := v_base || '-' || v_suffix::text;
  end loop;
  return v_slug;
end;
$$;

create or replace function private.marketplace_upsert_rating_stats(p_business_id uuid)
returns void language plpgsql security definer set search_path = '' as $$
declare v_count integer; v_average numeric; v_distribution jsonb;
begin
  select count(*)::integer, coalesce(avg(rating), 0)::numeric(3,2),
    jsonb_build_object('1', count(*) filter(where rating=1), '2', count(*) filter(where rating=2),
      '3', count(*) filter(where rating=3), '4', count(*) filter(where rating=4), '5', count(*) filter(where rating=5))
  into v_count, v_average, v_distribution
  from public.marketplace_reviews where business_id = p_business_id and state = 'PUBLISHED';
  insert into public.marketplace_business_rating_stats(business_id, review_count, rating_average, weighted_score, rating_distribution, updated_at)
  values (p_business_id, v_count, v_average, ((v_count * v_average + 5 * 3.5) / greatest(v_count + 5, 1))::numeric(4,3), v_distribution, now())
  on conflict (business_id) do update set review_count=excluded.review_count, rating_average=excluded.rating_average,
    weighted_score=excluded.weighted_score, rating_distribution=excluded.rating_distribution, updated_at=now();
end;
$$;

create or replace function private.marketplace_review_stats_trigger()
returns trigger language plpgsql security definer set search_path = '' as $$
begin
  perform private.marketplace_upsert_rating_stats(coalesce(new.business_id, old.business_id));
  return coalesce(new, old);
end;
$$;
drop trigger if exists marketplace_review_stats_after_change on public.marketplace_reviews;
create trigger marketplace_review_stats_after_change after insert or update or delete on public.marketplace_reviews
for each row execute function private.marketplace_review_stats_trigger();

create or replace function private.marketplace_public_media_path(p_path text)
returns boolean language sql stable security definer set search_path = '' as $$
  select exists(
    select 1 from public.marketplace_media_assets a
    join public.marketplace_business_revisions r on r.id=a.revision_id and r.state='PUBLISHED'
    join public.marketplace_businesses b on b.current_public_revision_id=r.id and b.lifecycle_state='PUBLISHED'
    where a.object_path=p_path and a.state='FINALIZED'
  );
$$;
create or replace function private.marketplace_media_owner_path(p_path text, p_actor uuid default auth.uid())
returns boolean language sql stable security definer set search_path = '' as $$
  select exists(select 1 from public.marketplace_media_assets a
    join public.marketplace_business_revisions r on r.id=a.revision_id
    where a.object_path=p_path and private.marketplace_is_member(r.business_id, p_actor));
$$;
create or replace function private.marketplace_verification_owner_path(p_path text, p_actor uuid default auth.uid())
returns boolean language sql stable security definer set search_path = '' as $$
  select exists(select 1 from public.marketplace_business_verifications v
    where v.evidence_object_path=p_path and (
      private.marketplace_is_member(v.business_id, p_actor) or private.has_any_role(array['CONTENT_EDITOR','SYSTEM_ADMIN']::public.app_role[])
    ));
$$;

insert into storage.buckets(id, name, public, file_size_limit, allowed_mime_types)
values
  ('rtc-marketplace-media', 'rtc-marketplace-media', false, 10485760, array['image/jpeg','image/png','image/webp']),
  ('rtc-marketplace-verification', 'rtc-marketplace-verification', false, 10485760, array['application/pdf','image/jpeg','image/png'])
on conflict (id) do update set public=excluded.public, file_size_limit=excluded.file_size_limit, allowed_mime_types=excluded.allowed_mime_types;

drop policy if exists marketplace_media_select on storage.objects;
create policy marketplace_media_select on storage.objects for select to authenticated using (
  bucket_id='rtc-marketplace-media' and (
    private.marketplace_public_media_path(name) or private.marketplace_media_owner_path(name)
    or private.has_any_role(array['CONTENT_EDITOR','MODERATOR','SYSTEM_ADMIN']::public.app_role[])
  )
);
drop policy if exists marketplace_media_insert on storage.objects;
create policy marketplace_media_insert on storage.objects for insert to authenticated with check (
  bucket_id='rtc-marketplace-media' and (storage.foldername(name))[1]=(select auth.uid())::text
);
drop policy if exists marketplace_media_update on storage.objects;
create policy marketplace_media_update on storage.objects for update to authenticated using (
  bucket_id='rtc-marketplace-media' and private.marketplace_media_owner_path(name)
) with check (bucket_id='rtc-marketplace-media' and private.marketplace_media_owner_path(name));
drop policy if exists marketplace_media_delete on storage.objects;
create policy marketplace_media_delete on storage.objects for delete to authenticated using (
  bucket_id='rtc-marketplace-media' and private.marketplace_media_owner_path(name)
);
drop policy if exists marketplace_verification_select on storage.objects;
create policy marketplace_verification_select on storage.objects for select to authenticated using (
  bucket_id='rtc-marketplace-verification' and private.marketplace_verification_owner_path(name)
);
drop policy if exists marketplace_verification_insert on storage.objects;
create policy marketplace_verification_insert on storage.objects for insert to authenticated with check (
  bucket_id='rtc-marketplace-verification' and (storage.foldername(name))[1]=(select auth.uid())::text
);
drop policy if exists marketplace_verification_delete on storage.objects;
create policy marketplace_verification_delete on storage.objects for delete to authenticated using (
  bucket_id='rtc-marketplace-verification' and private.marketplace_verification_owner_path(name)
);

insert into public.marketplace_categories(name, slug, description, display_order)
values
 ('Food & Dining','food-dining','Restaurants, cafés, catering and local food.',1),
 ('Home Services','home-services','Maintenance, construction and household services.',2),
 ('Health & Wellness','health-wellness','Health, fitness and wellbeing providers.',3),
 ('Beauty & Personal Care','beauty-personal-care','Beauty and personal-care providers.',4),
 ('Retail & Shopping','retail-shopping','Local shops and retail services.',5),
 ('Automotive','automotive','Vehicle services and automotive specialists.',6),
 ('Professional Services','professional-services','Professional advice and business services.',7),
 ('Education & Childcare','education-childcare','Education, tuition and childcare.',8),
 ('Technology & Repair','technology-repair','Technology, repair and technical services.',9),
 ('Events & Entertainment','events-entertainment','Events, entertainment and creative services.',10),
 ('Transport & Delivery','transport-delivery','Transport and delivery providers.',11),
 ('Agriculture & Local Produce','agriculture-local-produce','Agriculture and local-produce providers.',12),
 ('Accommodation & Tourism','accommodation-tourism','Accommodation and local tourism.',13),
 ('Nonprofits & Community Services','nonprofits-community-services','Community organisations and nonprofit services.',14),
 ('Other','other','Other local business and services.',15)
on conflict (slug) do update set name=excluded.name, description=excluded.description, display_order=excluded.display_order, updated_at=now();

create or replace function public.marketplace_create_business_draft(p_display_name text, p_idempotency_key uuid)
returns jsonb language plpgsql security definer set search_path = '' as $$
declare v_actor uuid:=private.marketplace_actor(); v_business uuid; v_revision uuid; v_slug text; v_name text:=trim(coalesce(p_display_name,''));
begin
  if char_length(v_name) not between 2 and 120 then raise exception 'Enter a business name between 2 and 120 characters.'; end if;
  select b.id into v_business from public.marketplace_businesses b where b.created_by=v_actor and b.create_idempotency_key=p_idempotency_key;
  if v_business is not null then
    select id into v_revision from public.marketplace_business_revisions where business_id=v_business order by revision_number desc limit 1;
    return jsonb_build_object('businessId',v_business,'revisionId',v_revision,'state','DRAFT');
  end if;
  v_slug:=private.marketplace_slug(v_name);
  insert into public.marketplace_businesses(slug,created_by,create_idempotency_key) values(v_slug,v_actor,p_idempotency_key) returning id into v_business;
  insert into public.marketplace_business_revisions(business_id,revision_number,display_name,created_by) values(v_business,1,v_name,v_actor) returning id into v_revision;
  insert into public.marketplace_business_members(business_id,user_id,role,invited_by) values(v_business,v_actor,'OWNER',v_actor);
  insert into public.marketplace_business_rating_stats(business_id) values(v_business);
  perform private.marketplace_audit(v_actor,'DRAFT_CREATED',v_business,v_revision,jsonb_build_object('slug',v_slug),p_idempotency_key);
  return jsonb_build_object('businessId',v_business,'revisionId',v_revision,'state','DRAFT');
end;
$$;

create or replace function public.marketplace_save_identity(p_business_id uuid, p_payload jsonb, p_idempotency_key uuid)
returns jsonb language plpgsql security definer set search_path = '' as $$
declare v_actor uuid:=private.marketplace_assert_editor(p_business_id); v_revision uuid; v_name text:=trim(coalesce(p_payload->>'displayName',''));
begin
  if char_length(v_name) not between 2 and 120 then raise exception 'Enter a business name between 2 and 120 characters.'; end if;
  v_revision:=private.marketplace_current_editable_revision(p_business_id,v_actor);
  update public.marketplace_business_revisions set
    display_name=v_name, legal_name=nullif(trim(p_payload->>'legalName'),''),
    business_type=coalesce(nullif(p_payload->>'businessType',''),'PHYSICAL'), registration_number=nullif(trim(p_payload->>'registrationNumber'),''),
    owner_declaration_at=case when coalesce((p_payload->>'ownerDeclared')::boolean,false) then now() else owner_declaration_at end,
    tagline=left(trim(coalesce(p_payload->>'tagline','')),180), description=left(trim(coalesce(p_payload->>'description','')),6000),
    languages=coalesce(array(select jsonb_array_elements_text(coalesce(p_payload->'languages','[]'::jsonb))),'{}'),
    service_modes=coalesce(array(select jsonb_array_elements_text(coalesce(p_payload->'serviceModes','[]'::jsonb))),'{}'),
    payment_methods=coalesce(array(select jsonb_array_elements_text(coalesce(p_payload->'paymentMethods','[]'::jsonb))),'{}'),
    public_phone=nullif(trim(p_payload->>'phone'),''), whatsapp_enabled=coalesce((p_payload->>'whatsappEnabled')::boolean,false),
    public_email=nullif(trim(p_payload->>'email'),''), website_url=nullif(trim(p_payload->>'websiteUrl'),''),
    social_links=coalesce(p_payload->'socialLinks','{}'::jsonb), public_contact_consent_at=case when coalesce((p_payload->>'publicContactConsent')::boolean,false) then now() else null end,
    public_address_consent_at=case when coalesce((p_payload->>'publicAddressConsent')::boolean,false) then now() else null end,
    terms_version=nullif(p_payload->>'termsVersion',''), privacy_version=nullif(p_payload->>'privacyVersion',''),
    no_endorsement_acknowledged_at=case when coalesce((p_payload->>'noEndorsementAcknowledged')::boolean,false) then now() else null end,
    accuracy_declared_at=case when coalesce((p_payload->>'accuracyDeclared')::boolean,false) then now() else null end, updated_at=now()
  where id=v_revision;
  update public.marketplace_businesses set slug=private.marketplace_slug(v_name,p_business_id),updated_at=now() where id=p_business_id;
  delete from public.marketplace_revision_categories where revision_id=v_revision;
  insert into public.marketplace_revision_categories(revision_id,category_id,is_primary)
  select v_revision,(value->>'id')::uuid,coalesce((value->>'primary')::boolean,false)
  from jsonb_array_elements(coalesce(p_payload->'categories','[]'::jsonb));
  if not exists(select 1 from public.marketplace_revision_categories where revision_id=v_revision and is_primary) then
    raise exception 'Select one primary category.';
  end if;
  perform private.marketplace_audit(v_actor,'IDENTITY_SAVED',p_business_id,v_revision,'{}',p_idempotency_key);
  return jsonb_build_object('businessId',p_business_id,'revisionId',v_revision,'state','DRAFT');
end;
$$;

create or replace function public.marketplace_upsert_location(p_business_id uuid, p_location_id uuid default null, p_payload jsonb default '{}'::jsonb, p_idempotency_key uuid default null)
returns jsonb language plpgsql security definer set search_path = '' as $$
declare v_actor uuid:=private.marketplace_assert_editor(p_business_id); v_revision uuid; v_id uuid:=p_location_id; v_lat double precision:=nullif(p_payload->>'latitude','')::double precision; v_lon double precision:=nullif(p_payload->>'longitude','')::double precision; v_visibility text:=coalesce(nullif(p_payload->>'addressVisibility',''),'EXACT'); v_private extensions.geography; v_public extensions.geography;
begin
  v_revision:=private.marketplace_current_editable_revision(p_business_id,v_actor);
  if v_lat is not null and (v_lat not between -90 and 90 or v_lon not between -180 and 180) then raise exception 'Use valid latitude and longitude.'; end if;
  if v_lat is not null then
    v_private:=extensions.ST_SetSRID(extensions.ST_MakePoint(v_lon,v_lat),4326)::extensions.geography;
    v_public:=case v_visibility when 'EXACT' then v_private when 'AREA_ONLY' then extensions.ST_SetSRID(extensions.ST_MakePoint(round(v_lon::numeric,2)::double precision,round(v_lat::numeric,2)::double precision),4326)::extensions.geography else null end;
  end if;
  if v_id is null then
    insert into public.marketplace_business_locations(revision_id,label,address_line1,address_line2,locality,municipality,province,postal_code,country_code,location_type,address_visibility,latitude,longitude,private_point,public_point,service_radius_metres,timezone,is_primary,phone_override,directions_note,accessibility_features,parking_note)
    values(v_revision,left(trim(coalesce(p_payload->>'label','')),120),nullif(trim(p_payload->>'addressLine1'),''),nullif(trim(p_payload->>'addressLine2'),''),left(trim(coalesce(p_payload->>'locality','')),120),nullif(trim(p_payload->>'municipality'),''),nullif(trim(p_payload->>'province'),''),nullif(trim(p_payload->>'postalCode'),''),coalesce(nullif(p_payload->>'countryCode',''),'ZA'),coalesce(nullif(p_payload->>'locationType',''),'PHYSICAL'),v_visibility,v_lat,v_lon,v_private,v_public,nullif(p_payload->>'serviceRadiusMetres','')::integer,coalesce(nullif(p_payload->>'timezone',''),'Africa/Johannesburg'),coalesce((p_payload->>'isPrimary')::boolean,false),nullif(trim(p_payload->>'phoneOverride'),''),nullif(trim(p_payload->>'directionsNote'),''),coalesce(array(select jsonb_array_elements_text(coalesce(p_payload->'accessibilityFeatures','[]'::jsonb))),'{}'),nullif(trim(p_payload->>'parkingNote'),'')) returning id into v_id;
  else
    update public.marketplace_business_locations set label=left(trim(coalesce(p_payload->>'label','')),120),address_line1=nullif(trim(p_payload->>'addressLine1'),''),address_line2=nullif(trim(p_payload->>'addressLine2'),''),locality=left(trim(coalesce(p_payload->>'locality','')),120),municipality=nullif(trim(p_payload->>'municipality'),''),province=nullif(trim(p_payload->>'province'),''),postal_code=nullif(trim(p_payload->>'postalCode'),''),country_code=coalesce(nullif(p_payload->>'countryCode',''),'ZA'),location_type=coalesce(nullif(p_payload->>'locationType',''),'PHYSICAL'),address_visibility=v_visibility,latitude=v_lat,longitude=v_lon,private_point=v_private,public_point=v_public,service_radius_metres=nullif(p_payload->>'serviceRadiusMetres','')::integer,timezone=coalesce(nullif(p_payload->>'timezone',''),'Africa/Johannesburg'),is_primary=coalesce((p_payload->>'isPrimary')::boolean,false),phone_override=nullif(trim(p_payload->>'phoneOverride'),''),directions_note=nullif(trim(p_payload->>'directionsNote'),''),accessibility_features=coalesce(array(select jsonb_array_elements_text(coalesce(p_payload->'accessibilityFeatures','[]'::jsonb))),'{}'),parking_note=nullif(trim(p_payload->>'parkingNote'),''),updated_at=now() where id=v_id and revision_id=v_revision;
    if not found then raise exception 'Location is not available for this draft.'; end if;
  end if;
  if coalesce((p_payload->>'isPrimary')::boolean,false) then update public.marketplace_business_locations set is_primary=false where revision_id=v_revision and id<>v_id; end if;
  perform private.marketplace_audit(v_actor,'LOCATION_SAVED',p_business_id,v_revision,jsonb_build_object('location_id',v_id),p_idempotency_key);
  return jsonb_build_object('locationId',v_id,'revisionId',v_revision);
end;
$$;

create or replace function public.marketplace_replace_location_hours(p_business_id uuid, p_location_id uuid, p_hours jsonb, p_exceptions jsonb default '[]'::jsonb, p_idempotency_key uuid default null)
returns jsonb language plpgsql security definer set search_path = '' as $$
declare v_actor uuid:=private.marketplace_assert_editor(p_business_id); v_revision uuid;
begin
  v_revision:=private.marketplace_current_editable_revision(p_business_id,v_actor);
  if not exists(select 1 from public.marketplace_business_locations where id=p_location_id and revision_id=v_revision) then raise exception 'Location is not available for this draft.'; end if;
  delete from public.marketplace_location_hours where location_id=p_location_id;
  insert into public.marketplace_location_hours(location_id,day_of_week,interval_order,state,opens_at,closes_at)
  select p_location_id,(v->>'dayOfWeek')::smallint,coalesce((v->>'intervalOrder')::smallint,1),v->>'state',nullif(v->>'opensAt','')::time,nullif(v->>'closesAt','')::time from jsonb_array_elements(coalesce(p_hours,'[]'::jsonb)) v;
  delete from public.marketplace_location_hour_exceptions where location_id=p_location_id;
  insert into public.marketplace_location_hour_exceptions(location_id,exception_date,state,opens_at,closes_at,note)
  select p_location_id,(v->>'date')::date,v->>'state',nullif(v->>'opensAt','')::time,nullif(v->>'closesAt','')::time,nullif(v->>'note','') from jsonb_array_elements(coalesce(p_exceptions,'[]'::jsonb)) v;
  perform private.marketplace_audit(v_actor,'HOURS_REPLACED',p_business_id,v_revision,jsonb_build_object('location_id',p_location_id),p_idempotency_key);
  return jsonb_build_object('locationId',p_location_id,'saved',true);
end;
$$;

create or replace function public.marketplace_upsert_offering(p_business_id uuid, p_offering_id uuid default null, p_payload jsonb default '{}'::jsonb, p_idempotency_key uuid default null)
returns jsonb language plpgsql security definer set search_path = '' as $$
declare v_actor uuid:=private.marketplace_assert_editor(p_business_id); v_revision uuid; v_id uuid:=p_offering_id; v_count integer;
begin
  v_revision:=private.marketplace_current_editable_revision(p_business_id,v_actor);
  if v_id is null then select count(*) into v_count from public.marketplace_offerings where revision_id=v_revision; if v_count>=50 then raise exception 'A maximum of 50 offerings is allowed.'; end if; end if;
  if v_id is null then insert into public.marketplace_offerings(revision_id,offering_type,title,description,price_type,currency_code,price_min,price_max,duration_minutes,availability_note,display_order)
    values(v_revision,coalesce(nullif(p_payload->>'offeringType',''),'SERVICE'),trim(p_payload->>'title'),left(coalesce(p_payload->>'description',''),3000),p_payload->>'priceType',coalesce(nullif(p_payload->>'currencyCode',''),'ZAR'),nullif(p_payload->>'priceMin','')::numeric,nullif(p_payload->>'priceMax','')::numeric,nullif(p_payload->>'durationMinutes','')::integer,nullif(p_payload->>'availabilityNote',''),coalesce((p_payload->>'displayOrder')::integer,0)) returning id into v_id;
  else update public.marketplace_offerings set offering_type=coalesce(nullif(p_payload->>'offeringType',''),'SERVICE'),title=trim(p_payload->>'title'),description=left(coalesce(p_payload->>'description',''),3000),price_type=p_payload->>'priceType',currency_code=coalesce(nullif(p_payload->>'currencyCode',''),'ZAR'),price_min=nullif(p_payload->>'priceMin','')::numeric,price_max=nullif(p_payload->>'priceMax','')::numeric,duration_minutes=nullif(p_payload->>'durationMinutes','')::integer,availability_note=nullif(p_payload->>'availabilityNote',''),display_order=coalesce((p_payload->>'displayOrder')::integer,0),updated_at=now() where id=v_id and revision_id=v_revision; if not found then raise exception 'Offering is not available for this draft.'; end if; end if;
  delete from public.marketplace_offering_locations where offering_id=v_id;
  insert into public.marketplace_offering_locations(offering_id,location_id) select v_id,value::uuid from jsonb_array_elements_text(coalesce(p_payload->'locationIds','[]'::jsonb));
  perform private.marketplace_audit(v_actor,'OFFERING_SAVED',p_business_id,v_revision,jsonb_build_object('offering_id',v_id),p_idempotency_key);
  return jsonb_build_object('offeringId',v_id,'revisionId',v_revision);
end;
$$;

create or replace function public.marketplace_begin_media_upload(p_business_id uuid, p_asset_type text, p_content_type text, p_byte_size bigint, p_alt_text text default '', p_upload_key uuid default null)
returns jsonb language plpgsql security definer set search_path = '' as $$
declare v_actor uuid:=private.marketplace_assert_editor(p_business_id); v_revision uuid; v_asset uuid; v_key uuid:=coalesce(p_upload_key,gen_random_uuid()); v_path text;
begin
  if p_asset_type not in ('LOGO','COVER','GALLERY','OFFERING') or p_content_type not in ('image/jpeg','image/png','image/webp') or p_byte_size not between 1 and 10485760 then raise exception 'Unsupported media metadata.'; end if;
  v_revision:=private.marketplace_current_editable_revision(p_business_id,v_actor);
  select id into v_asset from public.marketplace_media_assets where revision_id=v_revision and asset_type=p_asset_type and upload_key=v_key;
  if v_asset is null then
    v_path:=v_actor::text || '/' || p_business_id::text || '/' || v_revision::text || '/' || lower(p_asset_type) || '/' || v_key::text;
    insert into public.marketplace_media_assets(revision_id,asset_type,object_path,content_type,byte_size,alt_text,uploaded_by,upload_key) values(v_revision,p_asset_type,v_path,p_content_type,p_byte_size,left(coalesce(p_alt_text,''),280),v_actor,v_key) returning id into v_asset;
  else select object_path into v_path from public.marketplace_media_assets where id=v_asset; end if;
  return jsonb_build_object('assetId',v_asset,'objectPath',v_path,'uploadKey',v_key,'state','STAGED');
end;
$$;

create or replace function public.marketplace_finalize_media_upload(p_business_id uuid, p_asset_id uuid, p_width integer, p_height integer, p_focal_point jsonb default '{}'::jsonb, p_idempotency_key uuid default null)
returns jsonb language plpgsql security definer set search_path = '' as $$
declare v_actor uuid:=private.marketplace_assert_editor(p_business_id); v_revision uuid; v_path text;
begin
  v_revision:=private.marketplace_current_editable_revision(p_business_id,v_actor);
  update public.marketplace_media_assets set width=p_width,height=p_height,focal_point=coalesce(p_focal_point,'{}'::jsonb),state='FINALIZED',finalized_at=coalesce(finalized_at,now()) where id=p_asset_id and revision_id=v_revision and state in ('STAGED','FINALIZED') returning object_path into v_path;
  if v_path is null then raise exception 'Media asset is not available for finalization.'; end if;
  perform private.marketplace_audit(v_actor,'MEDIA_FINALIZED',p_business_id,v_revision,jsonb_build_object('asset_id',p_asset_id),p_idempotency_key);
  return jsonb_build_object('assetId',p_asset_id,'objectPath',v_path,'state','FINALIZED');
end;
$$;

create or replace function public.marketplace_delete_draft_media(p_business_id uuid, p_asset_id uuid, p_idempotency_key uuid default null)
returns jsonb language plpgsql security definer set search_path = '' as $$
declare v_actor uuid:=private.marketplace_assert_editor(p_business_id); v_revision uuid;
begin
  v_revision:=private.marketplace_current_editable_revision(p_business_id,v_actor);
  update public.marketplace_media_assets set state='DELETED' where id=p_asset_id and revision_id=v_revision and state in ('STAGED','FINALIZED');
  if not found then raise exception 'Media asset is not available for deletion.'; end if;
  perform private.marketplace_audit(v_actor,'MEDIA_DELETED',p_business_id,v_revision,jsonb_build_object('asset_id',p_asset_id),p_idempotency_key);
  return jsonb_build_object('assetId',p_asset_id,'state','DELETED');
end;
$$;

create or replace function public.marketplace_submit_business(p_business_id uuid, p_idempotency_key uuid)
returns jsonb language plpgsql security definer set search_path = '' as $$
declare v_actor uuid:=private.marketplace_assert_editor(p_business_id); v_revision uuid; v_submission uuid; v_name text;
begin
  v_revision:=private.marketplace_current_editable_revision(p_business_id,v_actor);
  select display_name into v_name from public.marketplace_business_revisions where id=v_revision for update;
  if char_length(trim(v_name))<2 or not exists(select 1 from public.marketplace_revision_categories where revision_id=v_revision and is_primary) or not exists(select 1 from public.marketplace_business_locations where revision_id=v_revision) then raise exception 'Complete identity, primary category and at least one location before submitting.'; end if;
  select id into v_submission from public.marketplace_submissions where revision_id=v_revision;
  if v_submission is not null then return jsonb_build_object('submissionId',v_submission,'revisionId',v_revision,'state','SUBMITTED'); end if;
  update public.marketplace_business_revisions set state='SUBMITTED',submitted_at=now(),updated_at=now() where id=v_revision and state in ('DRAFT','CHANGES_REQUESTED');
  if not found then raise exception 'This revision cannot be submitted.'; end if;
  insert into public.marketplace_submissions(business_id,revision_id,submitted_by,correlation_key) values(p_business_id,v_revision,v_actor,p_idempotency_key) returning id into v_submission;
  update public.marketplace_businesses set lifecycle_state=case when lifecycle_state='DRAFT' then 'PENDING_REVIEW' else lifecycle_state end,updated_at=now() where id=p_business_id;
  perform private.ops_upsert_work_item('MARKETPLACE_SUBMISSION',v_submission,'CONTENT_EDITOR'::public.app_role,'Review marketplace listing: '||left(v_name,120),'A business revision requires Marketplace publication review.','NORMAL',now()+interval '3 days');
  perform private.marketplace_audit(v_actor,'SUBMITTED',p_business_id,v_revision,jsonb_build_object('submission_id',v_submission),p_idempotency_key);
  return jsonb_build_object('submissionId',v_submission,'revisionId',v_revision,'state','SUBMITTED');
end;
$$;

create or replace function public.marketplace_cancel_submission(p_business_id uuid, p_idempotency_key uuid)
returns jsonb language plpgsql security definer set search_path = '' as $$
declare v_actor uuid:=private.marketplace_assert_editor(p_business_id); v_submission public.marketplace_submissions%rowtype;
begin
  select s.* into v_submission from public.marketplace_submissions s where s.business_id=p_business_id and s.state in ('SUBMITTED','ASSIGNED') order by s.created_at desc limit 1 for update;
  if not found then raise exception 'There is no cancellable submission.'; end if;
  update public.marketplace_submissions set state='CANCELLED',updated_at=now() where id=v_submission.id;
  update public.marketplace_business_revisions set state='DRAFT',updated_at=now() where id=v_submission.revision_id;
  perform private.ops_resolve_work_for_source('MARKETPLACE_SUBMISSION',v_submission.id,v_actor);
  perform private.marketplace_audit(v_actor,'SUBMISSION_CANCELLED',p_business_id,v_submission.revision_id,jsonb_build_object('submission_id',v_submission.id),p_idempotency_key);
  return jsonb_build_object('submissionId',v_submission.id,'state','CANCELLED');
end;
$$;

create or replace function public.marketplace_save_business(p_business_id uuid, p_saved boolean)
returns jsonb language plpgsql security definer set search_path = '' as $$
declare v_actor uuid:=private.marketplace_actor();
begin
  if not exists(select 1 from public.marketplace_businesses where id=p_business_id and lifecycle_state='PUBLISHED') then raise exception 'This business is not available.'; end if;
  if p_saved then insert into public.marketplace_favorites(business_id,user_id) values(p_business_id,v_actor) on conflict do nothing; else delete from public.marketplace_favorites where business_id=p_business_id and user_id=v_actor; end if;
  return jsonb_build_object('businessId',p_business_id,'saved',p_saved);
end;
$$;

create or replace function public.marketplace_submit_review(p_business_id uuid, p_rating smallint, p_title text default '', p_body text default '', p_idempotency_key uuid default null)
returns jsonb language plpgsql security definer set search_path = '' as $$
declare v_actor uuid:=private.marketplace_actor(); v_review uuid;
begin
  if p_rating not between 1 and 5 then raise exception 'Select a rating from one to five.'; end if;
  if not exists(select 1 from public.marketplace_businesses where id=p_business_id and lifecycle_state='PUBLISHED') then raise exception 'This business is not available for review.'; end if;
  if private.marketplace_is_member(p_business_id,v_actor) then raise exception 'Business members cannot review their own business.'; end if;
  select id into v_review from public.marketplace_reviews where business_id=p_business_id and author_id=v_actor for update;
  if v_review is null then
    insert into public.marketplace_reviews(business_id,author_id,rating,title,body) values(p_business_id,v_actor,p_rating,left(trim(coalesce(p_title,'')),120),left(trim(coalesce(p_body,'')),4000)) returning id into v_review;
  else
    insert into public.marketplace_review_versions(review_id,rating,title,body,changed_by,reason) select id,rating,title,body,v_actor,'Resident edit' from public.marketplace_reviews where id=v_review;
    update public.marketplace_reviews set rating=p_rating,title=left(trim(coalesce(p_title,'')),120),body=left(trim(coalesce(p_body,'')),4000),state='PUBLISHED',updated_at=now(),deleted_at=null where id=v_review;
  end if;
  perform private.marketplace_audit(v_actor,'REVIEW_SAVED',p_business_id,null,jsonb_build_object('review_id',v_review),p_idempotency_key);
  return jsonb_build_object('reviewId',v_review,'state','PUBLISHED');
end;
$$;

create or replace function public.marketplace_delete_review(p_review_id uuid, p_idempotency_key uuid default null)
returns jsonb language plpgsql security definer set search_path = '' as $$
declare v_actor uuid:=private.marketplace_actor(); v_business uuid;
begin
  select business_id into v_business from public.marketplace_reviews where id=p_review_id and author_id=v_actor for update;
  if v_business is null then raise exception 'Only the review author can delete this review.'; end if;
  delete from public.marketplace_reviews where id=p_review_id;
  perform private.marketplace_audit(v_actor,'REVIEW_DELETED',v_business,null,jsonb_build_object('review_id',p_review_id),p_idempotency_key);
  return jsonb_build_object('reviewId',p_review_id,'deleted',true);
end;
$$;

create or replace function public.marketplace_submit_owner_response(p_review_id uuid, p_body text, p_idempotency_key uuid default null)
returns jsonb language plpgsql security definer set search_path = '' as $$
declare v_actor uuid:=private.marketplace_actor(); v_business uuid; v_response uuid;
begin
  select business_id into v_business from public.marketplace_reviews where id=p_review_id and state='PUBLISHED';
  if v_business is null or not private.marketplace_is_editor(v_business,v_actor) then raise exception 'You cannot respond to this review.'; end if;
  insert into public.marketplace_review_responses(review_id,business_id,author_id,body) values(p_review_id,v_business,v_actor,left(trim(p_body),3000))
  on conflict(review_id) do update set body=excluded.body,author_id=excluded.author_id,state='PUBLISHED',updated_at=now() returning id into v_response;
  perform private.marketplace_audit(v_actor,'OWNER_RESPONSE_SAVED',v_business,null,jsonb_build_object('response_id',v_response),p_idempotency_key);
  return jsonb_build_object('responseId',v_response,'state','PUBLISHED');
end;
$$;

create or replace function public.marketplace_report_review(p_review_id uuid default null, p_response_id uuid default null, p_reason_code text default 'OTHER', p_details text default '')
returns jsonb language plpgsql security definer set search_path = '' as $$
declare v_actor uuid:=private.marketplace_actor(); v_report uuid; v_business uuid;
begin
  if ((p_review_id is not null)::integer + (p_response_id is not null)::integer) <> 1 then raise exception 'Report one review or one response.'; end if;
  select coalesce(r.business_id,rs.business_id) into v_business from public.marketplace_reviews r full join public.marketplace_review_responses rs on rs.id=p_response_id where r.id=p_review_id or rs.id=p_response_id;
  insert into public.marketplace_review_reports(review_id,response_id,reporter_id,reason_code,details) values(p_review_id,p_response_id,v_actor,p_reason_code,left(trim(coalesce(p_details,'')),2000)) returning id into v_report;
  perform private.ops_upsert_work_item('MARKETPLACE_REVIEW_REPORT',v_report,'MODERATOR'::public.app_role,'Review Marketplace report','Marketplace review or response requires moderation.','NORMAL',now()+interval '24 hours');
  perform private.marketplace_audit(v_actor,'REVIEW_REPORTED',v_business,null,jsonb_build_object('report_id',v_report),'{}'::uuid);
  return jsonb_build_object('reportId',v_report,'state','OPEN');
end;
$$;

create or replace function public.marketplace_vote_review_helpful(p_review_id uuid, p_helpful boolean)
returns jsonb language plpgsql security definer set search_path = '' as $$
declare v_actor uuid:=private.marketplace_actor();
begin
  if p_helpful then insert into public.marketplace_review_helpful_votes(review_id,voter_id) values(p_review_id,v_actor) on conflict do nothing; else delete from public.marketplace_review_helpful_votes where review_id=p_review_id and voter_id=v_actor; end if;
  return jsonb_build_object('reviewId',p_review_id,'helpful',p_helpful);
end;
$$;

create or replace function public.marketplace_invite_member(p_business_id uuid, p_email text, p_role text, p_token_hash text, p_idempotency_key uuid)
returns jsonb language plpgsql security definer set search_path = '' as $$
declare v_actor uuid:=private.marketplace_assert_editor(p_business_id); v_invite uuid;
begin
  if not exists(select 1 from public.marketplace_business_members where business_id=p_business_id and user_id=v_actor and role in ('OWNER','MANAGER') and state='ACTIVE') then raise exception 'Only an owner or manager can invite members.'; end if;
  insert into public.marketplace_business_invitations(business_id,email,role,token_hash,invited_by) values(p_business_id,lower(trim(p_email)),p_role,p_token_hash,v_actor) returning id into v_invite;
  perform private.marketplace_audit(v_actor,'MEMBER_INVITED',p_business_id,null,jsonb_build_object('invitation_id',v_invite),p_idempotency_key);
  return jsonb_build_object('invitationId',v_invite,'state','PENDING');
end;
$$;

create or replace function public.marketplace_accept_invitation(p_token_hash text)
returns jsonb language plpgsql security definer set search_path = '' as $$
declare v_actor uuid:=private.marketplace_actor(); v_invite public.marketplace_business_invitations%rowtype;
begin
  select * into v_invite from public.marketplace_business_invitations where token_hash=p_token_hash and state='PENDING' and expires_at>now() for update;
  if not found then raise exception 'This invitation is not available.'; end if;
  insert into public.marketplace_business_members(business_id,user_id,role,invited_by) values(v_invite.business_id,v_actor,v_invite.role,v_invite.invited_by) on conflict(business_id,user_id) do update set role=excluded.role,state='ACTIVE',updated_at=now();
  update public.marketplace_business_invitations set state='ACCEPTED',accepted_by=v_actor,accepted_at=now() where id=v_invite.id;
  perform private.marketplace_audit(v_actor,'INVITATION_ACCEPTED',v_invite.business_id,null,jsonb_build_object('invitation_id',v_invite.id),null);
  return jsonb_build_object('businessId',v_invite.business_id,'role',v_invite.role);
end;
$$;

create or replace function public.marketplace_revoke_member(p_business_id uuid, p_member_user_id uuid, p_idempotency_key uuid)
returns jsonb language plpgsql security definer set search_path = '' as $$
declare v_actor uuid:=private.marketplace_assert_editor(p_business_id);
begin
  if not exists(select 1 from public.marketplace_business_members where business_id=p_business_id and user_id=v_actor and role='OWNER' and state='ACTIVE') then raise exception 'Only an owner can revoke a member.'; end if;
  update public.marketplace_business_members set state='REVOKED',updated_at=now() where business_id=p_business_id and user_id=p_member_user_id and role<>'OWNER';
  if not found then raise exception 'This member cannot be revoked.'; end if;
  perform private.marketplace_audit(v_actor,'MEMBER_REVOKED',p_business_id,null,jsonb_build_object('member_user_id',p_member_user_id),p_idempotency_key);
  return jsonb_build_object('businessId',p_business_id,'memberUserId',p_member_user_id,'state','REVOKED');
end;
$$;

create or replace function public.marketplace_transfer_ownership(p_business_id uuid, p_new_owner_id uuid, p_idempotency_key uuid)
returns jsonb language plpgsql security definer set search_path = '' as $$
declare v_actor uuid:=private.marketplace_assert_editor(p_business_id);
begin
  if not exists(select 1 from public.marketplace_business_members where business_id=p_business_id and user_id=v_actor and role='OWNER' and state='ACTIVE') then raise exception 'Only the current owner can transfer ownership.'; end if;
  if not private.marketplace_is_member(p_business_id,p_new_owner_id) then raise exception 'The new owner must be an active business member.'; end if;
  update public.marketplace_business_members set role='MANAGER',updated_at=now() where business_id=p_business_id and user_id=v_actor;
  update public.marketplace_business_members set role='OWNER',updated_at=now() where business_id=p_business_id and user_id=p_new_owner_id;
  perform private.marketplace_audit(v_actor,'OWNERSHIP_TRANSFERRED',p_business_id,null,jsonb_build_object('new_owner_id',p_new_owner_id),p_idempotency_key);
  return jsonb_build_object('businessId',p_business_id,'ownerId',p_new_owner_id);
end;
$$;

create or replace function public.marketplace_assign_submission(p_submission_id uuid, p_assignee_id uuid default null)
returns jsonb language plpgsql security definer set search_path = '' as $$
declare v_actor uuid:=private.marketplace_assert_content_admin(); v_submission public.marketplace_submissions%rowtype;
begin
  select * into v_submission from public.marketplace_submissions where id=p_submission_id for update;
  if not found or v_submission.state not in ('SUBMITTED','ASSIGNED') then raise exception 'This submission is not available.'; end if;
  update public.marketplace_submissions set assigned_to=coalesce(p_assignee_id,v_actor),state='ASSIGNED',updated_at=now() where id=p_submission_id;
  perform private.marketplace_audit(v_actor,'SUBMISSION_ASSIGNED',v_submission.business_id,v_submission.revision_id,jsonb_build_object('submission_id',p_submission_id,'assignee_id',coalesce(p_assignee_id,v_actor)),null);
  return jsonb_build_object('submissionId',p_submission_id,'state','ASSIGNED');
end;
$$;

create or replace function public.marketplace_request_changes(p_submission_id uuid, p_feedback text, p_idempotency_key uuid)
returns jsonb language plpgsql security definer set search_path = '' as $$
declare v_actor uuid:=private.marketplace_assert_content_admin(); v_submission public.marketplace_submissions%rowtype;
begin
  if char_length(trim(coalesce(p_feedback,''))) not between 3 and 3000 then raise exception 'Provide change-request feedback.'; end if;
  select * into v_submission from public.marketplace_submissions where id=p_submission_id and state in ('SUBMITTED','ASSIGNED') for update;
  if not found then raise exception 'This submission is not available.'; end if;
  update public.marketplace_submissions set state='CHANGES_REQUESTED',feedback=left(trim(p_feedback),3000),decided_at=now(),decided_by=v_actor,updated_at=now() where id=p_submission_id;
  update public.marketplace_business_revisions set state='CHANGES_REQUESTED',reviewed_at=now(),reviewed_by=v_actor,review_feedback=left(trim(p_feedback),3000),updated_at=now() where id=v_submission.revision_id;
  perform private.ops_resolve_work_for_source('MARKETPLACE_SUBMISSION',p_submission_id,v_actor);
  perform private.marketplace_notification(v_submission.submitted_by,'Marketplace changes requested','Your business submission needs changes before publication.','account/marketplace/business/'||v_submission.business_id::text||'/status',v_submission.business_id);
  perform private.marketplace_audit(v_actor,'CHANGES_REQUESTED',v_submission.business_id,v_submission.revision_id,jsonb_build_object('submission_id',p_submission_id),p_idempotency_key);
  return jsonb_build_object('submissionId',p_submission_id,'state','CHANGES_REQUESTED');
end;
$$;

create or replace function public.marketplace_approve_and_publish(p_submission_id uuid, p_idempotency_key uuid)
returns jsonb language plpgsql security definer set search_path = '' as $$
declare v_actor uuid:=private.marketplace_assert_content_admin(); v_submission public.marketplace_submissions%rowtype;
begin
  select * into v_submission from public.marketplace_submissions where id=p_submission_id and state in ('SUBMITTED','ASSIGNED') for update;
  if not found then raise exception 'This submission is not available for publication.'; end if;
  if not exists(select 1 from public.marketplace_business_locations where revision_id=v_submission.revision_id) then raise exception 'A published business needs a location.'; end if;
  if exists(select 1 from public.marketplace_media_assets where revision_id=v_submission.revision_id and state='STAGED') then raise exception 'Finalize or remove staged media before publication.'; end if;
  update public.marketplace_business_revisions set state='ARCHIVED',updated_at=now() where business_id=v_submission.business_id and state='PUBLISHED';
  update public.marketplace_business_revisions set state='PUBLISHED',reviewed_at=now(),reviewed_by=v_actor,review_feedback=null,updated_at=now() where id=v_submission.revision_id;
  update public.marketplace_businesses set lifecycle_state='PUBLISHED',current_public_revision_id=v_submission.revision_id,published_at=coalesce(published_at,now()),suspended_at=null,updated_at=now() where id=v_submission.business_id;
  update public.marketplace_submissions set state='APPROVED',decided_at=now(),decided_by=v_actor,updated_at=now() where id=p_submission_id;
  perform private.ops_resolve_work_for_source('MARKETPLACE_SUBMISSION',p_submission_id,v_actor);
  perform private.marketplace_notification(v_submission.submitted_by,'Marketplace business published','Your business is now published in the RTC Community Marketplace.','community/marketplace/business/'||v_submission.business_id::text,v_submission.business_id);
  perform private.marketplace_audit(v_actor,'PUBLISHED',v_submission.business_id,v_submission.revision_id,jsonb_build_object('submission_id',p_submission_id),p_idempotency_key);
  return jsonb_build_object('submissionId',p_submission_id,'businessId',v_submission.business_id,'revisionId',v_submission.revision_id,'state','PUBLISHED');
end;
$$;

create or replace function public.marketplace_reject_submission(p_submission_id uuid, p_feedback text, p_idempotency_key uuid)
returns jsonb language plpgsql security definer set search_path = '' as $$
declare v_actor uuid:=private.marketplace_assert_content_admin(); v_submission public.marketplace_submissions%rowtype;
begin
  if char_length(trim(coalesce(p_feedback,''))) not between 3 and 3000 then raise exception 'Provide rejection feedback.'; end if;
  select * into v_submission from public.marketplace_submissions where id=p_submission_id and state in ('SUBMITTED','ASSIGNED') for update;
  if not found then raise exception 'This submission is not available.'; end if;
  update public.marketplace_submissions set state='REJECTED',feedback=left(trim(p_feedback),3000),decided_at=now(),decided_by=v_actor,updated_at=now() where id=p_submission_id;
  update public.marketplace_business_revisions set state='REJECTED',reviewed_at=now(),reviewed_by=v_actor,review_feedback=left(trim(p_feedback),3000),updated_at=now() where id=v_submission.revision_id;
  perform private.ops_resolve_work_for_source('MARKETPLACE_SUBMISSION',p_submission_id,v_actor);
  perform private.marketplace_notification(v_submission.submitted_by,'Marketplace submission not approved','Your submission was not approved. Review the feedback in My businesses.','account/marketplace/business/'||v_submission.business_id::text||'/status',v_submission.business_id);
  perform private.marketplace_audit(v_actor,'SUBMISSION_REJECTED',v_submission.business_id,v_submission.revision_id,jsonb_build_object('submission_id',p_submission_id),p_idempotency_key);
  return jsonb_build_object('submissionId',p_submission_id,'state','REJECTED');
end;
$$;

create or replace function public.marketplace_suspend_business(p_business_id uuid, p_reason text, p_idempotency_key uuid)
returns jsonb language plpgsql security definer set search_path = '' as $$
declare v_actor uuid:=private.marketplace_assert_moderator();
begin
  if char_length(trim(coalesce(p_reason,''))) not between 3 and 2000 then raise exception 'Provide a suspension reason.'; end if;
  update public.marketplace_businesses set lifecycle_state='SUSPENDED',suspended_at=now(),updated_at=now() where id=p_business_id and lifecycle_state='PUBLISHED';
  if not found then raise exception 'Only a published business can be suspended.'; end if;
  perform private.marketplace_audit(v_actor,'SUSPENDED',p_business_id,null,jsonb_build_object('reason',left(trim(p_reason),2000)),p_idempotency_key);
  return jsonb_build_object('businessId',p_business_id,'state','SUSPENDED');
end;
$$;

create or replace function public.marketplace_reinstate_business(p_business_id uuid, p_idempotency_key uuid)
returns jsonb language plpgsql security definer set search_path = '' as $$
declare v_actor uuid:=private.marketplace_assert_moderator();
begin
  update public.marketplace_businesses set lifecycle_state='PUBLISHED',suspended_at=null,updated_at=now() where id=p_business_id and lifecycle_state='SUSPENDED' and current_public_revision_id is not null;
  if not found then raise exception 'This business cannot be reinstated.'; end if;
  perform private.marketplace_audit(v_actor,'REINSTATED',p_business_id,null,'{}',p_idempotency_key);
  return jsonb_build_object('businessId',p_business_id,'state','PUBLISHED');
end;
$$;

create or replace function public.marketplace_archive_business(p_business_id uuid, p_idempotency_key uuid)
returns jsonb language plpgsql security definer set search_path = '' as $$
declare v_actor uuid:=private.marketplace_assert_editor(p_business_id);
begin
  update public.marketplace_businesses set lifecycle_state='ARCHIVED',archived_at=now(),updated_at=now() where id=p_business_id and lifecycle_state not in ('ARCHIVED','SUSPENDED');
  if not found then raise exception 'This business cannot be archived.'; end if;
  perform private.marketplace_audit(v_actor,'ARCHIVED',p_business_id,null,'{}',p_idempotency_key);
  return jsonb_build_object('businessId',p_business_id,'state','ARCHIVED');
end;
$$;

create or replace function public.marketplace_schedule_featured(p_business_id uuid, p_starts_at timestamptz, p_ends_at timestamptz, p_priority integer, p_idempotency_key uuid)
returns jsonb language plpgsql security definer set search_path = '' as $$
declare v_actor uuid:=private.marketplace_assert_content_admin(); v_id uuid;
begin
  if p_ends_at<=p_starts_at then raise exception 'Featured placement end must follow its start.'; end if;
  insert into public.marketplace_featured_placements(business_id,starts_at,ends_at,priority,state,created_by) values(p_business_id,p_starts_at,p_ends_at,p_priority,case when p_starts_at<=now() then 'ACTIVE' else 'SCHEDULED' end,v_actor) returning id into v_id;
  perform private.marketplace_audit(v_actor,'FEATURED_SCHEDULED',p_business_id,null,jsonb_build_object('placement_id',v_id),p_idempotency_key);
  return jsonb_build_object('placementId',v_id,'state',case when p_starts_at<=now() then 'ACTIVE' else 'SCHEDULED' end);
end;
$$;

create or replace function public.marketplace_cancel_featured(p_placement_id uuid, p_idempotency_key uuid)
returns jsonb language plpgsql security definer set search_path = '' as $$
declare v_actor uuid:=private.marketplace_assert_content_admin(); v_business uuid;
begin
  update public.marketplace_featured_placements set state='CANCELLED',cancelled_by=v_actor,cancelled_at=now() where id=p_placement_id and state in ('SCHEDULED','ACTIVE') returning business_id into v_business;
  if v_business is null then raise exception 'This featured placement cannot be cancelled.'; end if;
  perform private.marketplace_audit(v_actor,'FEATURED_CANCELLED',v_business,null,jsonb_build_object('placement_id',p_placement_id),p_idempotency_key);
  return jsonb_build_object('placementId',p_placement_id,'state','CANCELLED');
end;
$$;

create or replace function private.marketplace_card(p_business_id uuid, p_origin extensions.geography default null)
returns jsonb language sql stable security definer set search_path = '' as $$
  select jsonb_build_object(
    'id',b.id,'slug',b.slug,'displayName',r.display_name,'tagline',r.tagline,
    'category',coalesce((select c.name from public.marketplace_revision_categories rc join public.marketplace_categories c on c.id=rc.category_id where rc.revision_id=r.id and rc.is_primary limit 1),''),
    'ratingAverage',coalesce(s.rating_average,0),'reviewCount',coalesce(s.review_count,0),'weightedScore',coalesce(s.weighted_score,0),
    'locality',(select l.locality from public.marketplace_business_locations l where l.revision_id=r.id order by l.is_primary desc,l.created_at limit 1),
    'distanceMetres',case when p_origin is null then null else (select round(min(extensions.ST_Distance(l.public_point,p_origin)))::integer from public.marketplace_business_locations l where l.revision_id=r.id and l.public_point is not null) end,
    'logoPath',(select a.object_path from public.marketplace_media_assets a where a.revision_id=r.id and a.asset_type='LOGO' and a.state='FINALIZED' order by a.display_order,a.created_at limit 1),
    'verified',exists(select 1 from public.marketplace_business_verifications v where v.business_id=b.id and v.state='VERIFIED' and (v.expires_at is null or v.expires_at>now())),
    'featured',exists(select 1 from public.marketplace_featured_placements fp where fp.business_id=b.id and fp.state in ('SCHEDULED','ACTIVE') and fp.starts_at<=now() and fp.ends_at>now())
  ) from public.marketplace_businesses b join public.marketplace_business_revisions r on r.id=b.current_public_revision_id and r.state='PUBLISHED'
  left join public.marketplace_business_rating_stats s on s.business_id=b.id
  where b.id=p_business_id and b.lifecycle_state='PUBLISHED';
$$;

create or replace function public.marketplace_home(p_locality text default null, p_lat double precision default null, p_lon double precision default null)
returns jsonb language plpgsql security definer set search_path = '' as $$
declare v_actor uuid:=private.marketplace_actor(); v_origin extensions.geography;
begin
  if p_lat is not null and (p_lat not between -90 and 90 or p_lon not between -180 and 180) then raise exception 'Use valid coordinates.'; end if;
  if p_lat is not null then v_origin:=extensions.ST_SetSRID(extensions.ST_MakePoint(p_lon,p_lat),4326)::extensions.geography; end if;
  return jsonb_build_object(
    'featured',coalesce((select jsonb_agg(private.marketplace_card(q.id,v_origin)) from (select b.id from public.marketplace_businesses b join public.marketplace_featured_placements f on f.business_id=b.id where b.lifecycle_state='PUBLISHED' and f.state in ('SCHEDULED','ACTIVE') and f.starts_at<=now() and f.ends_at>now() order by f.priority desc,f.starts_at desc limit 8) q),'[]'::jsonb),
    'nearby',coalesce((select jsonb_agg(private.marketplace_card(q.id,v_origin)) from (select distinct b.id from public.marketplace_businesses b join public.marketplace_business_revisions r on r.id=b.current_public_revision_id join public.marketplace_business_locations l on l.revision_id=r.id where b.lifecycle_state='PUBLISHED' and l.public_point is not null and (v_origin is null or extensions.ST_DWithin(l.public_point,v_origin,25000)) and (p_locality is null or l.locality ilike p_locality) order by min(extensions.ST_Distance(l.public_point,coalesce(v_origin,l.public_point))) limit 20) q),'[]'::jsonb),
    'categories',coalesce((select jsonb_agg(jsonb_build_object('id',id,'name',name,'slug',slug,'iconKey',icon_key)) from public.marketplace_categories where is_active and parent_id is null order by display_order,name),'[]'::jsonb),
    'new',coalesce((select jsonb_agg(private.marketplace_card(q.id,v_origin)) from (select id from public.marketplace_businesses where lifecycle_state='PUBLISHED' order by published_at desc nulls last,id desc limit 12) q),'[]'::jsonb),
    'topRated',coalesce((select jsonb_agg(private.marketplace_card(q.id,v_origin)) from (select b.id from public.marketplace_businesses b join public.marketplace_business_rating_stats s on s.business_id=b.id where b.lifecycle_state='PUBLISHED' order by s.weighted_score desc,s.review_count desc,b.id limit 12) q),'[]'::jsonb)
  );
end;
$$;

create or replace function public.marketplace_nearby_businesses(p_lat double precision, p_lon double precision, p_radius_metres integer default 10000, p_limit integer default 20)
returns jsonb language plpgsql security definer set search_path = '' as $$
declare v_origin extensions.geography; v_radius integer:=greatest(100,least(coalesce(p_radius_metres,10000),50000)); v_limit integer:=greatest(1,least(coalesce(p_limit,20),50));
begin
  perform private.marketplace_actor();
  if p_lat not between -90 and 90 or p_lon not between -180 and 180 then raise exception 'Use valid coordinates.'; end if;
  v_origin:=extensions.ST_SetSRID(extensions.ST_MakePoint(p_lon,p_lat),4326)::extensions.geography;
  return coalesce((select jsonb_agg(private.marketplace_card(q.id,v_origin)) from (select distinct on (b.id) b.id from public.marketplace_businesses b join public.marketplace_business_revisions r on r.id=b.current_public_revision_id join public.marketplace_business_locations l on l.revision_id=r.id where b.lifecycle_state='PUBLISHED' and l.public_point is not null and extensions.ST_DWithin(l.public_point,v_origin,v_radius) order by b.id,extensions.ST_Distance(l.public_point,v_origin) limit v_limit) q),'[]'::jsonb);
end;
$$;

create or replace function public.marketplace_search_businesses(p_query text default null, p_category_id uuid default null, p_locality text default null, p_lat double precision default null, p_lon double precision default null, p_radius_metres integer default null, p_min_rating numeric default null, p_verified_only boolean default false, p_sort text default 'RECOMMENDED', p_limit integer default 20)
returns jsonb language plpgsql security definer set search_path = '' as $$
declare v_origin extensions.geography; v_limit integer:=greatest(1,least(coalesce(p_limit,20),50)); v_radius integer:=greatest(100,least(coalesce(p_radius_metres,50000),50000));
begin
  perform private.marketplace_actor();
  if p_lat is not null then if p_lat not between -90 and 90 or p_lon not between -180 and 180 then raise exception 'Use valid coordinates.'; end if; v_origin:=extensions.ST_SetSRID(extensions.ST_MakePoint(p_lon,p_lat),4326)::extensions.geography; end if;
  return coalesce((select jsonb_agg(private.marketplace_card(q.id,v_origin)) from (
    select b.id,coalesce(s.weighted_score,0) weighted,coalesce(s.rating_average,0) average,b.published_at,
      (select min(extensions.ST_Distance(l.public_point,v_origin)) from public.marketplace_business_locations l where l.revision_id=r.id and l.public_point is not null) distance
    from public.marketplace_businesses b join public.marketplace_business_revisions r on r.id=b.current_public_revision_id and r.state='PUBLISHED'
    left join public.marketplace_business_rating_stats s on s.business_id=b.id
    where b.lifecycle_state='PUBLISHED'
      and (nullif(trim(coalesce(p_query,'')),'') is null or r.display_name % p_query or r.tagline % p_query or r.description % p_query)
      and (p_category_id is null or exists(select 1 from public.marketplace_revision_categories rc where rc.revision_id=r.id and rc.category_id=p_category_id))
      and (p_locality is null or exists(select 1 from public.marketplace_business_locations l where l.revision_id=r.id and l.locality ilike p_locality))
      and (v_origin is null or exists(select 1 from public.marketplace_business_locations l where l.revision_id=r.id and l.public_point is not null and extensions.ST_DWithin(l.public_point,v_origin,v_radius)))
      and (p_min_rating is null or coalesce(s.rating_average,0)>=p_min_rating)
      and (not p_verified_only or exists(select 1 from public.marketplace_business_verifications v where v.business_id=b.id and v.state='VERIFIED' and (v.expires_at is null or v.expires_at>now())))
    order by case when upper(p_sort)='DISTANCE' then distance end asc nulls last,
      case when upper(p_sort)='TOP_RATED' then coalesce(s.weighted_score,0) end desc,
      case when upper(p_sort)='NEWEST' then b.published_at end desc,
      case when upper(p_sort)='NAME' then lower(r.display_name) end asc,
      coalesce(s.weighted_score,0) desc,b.updated_at desc,b.id
    limit v_limit
  ) q),'[]'::jsonb);
end;
$$;

create or replace function public.marketplace_businesses_in_view(p_west double precision, p_south double precision, p_east double precision, p_north double precision, p_limit integer default 200)
returns jsonb language plpgsql security definer set search_path = '' as $$
declare v_limit integer:=greatest(1,least(coalesce(p_limit,200),300));
begin
  perform private.marketplace_actor();
  if p_west not between -180 and 180 or p_east not between -180 and 180 or p_south not between -90 and 90 or p_north not between -90 and 90 or p_south>=p_north then raise exception 'Use a valid map viewport.'; end if;
  return coalesce((select jsonb_agg(jsonb_build_object('id',b.id,'slug',b.slug,'name',r.display_name,'latitude',extensions.ST_Y(l.public_point::extensions.geometry),'longitude',extensions.ST_X(l.public_point::extensions.geometry),'category',(select c.name from public.marketplace_revision_categories rc join public.marketplace_categories c on c.id=rc.category_id where rc.revision_id=r.id and rc.is_primary limit 1))) from public.marketplace_businesses b join public.marketplace_business_revisions r on r.id=b.current_public_revision_id join lateral (select l.* from public.marketplace_business_locations l where l.revision_id=r.id and l.public_point is not null and extensions.ST_Intersects(l.public_point::extensions.geometry,extensions.ST_MakeEnvelope(p_west,p_south,p_east,p_north,4326)) order by l.is_primary desc limit 1) l on true where b.lifecycle_state='PUBLISHED' limit v_limit),'[]'::jsonb);
end;
$$;

create or replace function public.marketplace_business_detail(p_business_id_or_slug text, p_lat double precision default null, p_lon double precision default null)
returns jsonb language plpgsql security definer set search_path = '' as $$
declare v_actor uuid:=private.marketplace_actor(); v_business public.marketplace_businesses%rowtype; v_revision public.marketplace_business_revisions%rowtype; v_origin extensions.geography;
begin
  if p_lat is not null then if p_lat not between -90 and 90 or p_lon not between -180 and 180 then raise exception 'Use valid coordinates.'; end if; v_origin:=extensions.ST_SetSRID(extensions.ST_MakePoint(p_lon,p_lat),4326)::extensions.geography; end if;
  select b.* into v_business from public.marketplace_businesses b where (b.id::text=p_business_id_or_slug or b.slug=p_business_id_or_slug) and b.lifecycle_state='PUBLISHED';
  if not found then raise exception 'This business is not available.'; end if;
  select * into v_revision from public.marketplace_business_revisions where id=v_business.current_public_revision_id and state='PUBLISHED';
  return jsonb_build_object('business',private.marketplace_card(v_business.id,v_origin),'description',v_revision.description,'phone',v_revision.public_phone,'whatsappEnabled',v_revision.whatsapp_enabled,'email',v_revision.public_email,'websiteUrl',v_revision.website_url,'socialLinks',v_revision.social_links,'locations',coalesce((select jsonb_agg(jsonb_build_object('id',l.id,'label',l.label,'locality',l.locality,'municipality',l.municipality,'province',l.province,'address',case when l.address_visibility='EXACT' then l.address_line1 else null end,'visibility',l.address_visibility,'latitude',case when l.public_point is null then null else extensions.ST_Y(l.public_point::extensions.geometry) end,'longitude',case when l.public_point is null then null else extensions.ST_X(l.public_point::extensions.geometry) end,'timezone',l.timezone,'hours',coalesce((select jsonb_agg(jsonb_build_object('dayOfWeek',h.day_of_week,'intervalOrder',h.interval_order,'state',h.state,'opensAt',h.opens_at,'closesAt',h.closes_at) order by h.day_of_week,h.interval_order) from public.marketplace_location_hours h where h.location_id=l.id),'[]'::jsonb),'accessibilityFeatures',l.accessibility_features,'parkingNote',l.parking_note) order by l.is_primary desc,l.created_at) from public.marketplace_business_locations l where l.revision_id=v_revision.id),'[]'::jsonb),'offerings',coalesce((select jsonb_agg(jsonb_build_object('id',o.id,'type',o.offering_type,'title',o.title,'description',o.description,'priceType',o.price_type,'currencyCode',o.currency_code,'priceMin',o.price_min,'priceMax',o.price_max,'durationMinutes',o.duration_minutes,'availabilityNote',o.availability_note) order by o.display_order,o.created_at) from public.marketplace_offerings o where o.revision_id=v_revision.id),'[]'::jsonb),'media',coalesce((select jsonb_agg(jsonb_build_object('id',a.id,'type',a.asset_type,'path',a.object_path,'altText',a.alt_text,'focalPoint',a.focal_point,'displayOrder',a.display_order) order by a.asset_type,a.display_order,a.created_at) from public.marketplace_media_assets a where a.revision_id=v_revision.id and a.state='FINALIZED'),'[]'::jsonb),'rating',coalesce((select jsonb_build_object('average',s.rating_average,'count',s.review_count,'weightedScore',s.weighted_score,'distribution',s.rating_distribution) from public.marketplace_business_rating_stats s where s.business_id=v_business.id),'{}'::jsonb),'saved',exists(select 1 from public.marketplace_favorites f where f.business_id=v_business.id and f.user_id=v_actor));
end;
$$;

create or replace function public.marketplace_business_reviews(p_business_id uuid, p_limit integer default 30)
returns jsonb language plpgsql security definer set search_path = '' as $$
declare v_actor uuid:=private.marketplace_actor(); v_limit integer:=greatest(1,least(coalesce(p_limit,30),100));
begin
  return jsonb_build_object('items',coalesce((select jsonb_agg(jsonb_build_object('id',r.id,'rating',r.rating,'title',r.title,'body',r.body,'createdAt',r.created_at,'updatedAt',r.updated_at,'isMine',r.author_id=v_actor,'helpfulCount',(select count(*) from public.marketplace_review_helpful_votes h where h.review_id=r.id),'response',(select jsonb_build_object('id',rs.id,'body',rs.body,'createdAt',rs.created_at) from public.marketplace_review_responses rs where rs.review_id=r.id and rs.state='PUBLISHED')) order by r.created_at desc) from (select * from public.marketplace_reviews where business_id=p_business_id and state='PUBLISHED' order by created_at desc limit v_limit) r),'[]'::jsonb),'rating',coalesce((select jsonb_build_object('average',rating_average,'count',review_count,'distribution',rating_distribution) from public.marketplace_business_rating_stats where business_id=p_business_id),'{}'::jsonb));
end;
$$;

create or replace function public.marketplace_my_businesses()
returns jsonb language plpgsql security definer set search_path = '' as $$
declare v_actor uuid:=private.marketplace_actor();
begin
 return coalesce((select jsonb_agg(jsonb_build_object('id',b.id,'slug',b.slug,'lifecycleState',b.lifecycle_state,'currentRevisionId',r.id,'displayName',r.display_name,'revisionState',r.state,'updatedAt',r.updated_at,'role',m.role,'submission',(select jsonb_build_object('id',s.id,'state',s.state,'feedback',s.feedback,'updatedAt',s.updated_at) from public.marketplace_submissions s where s.revision_id=r.id order by s.created_at desc limit 1)) order by b.updated_at desc) from public.marketplace_business_members m join public.marketplace_businesses b on b.id=m.business_id join lateral (select * from public.marketplace_business_revisions r where r.business_id=b.id and (r.state in ('DRAFT','CHANGES_REQUESTED','SUBMITTED') or r.id=b.current_public_revision_id) order by r.revision_number desc limit 1) r on true where m.user_id=v_actor and m.state='ACTIVE'),'[]'::jsonb);
end;
$$;

create or replace function public.marketplace_business_editor(p_business_id uuid)
returns jsonb language plpgsql security definer set search_path = '' as $$
declare v_actor uuid:=private.marketplace_assert_editor(p_business_id); v_revision uuid;
begin
  v_revision:=private.marketplace_current_editable_revision(p_business_id,v_actor);
  return jsonb_build_object('businessId',p_business_id,'revision',to_jsonb(r),'categories',coalesce((select jsonb_agg(jsonb_build_object('id',category_id,'primary',is_primary)) from public.marketplace_revision_categories where revision_id=v_revision),'[]'::jsonb),'locations',coalesce((select jsonb_agg(to_jsonb(l)) from public.marketplace_business_locations l where l.revision_id=v_revision),'[]'::jsonb),'offerings',coalesce((select jsonb_agg(to_jsonb(o)) from public.marketplace_offerings o where o.revision_id=v_revision),'[]'::jsonb),'media',coalesce((select jsonb_agg(to_jsonb(a)) from public.marketplace_media_assets a where a.revision_id=v_revision and a.state<>'DELETED'),'[]'::jsonb)) from public.marketplace_business_revisions r where r.id=v_revision;
end;
$$;

create or replace function public.marketplace_submission_status(p_business_id uuid)
returns jsonb language plpgsql security definer set search_path = '' as $$
declare v_actor uuid:=private.marketplace_assert_editor(p_business_id);
begin
  return jsonb_build_object('business',to_jsonb(b),'revisions',coalesce((select jsonb_agg(jsonb_build_object('id',r.id,'number',r.revision_number,'state',r.state,'feedback',r.review_feedback,'submittedAt',r.submitted_at,'reviewedAt',r.reviewed_at) order by r.revision_number desc) from public.marketplace_business_revisions r where r.business_id=p_business_id),'[]'::jsonb),'submissions',coalesce((select jsonb_agg(jsonb_build_object('id',s.id,'revisionId',s.revision_id,'state',s.state,'feedback',s.feedback,'createdAt',s.created_at,'updatedAt',s.updated_at) order by s.created_at desc) from public.marketplace_submissions s where s.business_id=p_business_id),'[]'::jsonb)) from public.marketplace_businesses b where b.id=p_business_id;
end;
$$;

create or replace function public.marketplace_my_reviews()
returns jsonb language plpgsql security definer set search_path = '' as $$
declare v_actor uuid:=private.marketplace_actor();
begin
  return coalesce((select jsonb_agg(jsonb_build_object('id',r.id,'businessId',r.business_id,'businessName',br.display_name,'rating',r.rating,'title',r.title,'body',r.body,'state',r.state,'updatedAt',r.updated_at) order by r.updated_at desc) from public.marketplace_reviews r join public.marketplace_businesses b on b.id=r.business_id left join public.marketplace_business_revisions br on br.id=b.current_public_revision_id where r.author_id=v_actor),'[]'::jsonb);
end;
$$;

create or replace function public.marketplace_admin_queue(p_limit integer default 50)
returns jsonb language plpgsql security definer set search_path = '' as $$
declare v_actor uuid:=private.marketplace_assert_content_admin(); v_limit integer:=greatest(1,least(coalesce(p_limit,50),100));
begin
  return jsonb_build_object('submissions',coalesce((select jsonb_agg(jsonb_build_object('id',s.id,'businessId',s.business_id,'revisionId',s.revision_id,'displayName',r.display_name,'state',s.state,'assignedTo',s.assigned_to,'createdAt',s.created_at) order by s.created_at) from (select * from public.marketplace_submissions where state in ('SUBMITTED','ASSIGNED') order by created_at limit v_limit) s join public.marketplace_business_revisions r on r.id=s.revision_id),'[]'::jsonb),'metrics',jsonb_build_object('pendingListings',(select count(*) from public.marketplace_submissions where state in ('SUBMITTED','ASSIGNED')),'changesRequested',(select count(*) from public.marketplace_submissions where state='CHANGES_REQUESTED'),'publishedBusinesses',(select count(*) from public.marketplace_businesses where lifecycle_state='PUBLISHED'),'activeLocations',(select count(*) from public.marketplace_business_locations l join public.marketplace_businesses b on b.current_public_revision_id=l.revision_id where b.lifecycle_state='PUBLISHED'),'flaggedReviews',(select count(*) from public.marketplace_review_reports where state in ('OPEN','UNDER_REVIEW')),'suspendedListings',(select count(*) from public.marketplace_businesses where lifecycle_state='SUSPENDED')));
end;
$$;

create or replace function public.marketplace_admin_submission_detail(p_submission_id uuid)
returns jsonb language plpgsql security definer set search_path = '' as $$
declare v_actor uuid:=private.marketplace_assert_content_admin(); v_submission public.marketplace_submissions%rowtype;
begin
  select * into v_submission from public.marketplace_submissions where id=p_submission_id;
  if not found then raise exception 'Submission is not available.'; end if;
  return jsonb_build_object('submission',to_jsonb(v_submission),'submittedRevision',(select to_jsonb(r) from public.marketplace_business_revisions r where r.id=v_submission.revision_id),'publishedRevision',(select to_jsonb(r) from public.marketplace_businesses b join public.marketplace_business_revisions r on r.id=b.current_public_revision_id where b.id=v_submission.business_id),'locations',coalesce((select jsonb_agg(to_jsonb(l)) from public.marketplace_business_locations l where l.revision_id=v_submission.revision_id),'[]'::jsonb),'offerings',coalesce((select jsonb_agg(to_jsonb(o)) from public.marketplace_offerings o where o.revision_id=v_submission.revision_id),'[]'::jsonb),'media',coalesce((select jsonb_agg(to_jsonb(a)) from public.marketplace_media_assets a where a.revision_id=v_submission.revision_id),'[]'::jsonb),'verification',coalesce((select jsonb_agg(to_jsonb(v)) from public.marketplace_business_verifications v where v.revision_id=v_submission.revision_id),'[]'::jsonb),'history',coalesce((select jsonb_agg(to_jsonb(a) order by a.created_at desc) from public.marketplace_audit_events a where a.business_id=v_submission.business_id),'[]'::jsonb));
end;
$$;

create or replace function public.marketplace_admin_moderate_review(p_report_id uuid, p_action text, p_note text default '')
returns jsonb language plpgsql security definer set search_path = '' as $$
declare v_actor uuid:=private.marketplace_assert_moderator(); v_report public.marketplace_review_reports%rowtype; v_state text:=upper(trim(p_action));
begin
  select * into v_report from public.marketplace_review_reports where id=p_report_id and state in ('OPEN','UNDER_REVIEW') for update;
  if not found then raise exception 'Review report is not available.'; end if;
  if v_state not in ('HIDE','REMOVE','RESTORE','CLOSE','ESCALATE') then raise exception 'Select a valid moderation action.'; end if;
  if v_report.review_id is not null and v_state in ('HIDE','REMOVE','RESTORE') then update public.marketplace_reviews set state=case v_state when 'HIDE' then 'HIDDEN' when 'REMOVE' then 'REMOVED' else 'PUBLISHED' end,updated_at=now() where id=v_report.review_id; end if;
  if v_report.response_id is not null and v_state in ('HIDE','REMOVE','RESTORE') then update public.marketplace_review_responses set state=case v_state when 'HIDE' then 'HIDDEN' when 'REMOVE' then 'REMOVED' else 'PUBLISHED' end,updated_at=now() where id=v_report.response_id; end if;
  update public.marketplace_review_reports set state=case when v_state='ESCALATE' then 'ESCALATED' else 'CLOSED' end,resolved_at=now(),resolved_by=v_actor where id=p_report_id;
  perform private.ops_resolve_work_for_source('MARKETPLACE_REVIEW_REPORT',p_report_id,v_actor);
  return jsonb_build_object('reportId',p_report_id,'action',v_state);
end;
$$;

grant execute on function public.marketplace_create_business_draft(text,uuid), public.marketplace_save_identity(uuid,jsonb,uuid), public.marketplace_upsert_location(uuid,uuid,jsonb,uuid), public.marketplace_replace_location_hours(uuid,uuid,jsonb,jsonb,uuid), public.marketplace_upsert_offering(uuid,uuid,jsonb,uuid), public.marketplace_begin_media_upload(uuid,text,text,bigint,text,uuid), public.marketplace_finalize_media_upload(uuid,uuid,integer,integer,jsonb,uuid), public.marketplace_delete_draft_media(uuid,uuid,uuid), public.marketplace_submit_business(uuid,uuid), public.marketplace_cancel_submission(uuid,uuid), public.marketplace_save_business(uuid,boolean), public.marketplace_submit_review(uuid,smallint,text,text,uuid), public.marketplace_delete_review(uuid,uuid), public.marketplace_submit_owner_response(uuid,text,uuid), public.marketplace_report_review(uuid,uuid,text,text), public.marketplace_vote_review_helpful(uuid,boolean), public.marketplace_invite_member(uuid,text,text,text,uuid), public.marketplace_accept_invitation(text), public.marketplace_revoke_member(uuid,uuid,uuid), public.marketplace_transfer_ownership(uuid,uuid,uuid), public.marketplace_assign_submission(uuid,uuid), public.marketplace_request_changes(uuid,text,uuid), public.marketplace_approve_and_publish(uuid,uuid), public.marketplace_reject_submission(uuid,text,uuid), public.marketplace_suspend_business(uuid,text,uuid), public.marketplace_reinstate_business(uuid,uuid), public.marketplace_archive_business(uuid,uuid), public.marketplace_schedule_featured(uuid,timestamptz,timestamptz,integer,uuid), public.marketplace_cancel_featured(uuid,uuid), public.marketplace_home(text,double precision,double precision), public.marketplace_nearby_businesses(double precision,double precision,integer,integer), public.marketplace_search_businesses(text,uuid,text,double precision,double precision,integer,numeric,boolean,text,integer), public.marketplace_businesses_in_view(double precision,double precision,double precision,double precision,integer), public.marketplace_business_detail(text,double precision,double precision), public.marketplace_business_reviews(uuid,integer), public.marketplace_my_businesses(), public.marketplace_business_editor(uuid), public.marketplace_submission_status(uuid), public.marketplace_my_reviews(), public.marketplace_admin_queue(integer), public.marketplace_admin_submission_detail(uuid), public.marketplace_admin_moderate_review(uuid,text,text) to authenticated;

commit;
