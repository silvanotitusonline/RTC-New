-- Public Reports v1 database contract.
-- CLI provenance: Supabase CLI 2.115.0 generated 20260829114254_civic_reports_v1.sql
-- in workflow run 33250759731 using `supabase migration new civic_reports_v1`.
--
-- This migration is additive. It creates a separate civic-reporting domain and does not
-- repurpose Community moderation reports, Support cases, or Operations incidents.
-- Production deployment is intentionally not performed by this change.

begin;

create table public.civic_report_categories (
  id uuid primary key default gen_random_uuid(),
  slug text not null unique check (slug ~ '^[a-z0-9][a-z0-9-]{1,79}$'),
  label text not null unique check (char_length(trim(label)) between 3 and 100),
  description text not null default '' check (char_length(description) <= 500),
  sort_order smallint not null check (sort_order between 1 and 100),
  is_active boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table public.civic_reports (
  id uuid primary key default gen_random_uuid(),
  reporter_id uuid not null references auth.users(id) on delete restrict,
  client_request_id uuid not null,
  title text not null check (char_length(trim(title)) between 5 and 80),
  description text not null check (char_length(trim(description)) between 20 and 2000),
  started_at timestamptz,
  category_id uuid not null references public.civic_report_categories(id) on delete restrict,
  urgency text not null check (urgency in ('LOW','NORMAL','HIGH','CRITICAL')),
  status text not null default 'SUBMITTED' check (status in (
    'SUBMITTED','ACKNOWLEDGED','IN_PROGRESS','COMPLETED','CLOSED','REJECTED','DUPLICATE','WITHDRAWN'
  )),
  identity_mode text not null check (identity_mode in ('NAMED','ANONYMOUS')),
  public_location_label text not null check (char_length(trim(public_location_label)) between 3 and 180),
  public_latitude double precision check (public_latitude is null or public_latitude between -90 and 90),
  public_longitude double precision check (public_longitude is null or public_longitude between -180 and 180),
  public_until timestamptz not null default (statement_timestamp() + interval '24 months'),
  verified_at timestamptz,
  verified_by uuid references auth.users(id) on delete set null,
  verification_reason text check (verification_reason is null or char_length(trim(verification_reason)) between 3 and 1000),
  duplicate_of uuid references public.civic_reports(id) on delete set null,
  created_at timestamptz not null default statement_timestamp(),
  updated_at timestamptz not null default now(),
  constraint civic_reports_idempotency unique (reporter_id, client_request_id),
  constraint civic_reports_public_window check (public_until = created_at + interval '24 months'),
  constraint civic_reports_verification_consistency check (
    (verified_at is null and verified_by is null)
    or (verified_at is not null and verified_by is not null)
  ),
  constraint civic_reports_duplicate_consistency check (
    (status <> 'DUPLICATE' and duplicate_of is null)
    or (status = 'DUPLICATE' and duplicate_of is not null and duplicate_of <> id)
  )
);

create table public.civic_report_private_details (
  report_id uuid primary key references public.civic_reports(id) on delete cascade,
  exact_address text check (exact_address is null or char_length(trim(exact_address)) between 3 and 500),
  latitude double precision check (latitude is null or latitude between -90 and 90),
  longitude double precision check (longitude is null or longitude between -180 and 180),
  location_mode text not null check (location_mode in ('MAP','MANUAL')),
  no_evidence_reason text check (no_evidence_reason is null or char_length(trim(no_evidence_reason)) between 20 and 300),
  contact_permission boolean not null default false,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint civic_report_private_location_shape check (
    (location_mode = 'MAP' and latitude is not null and longitude is not null)
    or (location_mode = 'MANUAL' and exact_address is not null)
  )
);

create table public.civic_report_evidence (
  id uuid primary key default gen_random_uuid(),
  report_id uuid not null references public.civic_reports(id) on delete cascade,
  owner_id uuid not null references auth.users(id) on delete restrict,
  storage_path text not null unique check (char_length(storage_path) between 10 and 500),
  media_kind text not null check (media_kind in ('IMAGE','VIDEO')),
  mime_type text not null check (mime_type in ('image/jpeg','image/png','image/webp','video/mp4','video/webm')),
  byte_size bigint not null check (byte_size between 1 and 20971520),
  width integer check (width is null or width > 0),
  height integer check (height is null or height > 0),
  duration_seconds integer check (duration_seconds is null or duration_seconds between 1 and 180),
  position smallint not null check (position between 1 and 6),
  state text not null default 'FINALIZED' check (state in ('FINALIZED','REJECTED')),
  finalize_request_id uuid not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint civic_report_evidence_position unique (report_id, position),
  constraint civic_report_evidence_finalize_idempotency unique (owner_id, finalize_request_id),
  constraint civic_report_evidence_size_by_kind check (
    (media_kind = 'IMAGE' and byte_size <= 5242880 and duration_seconds is null)
    or (media_kind = 'VIDEO' and byte_size <= 20971520 and duration_seconds between 1 and 180)
  )
);

create table public.civic_report_votes (
  report_id uuid not null references public.civic_reports(id) on delete cascade,
  voter_id uuid not null references auth.users(id) on delete cascade,
  direction smallint not null check (direction in (-1,1)),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key (report_id, voter_id)
);

create table public.civic_report_comments (
  id uuid primary key default gen_random_uuid(),
  report_id uuid not null references public.civic_reports(id) on delete cascade,
  author_id uuid not null references auth.users(id) on delete cascade,
  client_request_id uuid not null,
  body text not null check (char_length(trim(body)) between 1 and 1000),
  state text not null default 'PUBLISHED' check (state in ('PUBLISHED','LIMITED_PENDING_REVIEW','HIDDEN_BY_MODERATION','DELETED_BY_AUTHOR')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint civic_report_comments_idempotency unique (author_id, client_request_id)
);

create table public.civic_report_status_history (
  id uuid primary key default gen_random_uuid(),
  report_id uuid not null references public.civic_reports(id) on delete cascade,
  from_status text check (from_status is null or from_status in (
    'SUBMITTED','ACKNOWLEDGED','IN_PROGRESS','COMPLETED','CLOSED','REJECTED','DUPLICATE','WITHDRAWN'
  )),
  to_status text not null check (to_status in (
    'SUBMITTED','ACKNOWLEDGED','IN_PROGRESS','COMPLETED','CLOSED','REJECTED','DUPLICATE','WITHDRAWN'
  )),
  actor_id uuid references auth.users(id) on delete set null,
  public_note text check (public_note is null or char_length(trim(public_note)) between 3 and 1000),
  private_note text check (private_note is null or char_length(trim(private_note)) between 3 and 2000),
  request_id uuid,
  created_at timestamptz not null default now()
);

create unique index civic_report_status_history_request_idx
  on public.civic_report_status_history(report_id, request_id)
  where request_id is not null;

-- Cover every FK and the report-feed access paths required by the blueprint.
create index civic_reports_reporter_idx on public.civic_reports(reporter_id, created_at desc, id desc);
create index civic_reports_category_idx on public.civic_reports(category_id, created_at desc, id desc);
create index civic_reports_verified_by_idx on public.civic_reports(verified_by) where verified_by is not null;
create index civic_reports_duplicate_of_idx on public.civic_reports(duplicate_of) where duplicate_of is not null;
create index civic_reports_visible_created_idx on public.civic_reports(created_at desc, id desc);
create index civic_reports_status_category_urgency_idx on public.civic_reports(status, category_id, urgency, created_at desc, id desc);
create index civic_reports_verified_created_idx on public.civic_reports(verified_at, created_at desc, id desc) where verified_at is not null;

create index civic_report_evidence_report_idx on public.civic_report_evidence(report_id, position);
create index civic_report_evidence_owner_idx on public.civic_report_evidence(owner_id, created_at desc);
create index civic_report_votes_activity_idx on public.civic_report_votes(report_id, updated_at desc);
create index civic_report_votes_voter_idx on public.civic_report_votes(voter_id, updated_at desc);
create index civic_report_comments_feed_idx on public.civic_report_comments(report_id, state, created_at desc, id desc);
create index civic_report_comments_author_idx on public.civic_report_comments(author_id, created_at desc);
create index civic_report_status_history_report_idx on public.civic_report_status_history(report_id, created_at desc, id desc);
create index civic_report_status_history_actor_idx on public.civic_report_status_history(actor_id, created_at desc) where actor_id is not null;

alter table public.civic_report_categories enable row level security;
alter table public.civic_reports enable row level security;
alter table public.civic_report_private_details enable row level security;
alter table public.civic_report_evidence enable row level security;
alter table public.civic_report_votes enable row level security;
alter table public.civic_report_comments enable row level security;
alter table public.civic_report_status_history enable row level security;

revoke all on table public.civic_report_categories from public, anon, authenticated;
revoke all on table public.civic_reports from public, anon, authenticated;
revoke all on table public.civic_report_private_details from public, anon, authenticated;
revoke all on table public.civic_report_evidence from public, anon, authenticated;
revoke all on table public.civic_report_votes from public, anon, authenticated;
revoke all on table public.civic_report_comments from public, anon, authenticated;
revoke all on table public.civic_report_status_history from public, anon, authenticated;

create policy civic_report_categories_no_direct_client_access
  on public.civic_report_categories as restrictive for all to anon, authenticated
  using (false) with check (false);
create policy civic_reports_no_direct_client_access
  on public.civic_reports as restrictive for all to anon, authenticated
  using (false) with check (false);
create policy civic_report_private_details_no_direct_client_access
  on public.civic_report_private_details as restrictive for all to anon, authenticated
  using (false) with check (false);
create policy civic_report_evidence_no_direct_client_access
  on public.civic_report_evidence as restrictive for all to anon, authenticated
  using (false) with check (false);
create policy civic_report_votes_no_direct_client_access
  on public.civic_report_votes as restrictive for all to anon, authenticated
  using (false) with check (false);
create policy civic_report_comments_no_direct_client_access
  on public.civic_report_comments as restrictive for all to anon, authenticated
  using (false) with check (false);
create policy civic_report_status_history_no_direct_client_access
  on public.civic_report_status_history as restrictive for all to anon, authenticated
  using (false) with check (false);

insert into public.civic_report_categories(slug,label,description,sort_order,is_active)
values
  ('water-sanitation','Water & Sanitation','Water supply, sanitation, leaks and sewer issues.',1,true),
  ('electricity-streetlights','Electricity & Streetlights','Electricity supply and public streetlight issues.',2,true),
  ('roads-traffic-transport','Roads, Traffic & Transport','Road, traffic and public transport infrastructure issues.',3,true),
  ('waste-illegal-dumping','Waste & Illegal Dumping','Waste collection, dumping and refuse issues.',4,true),
  ('public-buildings-facilities','Public Buildings & Facilities','Public building and municipal facility issues.',5,true),
  ('health-environmental-hazards','Health & Environmental Hazards','Public health and environmental hazards.',6,true),
  ('safety-security','Safety & Security','Public-space safety and security concerns.',7,true),
  ('housing-infrastructure','Housing & Infrastructure','Housing and general infrastructure concerns.',8,true),
  ('parks-public-spaces','Parks & Public Spaces','Parks, recreation areas and public-space issues.',9,true),
  ('community-services','Community Services','Community-service access or delivery issues.',10,true),
  ('other','Other','A civic issue not covered by another active category.',11,true)
on conflict (slug) do update
set label=excluded.label,
    description=excluded.description,
    sort_order=excluded.sort_order,
    is_active=excluded.is_active,
    updated_at=now();

-- These projections are intentionally narrow and security-barrier protected. Base civic
-- tables have no client SELECT grants, so callers can only consume the explicitly listed
-- sanitized columns below. Exact location, reporter id, evidence paths and private notes
-- are never columns of these projections.
create view public.civic_report_categories_public
with (security_barrier=true)
as
select id, slug, label, description, sort_order, is_active
from public.civic_report_categories
where is_active;

create view public.civic_reports_public
with (security_barrier=true)
as
select
  r.id,
  r.title,
  r.description,
  r.started_at,
  c.id as category_id,
  c.slug as category_slug,
  c.label as category_label,
  r.urgency,
  r.status,
  r.identity_mode,
  r.public_location_label,
  r.public_latitude,
  r.public_longitude,
  r.public_until,
  r.verified_at,
  r.verification_reason,
  r.duplicate_of,
  case
    when r.identity_mode='ANONYMOUS' then 'Anonymous community member'
    else coalesce(nullif(trim(p.display_name),''),'Community member')
  end as author_display_name,
  (select count(*)::integer from public.civic_report_votes v where v.report_id=r.id and v.direction=1) as thumbs_up_count,
  (select count(*)::integer from public.civic_report_votes v where v.report_id=r.id and v.direction=-1) as thumbs_down_count,
  (select count(*)::integer from public.civic_report_comments cm where cm.report_id=r.id and cm.state='PUBLISHED') as comment_count,
  (select count(*)::integer from public.civic_report_evidence e where e.report_id=r.id and e.state='FINALIZED') as evidence_count,
  (select v.direction from public.civic_report_votes v where v.report_id=r.id and v.voter_id=auth.uid()) as current_user_vote,
  (
    (select count(*) from public.civic_report_votes v where v.report_id=r.id and v.updated_at >= now()-interval '5 hours')
    + 2 * (select count(*) from public.civic_report_comments cm where cm.report_id=r.id and cm.state='PUBLISHED' and cm.created_at >= now()-interval '5 hours')
  )::integer as hot_score,
  r.created_at,
  r.updated_at
from public.civic_reports r
join public.civic_report_categories c on c.id=r.category_id
left join public.profiles p on p.id=r.reporter_id
where r.public_until > now();

create view public.civic_report_status_history_public
with (security_barrier=true)
as
select h.id, h.report_id, h.from_status, h.to_status, h.public_note, h.created_at
from public.civic_report_status_history h
join public.civic_reports r on r.id=h.report_id
where r.public_until > now();

create view public.civic_report_comments_public
with (security_barrier=true)
as
select
  cm.id,
  cm.report_id,
  case
    when r.identity_mode='ANONYMOUS' and cm.author_id=r.reporter_id then 'Anonymous community member'
    else coalesce(nullif(trim(p.display_name),''),'Community member')
  end as author_display_name,
  cm.body,
  cm.created_at,
  cm.updated_at
from public.civic_report_comments cm
join public.civic_reports r on r.id=cm.report_id
left join public.profiles p on p.id=cm.author_id
where cm.state='PUBLISHED' and r.public_until > now();

revoke all on public.civic_report_categories_public, public.civic_reports_public,
  public.civic_report_status_history_public, public.civic_report_comments_public
  from public, anon, authenticated;
grant select on public.civic_report_categories_public, public.civic_reports_public,
  public.civic_report_status_history_public, public.civic_report_comments_public
  to anon, authenticated;

insert into storage.buckets(id,name,public,file_size_limit,allowed_mime_types)
values (
  'civic-report-evidence',
  'civic-report-evidence',
  false,
  20971520,
  array['image/jpeg','image/png','image/webp','video/mp4','video/webm']
)
on conflict (id) do update
set public=false,
    file_size_limit=excluded.file_size_limit,
    allowed_mime_types=excluded.allowed_mime_types;

create policy civic_report_evidence_owner_insert on storage.objects
  for insert to authenticated
  with check (
    bucket_id='civic-report-evidence'
    and (storage.foldername(name))[1]=(select auth.uid())::text
  );
create policy civic_report_evidence_owner_update on storage.objects
  for update to authenticated
  using (
    bucket_id='civic-report-evidence'
    and (storage.foldername(name))[1]=(select auth.uid())::text
  )
  with check (
    bucket_id='civic-report-evidence'
    and (storage.foldername(name))[1]=(select auth.uid())::text
  );
create policy civic_report_evidence_owner_delete on storage.objects
  for delete to authenticated
  using (
    bucket_id='civic-report-evidence'
    and (storage.foldername(name))[1]=(select auth.uid())::text
  );

create or replace function private.civic_report_assert_resident()
returns uuid
language plpgsql
security definer
set search_path=''
as $$
declare
  v_actor uuid := auth.uid();
begin
  if v_actor is null or not private.access_session_is_current() then
    raise exception 'CIVIC_REPORT_AUTH_REQUIRED' using errcode='42501';
  end if;
  if not private.community_guidelines_accepted() then
    raise exception 'CIVIC_REPORT_GUIDELINES_REQUIRED' using errcode='42501';
  end if;
  if private.ops_community_paused() then
    raise exception 'CIVIC_REPORT_WRITES_PAUSED' using errcode='55000';
  end if;
  return v_actor;
end;
$$;

create or replace function private.civic_report_assert_staff(p_roles public.app_role[])
returns uuid
language plpgsql
security definer
set search_path=''
as $$
declare
  v_actor uuid := auth.uid();
  v_aal text := coalesce(auth.jwt()->>'aal','aal1');
begin
  if v_actor is null or not private.access_session_is_current() then
    raise exception 'CIVIC_REPORT_AUTH_REQUIRED' using errcode='42501';
  end if;
  if not private.has_any_role(p_roles) then
    raise exception 'CIVIC_REPORT_STAFF_ROLE_REQUIRED' using errcode='42501';
  end if;
  if v_aal <> 'aal2' then
    raise exception 'CIVIC_REPORT_STAFF_MFA_REQUIRED' using errcode='42501';
  end if;
  return v_actor;
end;
$$;

revoke all on function private.civic_report_assert_resident() from public, anon, authenticated;
revoke all on function private.civic_report_assert_staff(public.app_role[]) from public, anon, authenticated;

create or replace function public.civic_report_create_v1(
  p_client_request_id uuid,
  p_title text,
  p_description text,
  p_started_at timestamptz,
  p_category_id uuid,
  p_urgency text,
  p_identity_mode text,
  p_location_mode text,
  p_public_location_label text,
  p_latitude double precision,
  p_longitude double precision,
  p_exact_address text,
  p_no_evidence_reason text,
  p_contact_permission boolean,
  p_guidelines_version text
)
returns uuid
language plpgsql
security definer
set search_path=''
as $$
declare
  v_actor uuid := private.civic_report_assert_resident();
  v_existing uuid;
  v_report_id uuid;
  v_title text := trim(coalesce(p_title,''));
  v_description text := trim(coalesce(p_description,''));
  v_urgency text := upper(trim(coalesce(p_urgency,'')));
  v_identity text := upper(trim(coalesce(p_identity_mode,'')));
  v_location text := upper(trim(coalesce(p_location_mode,'')));
  v_public_label text := trim(coalesce(p_public_location_label,''));
  v_exact_address text := nullif(trim(coalesce(p_exact_address,'')),'');
  v_no_evidence text := nullif(trim(coalesce(p_no_evidence_reason,'')),'');
begin
  if p_client_request_id is null then
    raise exception 'CIVIC_REPORT_REQUEST_ID_REQUIRED' using errcode='22023';
  end if;

  select r.id into v_existing
  from public.civic_reports r
  where r.reporter_id=v_actor and r.client_request_id=p_client_request_id;
  if found then
    return v_existing;
  end if;

  if not public.claim_edge_function_rate_limit('civic-report-create',v_actor,3,3600) then
    raise exception 'CIVIC_REPORT_RATE_LIMIT' using errcode='P0001';
  end if;
  if char_length(v_title) not between 5 and 80 then
    raise exception 'CIVIC_REPORT_TITLE_INVALID' using errcode='22023';
  end if;
  if char_length(v_description) not between 20 and 2000 then
    raise exception 'CIVIC_REPORT_DESCRIPTION_INVALID' using errcode='22023';
  end if;
  if v_urgency not in ('LOW','NORMAL','HIGH','CRITICAL') then
    raise exception 'CIVIC_REPORT_URGENCY_INVALID' using errcode='22023';
  end if;
  if v_identity not in ('NAMED','ANONYMOUS') then
    raise exception 'CIVIC_REPORT_IDENTITY_INVALID' using errcode='22023';
  end if;
  if v_location not in ('MAP','MANUAL') then
    raise exception 'CIVIC_REPORT_LOCATION_MODE_INVALID' using errcode='22023';
  end if;
  if char_length(v_public_label) not between 3 and 180 then
    raise exception 'CIVIC_REPORT_PUBLIC_LOCATION_INVALID' using errcode='22023';
  end if;
  if v_location='MAP' and (p_latitude is null or p_longitude is null or p_latitude not between -90 and 90 or p_longitude not between -180 and 180) then
    raise exception 'CIVIC_REPORT_MAP_LOCATION_INVALID' using errcode='22023';
  end if;
  if v_location='MANUAL' and (v_exact_address is null or char_length(v_exact_address) not between 3 and 500) then
    raise exception 'CIVIC_REPORT_MANUAL_LOCATION_INVALID' using errcode='22023';
  end if;
  if v_no_evidence is not null and char_length(v_no_evidence) not between 20 and 300 then
    raise exception 'CIVIC_REPORT_EVIDENCE_EXCEPTION_INVALID' using errcode='22023';
  end if;
  if not exists(select 1 from public.civic_report_categories c where c.id=p_category_id and c.is_active) then
    raise exception 'CIVIC_REPORT_CATEGORY_INVALID' using errcode='22023';
  end if;
  if coalesce(trim(p_guidelines_version),'') <> private.current_community_guidelines_version()::text then
    raise exception 'CIVIC_REPORT_GUIDELINES_VERSION_INVALID' using errcode='22023';
  end if;

  insert into public.civic_reports(
    reporter_id,client_request_id,title,description,started_at,category_id,urgency,
    identity_mode,public_location_label,public_latitude,public_longitude
  ) values (
    v_actor,p_client_request_id,v_title,v_description,p_started_at,p_category_id,v_urgency,
    v_identity,v_public_label,
    case when v_location='MAP' then round(p_latitude::numeric,3)::double precision else null end,
    case when v_location='MAP' then round(p_longitude::numeric,3)::double precision else null end
  ) returning id into v_report_id;

  insert into public.civic_report_private_details(
    report_id,exact_address,latitude,longitude,location_mode,no_evidence_reason,contact_permission
  ) values (
    v_report_id,v_exact_address,p_latitude,p_longitude,v_location,v_no_evidence,coalesce(p_contact_permission,false)
  );

  insert into public.civic_report_status_history(report_id,from_status,to_status,actor_id,public_note,request_id)
  values(v_report_id,null,'SUBMITTED',v_actor,'Report submitted.',p_client_request_id);

  perform private.ops_log_audit(
    v_actor,'CIVIC_REPORT_CREATED','CIVIC_REPORT',v_report_id,'SUCCESS',
    jsonb_build_object('request_id',p_client_request_id,'identity_mode',v_identity,'urgency',v_urgency),
    'civic_reports'
  );
  return v_report_id;
exception
  when unique_violation then
    select r.id into v_existing from public.civic_reports r
    where r.reporter_id=v_actor and r.client_request_id=p_client_request_id;
    if v_existing is not null then return v_existing; end if;
    raise;
end;
$$;

create or replace function public.civic_report_page_v1(
  p_status text,
  p_urgency text,
  p_category_slug text,
  p_verified boolean,
  p_sort text,
  p_cursor_created_at timestamptz,
  p_cursor_id uuid,
  p_limit integer,
  p_evaluated_at timestamptz
)
returns setof public.civic_reports_public
language plpgsql
security invoker
set search_path=''
as $$
declare
  v_status text := upper(trim(coalesce(p_status,'ALL')));
  v_urgency text := upper(trim(coalesce(p_urgency,'')));
  v_category text := lower(trim(coalesce(p_category_slug,'')));
  v_sort text := upper(trim(coalesce(p_sort,'LATEST')));
  v_limit integer := greatest(1,least(coalesce(p_limit,20),50));
begin
  if v_status not in ('ALL','ACTIVE','COMPLETED','INACTIVE') then v_status := 'ALL'; end if;
  if v_urgency not in ('','LOW','NORMAL','HIGH','CRITICAL') then v_urgency := ''; end if;
  if v_sort not in ('LATEST','OLDEST','URGENCY','MOST_DISCUSSED','MOST_SUPPORTED','HOT') then v_sort := 'LATEST'; end if;

  return query
  select r.*
  from public.civic_reports_public r
  where (v_status='ALL'
      or (v_status='ACTIVE' and r.status in ('SUBMITTED','ACKNOWLEDGED','IN_PROGRESS'))
      or (v_status='COMPLETED' and r.status='COMPLETED')
      or (v_status='INACTIVE' and r.status in ('CLOSED','REJECTED','DUPLICATE','WITHDRAWN')))
    and (v_urgency='' or r.urgency=v_urgency)
    and (v_category='' or r.category_slug=v_category)
    and (p_verified is null or (r.verified_at is not null)=p_verified)
    and (
      p_cursor_created_at is null or p_cursor_id is null
      or (v_sort='OLDEST' and (r.created_at,r.id) > (p_cursor_created_at,p_cursor_id))
      or (v_sort<>'OLDEST' and (r.created_at,r.id) < (p_cursor_created_at,p_cursor_id))
    )
  order by
    case when v_sort='OLDEST' then r.created_at end asc,
    case when v_sort='URGENCY' then case r.urgency when 'CRITICAL' then 4 when 'HIGH' then 3 when 'NORMAL' then 2 else 1 end end desc,
    case when v_sort='MOST_DISCUSSED' then r.comment_count end desc,
    case when v_sort='MOST_SUPPORTED' then r.thumbs_up_count end desc,
    case when v_sort='HOT' then r.hot_score end desc,
    case when v_sort<>'OLDEST' then r.created_at end desc,
    r.id desc
  limit v_limit;
end;
$$;

create or replace function public.civic_report_get_v1(p_report_id uuid)
returns setof public.civic_reports_public
language sql
security invoker
set search_path=''
as $$
  select r.* from public.civic_reports_public r where r.id=p_report_id limit 1;
$$;

create or replace function public.civic_report_set_vote_v1(p_report_id uuid,p_direction smallint)
returns jsonb
language plpgsql
security definer
set search_path=''
as $$
declare
  v_actor uuid := private.civic_report_assert_resident();
  v_up integer;
  v_down integer;
begin
  if p_direction not in (-1,0,1) then
    raise exception 'CIVIC_REPORT_VOTE_INVALID' using errcode='22023';
  end if;
  if not public.claim_edge_function_rate_limit('civic-report-vote',v_actor,60,600) then
    raise exception 'CIVIC_REPORT_RATE_LIMIT' using errcode='P0001';
  end if;
  if not exists(select 1 from public.civic_reports r where r.id=p_report_id and r.public_until>now()) then
    raise exception 'CIVIC_REPORT_NOT_AVAILABLE' using errcode='P0002';
  end if;

  if p_direction=0 then
    delete from public.civic_report_votes where report_id=p_report_id and voter_id=v_actor;
  else
    insert into public.civic_report_votes(report_id,voter_id,direction)
    values(p_report_id,v_actor,p_direction)
    on conflict (report_id,voter_id) do update
      set direction=excluded.direction, updated_at=now();
  end if;

  select
    count(*) filter(where direction=1)::integer,
    count(*) filter(where direction=-1)::integer
  into v_up,v_down
  from public.civic_report_votes where report_id=p_report_id;

  return jsonb_build_object('thumbs_up_count',v_up,'thumbs_down_count',v_down,'current_user_vote',p_direction);
end;
$$;

create or replace function public.civic_report_comment_page_v1(
  p_report_id uuid,
  p_cursor_created_at timestamptz,
  p_cursor_id uuid,
  p_limit integer
)
returns setof public.civic_report_comments_public
language sql
security invoker
set search_path=''
as $$
  select cm.*
  from public.civic_report_comments_public cm
  where cm.report_id=p_report_id
    and (p_cursor_created_at is null or p_cursor_id is null or (cm.created_at,cm.id)<(p_cursor_created_at,p_cursor_id))
  order by cm.created_at desc,cm.id desc
  limit greatest(1,least(coalesce(p_limit,20),50));
$$;

create or replace function public.civic_report_add_comment_v1(
  p_report_id uuid,
  p_body text,
  p_client_request_id uuid
)
returns uuid
language plpgsql
security definer
set search_path=''
as $$
declare
  v_actor uuid := private.civic_report_assert_resident();
  v_existing uuid;
  v_comment_id uuid;
  v_body text := trim(coalesce(p_body,''));
  v_reporter uuid;
begin
  if p_client_request_id is null then raise exception 'CIVIC_REPORT_REQUEST_ID_REQUIRED' using errcode='22023'; end if;
  select cm.id into v_existing from public.civic_report_comments cm
  where cm.author_id=v_actor and cm.client_request_id=p_client_request_id;
  if found then return v_existing; end if;
  if char_length(v_body) not between 1 and 1000 then raise exception 'CIVIC_REPORT_COMMENT_INVALID' using errcode='22023'; end if;
  if not public.claim_edge_function_rate_limit('civic-report-comment',v_actor,20,600) then
    raise exception 'CIVIC_REPORT_RATE_LIMIT' using errcode='P0001';
  end if;
  select r.reporter_id into v_reporter from public.civic_reports r
  where r.id=p_report_id and r.public_until>now();
  if not found then raise exception 'CIVIC_REPORT_NOT_AVAILABLE' using errcode='P0002'; end if;

  insert into public.civic_report_comments(report_id,author_id,client_request_id,body)
  values(p_report_id,v_actor,p_client_request_id,v_body)
  returning id into v_comment_id;

  if v_reporter<>v_actor then
    insert into public.notification_events(recipient_id,notification_type,title,body,payload)
    values(v_reporter,'CIVIC_REPORT_COMMENT','New comment on your Public Report','A community member commented on your report.',jsonb_build_object('report_id',p_report_id));
  end if;
  return v_comment_id;
exception
  when unique_violation then
    select cm.id into v_existing from public.civic_report_comments cm
    where cm.author_id=v_actor and cm.client_request_id=p_client_request_id;
    if v_existing is not null then return v_existing; end if;
    raise;
end;
$$;

create or replace function public.civic_report_finalize_evidence_v1(
  p_report_id uuid,
  p_storage_path text,
  p_media_kind text,
  p_mime_type text,
  p_byte_size bigint,
  p_width integer,
  p_height integer,
  p_duration_seconds integer,
  p_position smallint,
  p_client_request_id uuid
)
returns uuid
language plpgsql
security definer
set search_path=''
as $$
declare
  v_actor uuid := private.civic_report_assert_resident();
  v_kind text := upper(trim(coalesce(p_media_kind,'')));
  v_mime text := lower(trim(coalesce(p_mime_type,'')));
  v_path text := trim(coalesce(p_storage_path,''));
  v_existing uuid;
  v_id uuid;
begin
  if p_client_request_id is null then raise exception 'CIVIC_REPORT_REQUEST_ID_REQUIRED' using errcode='22023'; end if;
  select e.id into v_existing from public.civic_report_evidence e
  where e.owner_id=v_actor and e.finalize_request_id=p_client_request_id;
  if found then return v_existing; end if;
  if not exists(select 1 from public.civic_reports r where r.id=p_report_id and r.reporter_id=v_actor) then
    raise exception 'CIVIC_REPORT_EVIDENCE_OWNER_MISMATCH' using errcode='42501';
  end if;
  if split_part(v_path,'/',1)<>v_actor::text then
    raise exception 'CIVIC_REPORT_EVIDENCE_PATH_INVALID' using errcode='42501';
  end if;
  if v_kind not in ('IMAGE','VIDEO') then raise exception 'CIVIC_REPORT_EVIDENCE_KIND_INVALID' using errcode='22023'; end if;
  if v_mime not in ('image/jpeg','image/png','image/webp','video/mp4','video/webm') then raise exception 'CIVIC_REPORT_EVIDENCE_MIME_INVALID' using errcode='22023'; end if;
  if p_position not between 1 and 6 then raise exception 'CIVIC_REPORT_EVIDENCE_POSITION_INVALID' using errcode='22023'; end if;
  if v_kind='IMAGE' and (p_byte_size not between 1 and 5242880 or p_duration_seconds is not null) then raise exception 'CIVIC_REPORT_EVIDENCE_SIZE_INVALID' using errcode='22023'; end if;
  if v_kind='VIDEO' and (p_byte_size not between 1 and 20971520 or p_duration_seconds not between 1 and 180) then raise exception 'CIVIC_REPORT_EVIDENCE_SIZE_INVALID' using errcode='22023'; end if;
  if (select count(*) from public.civic_report_evidence e where e.report_id=p_report_id and e.state='FINALIZED')>=6 then
    raise exception 'CIVIC_REPORT_EVIDENCE_LIMIT' using errcode='22023';
  end if;
  if not exists(select 1 from storage.objects o where o.bucket_id='civic-report-evidence' and o.name=v_path and o.owner_id::text=v_actor::text) then
    raise exception 'CIVIC_REPORT_EVIDENCE_OBJECT_MISSING' using errcode='P0002';
  end if;

  insert into public.civic_report_evidence(
    report_id,owner_id,storage_path,media_kind,mime_type,byte_size,width,height,duration_seconds,position,finalize_request_id
  ) values (
    p_report_id,v_actor,v_path,v_kind,v_mime,p_byte_size,p_width,p_height,p_duration_seconds,p_position,p_client_request_id
  ) returning id into v_id;
  return v_id;
end;
$$;

create or replace function public.civic_report_dashboard_v1()
returns table(reported_incidents bigint,repairs_completed bigint,active_reports bigint,inactive_reports bigint)
language sql
security invoker
set search_path=''
as $$
  select
    count(*)::bigint,
    count(*) filter(where status='COMPLETED')::bigint,
    count(*) filter(where status in ('SUBMITTED','ACKNOWLEDGED','IN_PROGRESS'))::bigint,
    count(*) filter(where status in ('CLOSED','REJECTED','DUPLICATE','WITHDRAWN'))::bigint
  from public.civic_reports_public;
$$;

create or replace function public.civic_report_my_page_v1(
  p_cursor_created_at timestamptz,
  p_cursor_id uuid,
  p_limit integer
)
returns setof public.civic_reports_public
language plpgsql
security definer
set search_path=''
as $$
declare
  v_actor uuid := private.civic_report_assert_resident();
begin
  return query
  select r.* from public.civic_reports_public r
  join public.civic_reports source on source.id=r.id
  where source.reporter_id=v_actor
    and (p_cursor_created_at is null or p_cursor_id is null or (source.created_at,source.id)<(p_cursor_created_at,p_cursor_id))
  order by source.created_at desc,source.id desc
  limit greatest(1,least(coalesce(p_limit,20),50));
end;
$$;

create or replace function public.civic_report_owner_private_details_v1(p_report_id uuid)
returns table(
  report_id uuid,
  exact_address text,
  latitude double precision,
  longitude double precision,
  location_mode text,
  no_evidence_reason text,
  contact_permission boolean
)
language plpgsql
security definer
set search_path=''
as $$
declare
  v_actor uuid;
  v_owner uuid;
  v_staff_sensitive boolean;
begin
  if auth.uid() is null or not private.access_session_is_current() then
    raise exception 'CIVIC_REPORT_AUTH_REQUIRED' using errcode='42501';
  end if;
  v_actor:=auth.uid();
  select r.reporter_id into v_owner from public.civic_reports r where r.id=p_report_id;
  if not found then raise exception 'CIVIC_REPORT_NOT_AVAILABLE' using errcode='P0002'; end if;
  v_staff_sensitive:=private.has_any_role(array['EVIDENCE_REVIEWER','SYSTEM_ADMIN']::public.app_role[]);
  if v_actor<>v_owner and not v_staff_sensitive then
    raise exception 'CIVIC_REPORT_PRIVATE_ACCESS_DENIED' using errcode='42501';
  end if;
  if v_actor<>v_owner and coalesce(auth.jwt()->>'aal','aal1')<>'aal2' then
    raise exception 'CIVIC_REPORT_STAFF_MFA_REQUIRED' using errcode='42501';
  end if;
  if v_actor<>v_owner then
    perform private.ops_log_audit(v_actor,'CIVIC_REPORT_PRIVATE_DETAILS_VIEWED','CIVIC_REPORT',p_report_id,'SUCCESS','{}'::jsonb,'civic_reports');
  end if;
  return query
  select d.report_id,d.exact_address,d.latitude,d.longitude,d.location_mode,d.no_evidence_reason,d.contact_permission
  from public.civic_report_private_details d where d.report_id=p_report_id;
end;
$$;

create or replace function public.admin_civic_report_page_v1(
  p_status text,
  p_urgency text,
  p_category_slug text,
  p_verified boolean,
  p_limit integer
)
returns table(
  id uuid,
  title text,
  status text,
  urgency text,
  category_slug text,
  verified_at timestamptz,
  reporter_id uuid,
  exact_address text,
  latitude double precision,
  longitude double precision,
  created_at timestamptz
)
language plpgsql
security definer
set search_path=''
as $$
declare
  v_actor uuid := private.civic_report_assert_staff(array['CASE_STAFF','EVIDENCE_REVIEWER','SYSTEM_ADMIN']::public.app_role[]);
  v_sensitive boolean := private.has_any_role(array['EVIDENCE_REVIEWER','SYSTEM_ADMIN']::public.app_role[]);
  v_status text := upper(trim(coalesce(p_status,'')));
  v_urgency text := upper(trim(coalesce(p_urgency,'')));
  v_category text := lower(trim(coalesce(p_category_slug,'')));
begin
  return query
  select r.id,r.title,r.status,r.urgency,c.slug,r.verified_at,
    case when v_sensitive then r.reporter_id else null end,
    case when v_sensitive then d.exact_address else null end,
    case when v_sensitive then d.latitude else null end,
    case when v_sensitive then d.longitude else null end,
    r.created_at
  from public.civic_reports r
  join public.civic_report_categories c on c.id=r.category_id
  join public.civic_report_private_details d on d.report_id=r.id
  where (v_status='' or r.status=v_status)
    and (v_urgency='' or r.urgency=v_urgency)
    and (v_category='' or c.slug=v_category)
    and (p_verified is null or (r.verified_at is not null)=p_verified)
  order by r.created_at desc,r.id desc
  limit greatest(1,least(coalesce(p_limit,50),100));
end;
$$;

create or replace function public.admin_civic_report_set_verification_v1(
  p_report_id uuid,
  p_verified boolean,
  p_reason text,
  p_request_id uuid
)
returns void
language plpgsql
security definer
set search_path=''
as $$
declare
  v_actor uuid := private.civic_report_assert_staff(array['EVIDENCE_REVIEWER','SYSTEM_ADMIN']::public.app_role[]);
  v_reason text := trim(coalesce(p_reason,''));
  v_current boolean;
begin
  if p_request_id is null then raise exception 'CIVIC_REPORT_REQUEST_ID_REQUIRED' using errcode='22023'; end if;
  if char_length(v_reason) not between 3 and 1000 then raise exception 'CIVIC_REPORT_VERIFICATION_REASON_REQUIRED' using errcode='22023'; end if;
  if exists(select 1 from public.audit_events a where a.entity_type='CIVIC_REPORT' and a.entity_id=p_report_id and a.metadata->>'request_id'=p_request_id::text and a.event_type like 'CIVIC_REPORT_VERIFICATION_%') then
    return;
  end if;
  select (r.verified_at is not null) into v_current from public.civic_reports r where r.id=p_report_id for update;
  if not found then raise exception 'CIVIC_REPORT_NOT_AVAILABLE' using errcode='P0002'; end if;

  update public.civic_reports
  set verified_at=case when p_verified then now() else null end,
      verified_by=case when p_verified then v_actor else null end,
      verification_reason=case when p_verified then v_reason else null end,
      updated_at=now()
  where id=p_report_id;

  perform private.ops_log_audit(
    v_actor,
    case when p_verified then 'CIVIC_REPORT_VERIFICATION_SET' else 'CIVIC_REPORT_VERIFICATION_CLEARED' end,
    'CIVIC_REPORT',p_report_id,'SUCCESS',
    jsonb_build_object('request_id',p_request_id,'reason',v_reason,'prior_verified',v_current,'new_verified',p_verified),
    'civic_reports'
  );
  insert into public.notification_events(recipient_id,notification_type,title,body,payload)
  select r.reporter_id,'CIVIC_REPORT_VERIFICATION','Public Report verification updated','The verification state of your Public Report changed.',jsonb_build_object('report_id',r.id,'verified',p_verified)
  from public.civic_reports r where r.id=p_report_id;
end;
$$;

create or replace function public.admin_civic_report_transition_v1(
  p_report_id uuid,
  p_to_status text,
  p_public_note text,
  p_private_note text,
  p_duplicate_of uuid,
  p_request_id uuid
)
returns void
language plpgsql
security definer
set search_path=''
as $$
declare
  v_actor uuid := private.civic_report_assert_staff(array['CASE_STAFF','EVIDENCE_REVIEWER','SYSTEM_ADMIN']::public.app_role[]);
  v_report public.civic_reports%rowtype;
  v_to text := upper(trim(coalesce(p_to_status,'')));
  v_public text := nullif(trim(coalesce(p_public_note,'')),'');
  v_private text := nullif(trim(coalesce(p_private_note,'')),'');
  v_allowed boolean := false;
begin
  if p_request_id is null then raise exception 'CIVIC_REPORT_REQUEST_ID_REQUIRED' using errcode='22023'; end if;
  if exists(select 1 from public.civic_report_status_history h where h.report_id=p_report_id and h.request_id=p_request_id) then return; end if;
  if v_to not in ('ACKNOWLEDGED','IN_PROGRESS','COMPLETED','CLOSED','REJECTED','DUPLICATE') then
    raise exception 'CIVIC_REPORT_TRANSITION_INVALID' using errcode='22023';
  end if;
  select * into v_report from public.civic_reports where id=p_report_id for update;
  if not found then raise exception 'CIVIC_REPORT_NOT_AVAILABLE' using errcode='P0002'; end if;

  v_allowed := case v_report.status
    when 'SUBMITTED' then v_to in ('ACKNOWLEDGED','REJECTED','DUPLICATE','CLOSED')
    when 'ACKNOWLEDGED' then v_to in ('IN_PROGRESS','COMPLETED','CLOSED','REJECTED','DUPLICATE')
    when 'IN_PROGRESS' then v_to in ('COMPLETED','CLOSED','REJECTED','DUPLICATE')
    when 'COMPLETED' then v_to='CLOSED'
    else false
  end;
  if not v_allowed then raise exception 'CIVIC_REPORT_TRANSITION_NOT_ALLOWED' using errcode='22023'; end if;
  if v_to in ('CLOSED','REJECTED','DUPLICATE') and (v_public is null or char_length(v_public) not between 3 and 1000) then
    raise exception 'CIVIC_REPORT_TRANSITION_REASON_REQUIRED' using errcode='22023';
  end if;
  if v_to='DUPLICATE' and (p_duplicate_of is null or p_duplicate_of=p_report_id or not exists(select 1 from public.civic_reports r where r.id=p_duplicate_of)) then
    raise exception 'CIVIC_REPORT_DUPLICATE_TARGET_INVALID' using errcode='22023';
  end if;

  update public.civic_reports
  set status=v_to,
      duplicate_of=case when v_to='DUPLICATE' then p_duplicate_of else null end,
      updated_at=now()
  where id=p_report_id;

  insert into public.civic_report_status_history(report_id,from_status,to_status,actor_id,public_note,private_note,request_id)
  values(p_report_id,v_report.status,v_to,v_actor,v_public,v_private,p_request_id);

  perform private.ops_log_audit(
    v_actor,'CIVIC_REPORT_STATUS_CHANGED','CIVIC_REPORT',p_report_id,'SUCCESS',
    jsonb_build_object('request_id',p_request_id,'from',v_report.status,'to',v_to,'duplicate_of',p_duplicate_of),
    'civic_reports'
  );
  insert into public.notification_events(recipient_id,notification_type,title,body,payload)
  values(v_report.reporter_id,'CIVIC_REPORT_STATUS','Public Report status updated','The status of your Public Report changed.',jsonb_build_object('report_id',p_report_id,'status',v_to));
end;
$$;

-- Owner withdrawal is a narrow lifecycle operation required by the WITHDRAWN state.
create or replace function public.civic_report_withdraw_v1(p_report_id uuid,p_reason text,p_request_id uuid)
returns void
language plpgsql
security definer
set search_path=''
as $$
declare
  v_actor uuid := private.civic_report_assert_resident();
  v_report public.civic_reports%rowtype;
  v_reason text := trim(coalesce(p_reason,''));
begin
  if p_request_id is null then raise exception 'CIVIC_REPORT_REQUEST_ID_REQUIRED' using errcode='22023'; end if;
  if exists(select 1 from public.civic_report_status_history h where h.report_id=p_report_id and h.request_id=p_request_id) then return; end if;
  select * into v_report from public.civic_reports where id=p_report_id and reporter_id=v_actor for update;
  if not found then raise exception 'CIVIC_REPORT_NOT_AVAILABLE' using errcode='P0002'; end if;
  if v_report.status not in ('SUBMITTED','ACKNOWLEDGED','IN_PROGRESS') then raise exception 'CIVIC_REPORT_WITHDRAW_NOT_ALLOWED' using errcode='22023'; end if;
  if char_length(v_reason) not between 3 and 1000 then raise exception 'CIVIC_REPORT_WITHDRAW_REASON_REQUIRED' using errcode='22023'; end if;
  update public.civic_reports set status='WITHDRAWN',updated_at=now() where id=p_report_id;
  insert into public.civic_report_status_history(report_id,from_status,to_status,actor_id,public_note,request_id)
  values(p_report_id,v_report.status,'WITHDRAWN',v_actor,'Withdrawn by reporter.',p_request_id);
  perform private.ops_log_audit(v_actor,'CIVIC_REPORT_WITHDRAWN','CIVIC_REPORT',p_report_id,'SUCCESS',jsonb_build_object('request_id',p_request_id),'civic_reports');
end;
$$;

revoke all on function public.civic_report_create_v1(uuid,text,text,timestamptz,uuid,text,text,text,text,double precision,double precision,text,text,boolean,text) from public, anon;
revoke all on function public.civic_report_page_v1(text,text,text,boolean,text,timestamptz,uuid,integer,timestamptz) from public;
revoke all on function public.civic_report_get_v1(uuid) from public;
revoke all on function public.civic_report_set_vote_v1(uuid,smallint) from public, anon;
revoke all on function public.civic_report_comment_page_v1(uuid,timestamptz,uuid,integer) from public;
revoke all on function public.civic_report_add_comment_v1(uuid,text,uuid) from public, anon;
revoke all on function public.civic_report_finalize_evidence_v1(uuid,text,text,text,bigint,integer,integer,integer,smallint,uuid) from public, anon;
revoke all on function public.civic_report_dashboard_v1() from public;
revoke all on function public.civic_report_my_page_v1(timestamptz,uuid,integer) from public, anon;
revoke all on function public.civic_report_owner_private_details_v1(uuid) from public, anon;
revoke all on function public.admin_civic_report_page_v1(text,text,text,boolean,integer) from public, anon;
revoke all on function public.admin_civic_report_set_verification_v1(uuid,boolean,text,uuid) from public, anon;
revoke all on function public.admin_civic_report_transition_v1(uuid,text,text,text,uuid,uuid) from public, anon;
revoke all on function public.civic_report_withdraw_v1(uuid,text,uuid) from public, anon;

grant execute on function public.civic_report_create_v1(uuid,text,text,timestamptz,uuid,text,text,text,text,double precision,double precision,text,text,boolean,text) to authenticated;
grant execute on function public.civic_report_page_v1(text,text,text,boolean,text,timestamptz,uuid,integer,timestamptz) to anon, authenticated;
grant execute on function public.civic_report_get_v1(uuid) to anon, authenticated;
grant execute on function public.civic_report_set_vote_v1(uuid,smallint) to authenticated;
grant execute on function public.civic_report_comment_page_v1(uuid,timestamptz,uuid,integer) to anon, authenticated;
grant execute on function public.civic_report_add_comment_v1(uuid,text,uuid) to authenticated;
grant execute on function public.civic_report_finalize_evidence_v1(uuid,text,text,text,bigint,integer,integer,integer,smallint,uuid) to authenticated;
grant execute on function public.civic_report_dashboard_v1() to anon, authenticated;
grant execute on function public.civic_report_my_page_v1(timestamptz,uuid,integer) to authenticated;
grant execute on function public.civic_report_owner_private_details_v1(uuid) to authenticated;
grant execute on function public.admin_civic_report_page_v1(text,text,text,boolean,integer) to authenticated;
grant execute on function public.admin_civic_report_set_verification_v1(uuid,boolean,text,uuid) to authenticated;
grant execute on function public.admin_civic_report_transition_v1(uuid,text,text,text,uuid,uuid) to authenticated;
grant execute on function public.civic_report_withdraw_v1(uuid,text,uuid) to authenticated;

comment on table public.civic_reports is 'Public Reports civic domain. Reporter identity and exact location are separated from public projections.';
comment on table public.civic_report_private_details is 'Sensitive owner/admin-only exact location and evidence-exception details; no direct client grants.';
comment on table public.civic_report_evidence is 'Private evidence metadata; storage paths are never exposed by public projections.';
comment on function public.admin_civic_report_transition_v1(uuid,text,text,text,uuid,uuid) is 'AAL2 staff lifecycle mutation with explicit role checks, audit and notification.';
comment on function public.admin_civic_report_set_verification_v1(uuid,boolean,text,uuid) is 'AAL2 Evidence Reviewer/System Administrator verification mutation with audit and notification.';

commit;
