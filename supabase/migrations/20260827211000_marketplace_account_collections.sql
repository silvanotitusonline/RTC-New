begin;

create or replace function public.marketplace_saved_businesses()
returns jsonb language sql stable security definer set search_path = '' as $$
  select coalesce(
    jsonb_agg(private.marketplace_card(f.business_id, null) order by f.created_at desc),
    '[]'::jsonb
  )
  from public.marketplace_favorites f
  join public.marketplace_businesses b on b.id=f.business_id and b.lifecycle_state='PUBLISHED'
  join public.marketplace_business_revisions r on r.id=b.current_public_revision_id and r.state='PUBLISHED'
  where f.user_id=private.marketplace_actor();
$$;

create or replace function public.marketplace_my_invitations()
returns jsonb language sql stable security definer set search_path = '' as $$
  select coalesce(jsonb_agg(jsonb_build_object(
    'id',i.id,
    'businessId',i.business_id,
    'businessName',coalesce(r.display_name,b.slug),
    'role',i.role,
    'state',i.state,
    'expiresAt',i.expires_at
  ) order by i.created_at desc), '[]'::jsonb)
  from public.marketplace_business_invitations i
  join public.marketplace_businesses b on b.id=i.business_id
  left join public.marketplace_business_revisions r on r.id=b.current_public_revision_id
  where lower(i.email)=lower(coalesce(auth.jwt()->>'email',''));
$$;

grant execute on function public.marketplace_saved_businesses(), public.marketplace_my_invitations() to authenticated;

commit;
