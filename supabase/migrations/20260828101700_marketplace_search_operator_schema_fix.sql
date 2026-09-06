begin;

-- Forward repair for environments that already applied the initial pagination function.
-- pg_trgm is installed in the extensions schema while this SECURITY DEFINER function
-- intentionally runs with an empty search_path, so the similarity operator must be qualified.

create or replace function public.marketplace_search_businesses_page(
  p_query text default null,
  p_category_id uuid default null,
  p_locality text default null,
  p_lat double precision default null,
  p_lon double precision default null,
  p_radius_metres integer default null,
  p_min_rating numeric default null,
  p_verified_only boolean default false,
  p_sort text default 'RECOMMENDED',
  p_offset integer default 0,
  p_limit integer default 20
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_origin extensions.geography;
  v_query text := nullif(trim(coalesce(p_query, '')), '');
  v_locality text := nullif(trim(coalesce(p_locality, '')), '');
  v_sort text := upper(trim(coalesce(p_sort, 'RECOMMENDED')));
  v_limit integer := greatest(1, least(coalesce(p_limit, 20), 50));
  v_offset integer := greatest(0, coalesce(p_offset, 0));
  v_radius integer := greatest(100, least(coalesce(p_radius_metres, 50000), 50000));
  v_items jsonb := '[]'::jsonb;
  v_has_more boolean := false;
begin
  perform private.marketplace_actor();
  if char_length(coalesce(p_query, '')) > 160 then raise exception 'Search query is too long.' using errcode = '22023'; end if;
  if char_length(coalesce(p_locality, '')) > 120 then raise exception 'Locality filter is too long.' using errcode = '22023'; end if;
  if v_sort not in ('RECOMMENDED', 'DISTANCE', 'TOP_RATED', 'NEWEST', 'NAME') then raise exception 'Unsupported Marketplace sort mode.' using errcode = '22023'; end if;
  if v_offset > 5000 then raise exception 'Marketplace search offset is outside the supported window.' using errcode = '22023'; end if;
  if p_min_rating is not null and p_min_rating not between 0 and 5 then raise exception 'Minimum rating must be between zero and five.' using errcode = '22023'; end if;
  if (p_lat is null) <> (p_lon is null) then raise exception 'Provide both latitude and longitude or neither.' using errcode = '22023'; end if;
  if p_lat is not null then
    if p_lat not between -90 and 90 or p_lon not between -180 and 180 then raise exception 'Use valid coordinates.' using errcode = '22023'; end if;
    v_origin := extensions.ST_SetSRID(extensions.ST_MakePoint(p_lon, p_lat), 4326)::extensions.geography;
  end if;

  with candidates as (
    select b.id, lower(r.display_name) as display_name_sort,
      coalesce(s.weighted_score,0) as weighted, coalesce(s.rating_average,0) as average,
      b.published_at,b.updated_at,
      (select min(extensions.ST_Distance(l.public_point,v_origin)) from public.marketplace_business_locations l where l.revision_id=r.id and l.public_point is not null) as distance
    from public.marketplace_businesses b
    join public.marketplace_business_revisions r on r.id=b.current_public_revision_id and r.state='PUBLISHED'
    left join public.marketplace_business_rating_stats s on s.business_id=b.id
    where b.lifecycle_state='PUBLISHED'
      and (v_query is null
        or r.display_name OPERATOR(extensions.%) v_query
        or r.tagline OPERATOR(extensions.%) v_query
        or r.description OPERATOR(extensions.%) v_query)
      and (p_category_id is null or exists(select 1 from public.marketplace_revision_categories rc where rc.revision_id=r.id and rc.category_id=p_category_id))
      and (v_locality is null or exists(select 1 from public.marketplace_business_locations l where l.revision_id=r.id and l.locality ilike v_locality))
      and (v_origin is null or exists(select 1 from public.marketplace_business_locations l where l.revision_id=r.id and l.public_point is not null and extensions.ST_DWithin(l.public_point,v_origin,v_radius)))
      and (p_min_rating is null or coalesce(s.rating_average,0)>=p_min_rating)
      and (not p_verified_only or exists(select 1 from public.marketplace_business_verifications v where v.business_id=b.id and v.state='VERIFIED' and (v.expires_at is null or v.expires_at>now())))
  ), ordered as (
    select c.*, row_number() over(order by
      case when v_sort='DISTANCE' then c.distance end asc nulls last,
      case when v_sort='TOP_RATED' then c.weighted end desc,
      case when v_sort='NEWEST' then c.published_at end desc,
      case when v_sort='NAME' then c.display_name_sort end asc,
      c.weighted desc,c.updated_at desc,c.id) as ordinal
    from candidates c
  ), requested as (
    select * from ordered where ordinal>v_offset order by ordinal limit v_limit+1
  )
  select coalesce(jsonb_agg(private.marketplace_card(requested.id,v_origin) order by requested.ordinal)
    filter(where requested.ordinal<=v_offset+v_limit),'[]'::jsonb), count(*)>v_limit
  into v_items,v_has_more from requested;

  return jsonb_build_object('items',v_items,'nextOffset',case when v_has_more then v_offset+jsonb_array_length(v_items) else null end,'hasMore',v_has_more);
end;
$$;

revoke all on function public.marketplace_search_businesses_page(text,uuid,text,double precision,double precision,integer,numeric,boolean,text,integer,integer) from public, anon;
grant execute on function public.marketplace_search_businesses_page(text,uuid,text,double precision,double precision,integer,numeric,boolean,text,integer,integer) to authenticated;

commit;
