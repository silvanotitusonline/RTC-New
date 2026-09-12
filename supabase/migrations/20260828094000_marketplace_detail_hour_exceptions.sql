-- Marketplace public detail contract: expose special-date hour exceptions additively.
-- Sequenced after the 09:20 Marketplace authorization hardening migration on current main.

create or replace function public.marketplace_business_detail(
  p_business_id_or_slug text,
  p_lat double precision default null,
  p_lon double precision default null
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_actor uuid := private.marketplace_actor();
  v_business public.marketplace_businesses%rowtype;
  v_revision public.marketplace_business_revisions%rowtype;
  v_origin extensions.geography;
begin
  if p_lat is not null then
    if p_lon is null or p_lat not between -90 and 90 or p_lon not between -180 and 180 then
      raise exception 'Use valid coordinates.';
    end if;
    v_origin := extensions.ST_SetSRID(extensions.ST_MakePoint(p_lon,p_lat),4326)::extensions.geography;
  end if;

  select b.* into v_business
  from public.marketplace_businesses b
  where (b.id::text = p_business_id_or_slug or b.slug = p_business_id_or_slug)
    and b.lifecycle_state = 'PUBLISHED';

  if not found then raise exception 'This business is not available.'; end if;

  select * into v_revision
  from public.marketplace_business_revisions
  where id = v_business.current_public_revision_id and state = 'PUBLISHED';

  return jsonb_build_object(
    'business', private.marketplace_card(v_business.id,v_origin),
    'description', v_revision.description,
    'phone', v_revision.public_phone,
    'whatsappEnabled', v_revision.whatsapp_enabled,
    'email', v_revision.public_email,
    'websiteUrl', v_revision.website_url,
    'socialLinks', v_revision.social_links,
    'locations', coalesce((
      select jsonb_agg(jsonb_build_object(
        'id',l.id,'label',l.label,'locality',l.locality,'municipality',l.municipality,'province',l.province,
        'address',case when l.address_visibility='EXACT' then l.address_line1 else null end,
        'visibility',l.address_visibility,
        'latitude',case when l.public_point is null then null else extensions.ST_Y(l.public_point::extensions.geometry) end,
        'longitude',case when l.public_point is null then null else extensions.ST_X(l.public_point::extensions.geometry) end,
        'timezone',l.timezone,
        'hours',coalesce((select jsonb_agg(jsonb_build_object(
          'dayOfWeek',h.day_of_week,'intervalOrder',h.interval_order,'state',h.state,'opensAt',h.opens_at,'closesAt',h.closes_at
        ) order by h.day_of_week,h.interval_order) from public.marketplace_location_hours h where h.location_id=l.id),'[]'::jsonb),
        'hourExceptions',coalesce((select jsonb_agg(jsonb_build_object(
          'date',e.exception_date,'state',e.state,'opensAt',e.opens_at,'closesAt',e.closes_at,'note',e.note
        ) order by e.exception_date) from public.marketplace_location_hour_exceptions e where e.location_id=l.id),'[]'::jsonb),
        'accessibilityFeatures',l.accessibility_features,'parkingNote',l.parking_note
      ) order by l.is_primary desc,l.created_at)
      from public.marketplace_business_locations l where l.revision_id=v_revision.id
    ),'[]'::jsonb),
    'offerings',coalesce((select jsonb_agg(jsonb_build_object(
      'id',o.id,'type',o.offering_type,'title',o.title,'description',o.description,'priceType',o.price_type,
      'currencyCode',o.currency_code,'priceMin',o.price_min,'priceMax',o.price_max,'durationMinutes',o.duration_minutes,
      'availabilityNote',o.availability_note
    ) order by o.display_order,o.created_at) from public.marketplace_offerings o where o.revision_id=v_revision.id),'[]'::jsonb),
    'media',coalesce((select jsonb_agg(jsonb_build_object(
      'id',a.id,'type',a.asset_type,'path',a.object_path,'altText',a.alt_text,'focalPoint',a.focal_point,'displayOrder',a.display_order
    ) order by a.asset_type,a.display_order,a.created_at) from public.marketplace_media_assets a where a.revision_id=v_revision.id and a.state='FINALIZED'),'[]'::jsonb),
    'rating',coalesce((select jsonb_build_object(
      'average',s.rating_average,'count',s.review_count,'weightedScore',s.weighted_score,'distribution',s.rating_distribution
    ) from public.marketplace_business_rating_stats s where s.business_id=v_business.id),'{}'::jsonb),
    'saved',exists(select 1 from public.marketplace_favorites f where f.business_id=v_business.id and f.user_id=v_actor)
  );
end;
$$;

revoke all on function public.marketplace_business_detail(text,double precision,double precision) from public, anon;
grant execute on function public.marketplace_business_detail(text,double precision,double precision) to authenticated;
