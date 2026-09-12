-- Product decision: RTC-LIVE does not process or facilitate payments.
-- This forward migration removes the Service Centre commitment-fee subsystem and
-- collapses provider acceptance directly into CONFIRMED booking state.

begin;

-- Preserve existing accepted bookings by confirming them before the old status is removed.
update public.service_centre_bookings
set status = 'CONFIRMED',
    confirmed_at = coalesce(confirmed_at, accepted_at, now()),
    updated_at = now()
where status = 'ACCEPTED_AWAITING_PAYMENT';

-- Return booking payloads without any payment/commitment-fee field.
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

-- Provider acceptance is now the authoritative confirmation event. There is no
-- payment gate or intermediate payment-related booking status.
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
  if p_idempotency_key is null then
    raise exception 'Idempotency key is required.' using errcode = '22023';
  end if;
  if exists(
    select 1 from public.service_centre_booking_events e
    where e.actor_user_id = v_actor
      and e.event_type = 'BOOKING_ACCEPTED'
      and e.idempotency_key = p_idempotency_key
  ) then
    return private.service_centre_booking_payload(p_booking_id, v_actor);
  end if;

  select * into v_booking
  from public.service_centre_bookings b
  where b.id = p_booking_id
  for update;

  if not found or v_booking.provider_user_id <> v_actor then
    raise exception 'Only the booking provider may accept this request.' using errcode = '42501';
  end if;
  if v_booking.status <> 'PENDING_PROVIDER' then
    raise exception 'This booking is no longer pending.' using errcode = '22023';
  end if;

  update public.service_centre_bookings
  set status = 'CONFIRMED',
      accepted_at = now(),
      confirmed_at = now(),
      updated_at = now()
  where id = p_booking_id;

  perform private.service_centre_append_event(
    p_booking_id,
    v_actor,
    'BOOKING_ACCEPTED',
    'PENDING_PROVIDER',
    'CONFIRMED',
    p_idempotency_key,
    '{}'::jsonb
  );
  return private.service_centre_booking_payload(p_booking_id, v_actor);
end;
$$;

-- A participant may cancel a pending or confirmed booking. No payment record exists
-- to cancel or reconcile.
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
  if p_idempotency_key is null then
    raise exception 'Idempotency key is required.' using errcode = '22023';
  end if;
  if exists(
    select 1 from public.service_centre_booking_events e
    where e.actor_user_id = v_actor
      and e.event_type = 'BOOKING_CANCELLED'
      and e.idempotency_key = p_idempotency_key
  ) then
    return private.service_centre_booking_payload(p_booking_id, v_actor);
  end if;

  select * into v_booking
  from public.service_centre_bookings b
  where b.id = p_booking_id
  for update;

  if not found or v_actor not in (v_booking.customer_user_id, v_booking.provider_user_id) then
    raise exception 'Only a booking participant may cancel.' using errcode = '42501';
  end if;
  if v_booking.status not in ('PENDING_PROVIDER', 'CONFIRMED') then
    raise exception 'This booking can no longer be cancelled.' using errcode = '22023';
  end if;

  v_from := v_booking.status;
  update public.service_centre_bookings
  set status = 'CANCELLED', cancelled_at = now(), updated_at = now()
  where id = p_booking_id;

  perform private.service_centre_append_event(
    p_booking_id,
    v_actor,
    'BOOKING_CANCELLED',
    v_from,
    'CANCELLED',
    p_idempotency_key,
    '{}'::jsonb
  );
  return private.service_centre_booking_payload(p_booking_id, v_actor);
end;
$$;

-- Notification context follows the direct-confirmation state machine.
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
  select * into v_booking from public.service_centre_bookings b where b.id = p_booking_id;
  if not found or v_actor not in (v_booking.customer_user_id, v_booking.provider_user_id) then
    raise exception 'Only a booking participant may create its notification.' using errcode = '42501';
  end if;
  select p.display_name into v_actor_name from public.profiles p where p.id = v_actor;
  v_recipient := case when v_actor = v_booking.customer_user_id then v_booking.provider_user_id else v_booking.customer_user_id end;

  if v_event = 'SERVICE_BOOKING_NEW' then
    if v_actor <> v_booking.customer_user_id or v_booking.status <> 'PENDING_PROVIDER' then
      raise exception 'Invalid new-booking notification.' using errcode = '42501';
    end if;
    v_title := 'New booking request';
    v_body := v_actor_name || ' sent a ' || v_booking.category_name_snapshot || ' booking offer.';
  elsif v_event = 'SERVICE_BOOKING_ACCEPTED' then
    if v_actor <> v_booking.provider_user_id or v_booking.status <> 'CONFIRMED' then
      raise exception 'Invalid accepted-booking notification.' using errcode = '42501';
    end if;
    v_title := 'Booking confirmed';
    v_body := 'Your provider accepted and confirmed the booking.';
  elsif v_event = 'SERVICE_BOOKING_DECLINED' then
    if v_actor <> v_booking.provider_user_id or v_booking.status <> 'DECLINED' then
      raise exception 'Invalid declined-booking notification.' using errcode = '42501';
    end if;
    v_title := 'Booking declined';
    v_body := 'The provider declined this booking request.';
  elsif v_event = 'SERVICE_BOOKING_MESSAGE' then
    if p_message_id is null or not exists(
      select 1 from public.service_centre_booking_messages m
      where m.id = p_message_id and m.booking_id = p_booking_id and m.sender_user_id = v_actor
    ) then
      raise exception 'Invalid booking-message notification.' using errcode = '42501';
    end if;
    v_title := 'New booking message';
    v_body := v_actor_name || ' sent you a message.';
  elsif v_event = 'SERVICE_BOOKING_COMPLETED' then
    if v_actor <> v_booking.provider_user_id or v_booking.status <> 'COMPLETED' then
      raise exception 'Invalid completed-booking notification.' using errcode = '42501';
    end if;
    v_title := 'Booking completed';
    v_body := 'Your ' || v_booking.category_name_snapshot || ' booking was marked completed.';
  elsif v_event = 'SERVICE_BOOKING_CANCELLED' then
    if v_booking.status <> 'CANCELLED' then
      raise exception 'Invalid cancelled-booking notification.' using errcode = '42501';
    end if;
    v_title := 'Booking cancelled';
    v_body := v_actor_name || ' cancelled the booking.';
  else
    raise exception 'Unsupported Service Centre notification event.' using errcode = '22023';
  end if;

  return jsonb_build_object(
    'recipientUserId', v_recipient,
    'title', v_title,
    'body', v_body,
    'payload', jsonb_build_object(
      'notification_type', v_event,
      'booking_id', p_booking_id::text,
      'route', 'rtc://service-centre/booking/' || p_booking_id::text,
      'message_id', case when p_message_id is null then '' else p_message_id::text end
    )
  );
end;
$$;

-- Remove all payment authority and data surfaces.
drop function if exists public.service_centre_attach_commitment_checkout(uuid, text);
drop function if exists public.service_centre_confirm_commitment_payment(text, text, integer, text);
drop function if exists public.service_centre_prepare_commitment_payment(uuid, uuid);

drop table if exists public.service_centre_booking_payments;

alter table public.service_centre_bookings
  drop constraint if exists service_centre_bookings_status_check;

alter table public.service_centre_bookings
  add constraint service_centre_bookings_status_check
  check (status in ('PENDING_PROVIDER','CONFIRMED','COMPLETED','DECLINED','CANCELLED'));

alter table public.service_centre_bookings
  drop column if exists commitment_fee_amount;

-- Reassert the client grants for the functions replaced above.
revoke all on function public.service_centre_accept_booking(uuid,uuid) from public, anon;
grant execute on function public.service_centre_accept_booking(uuid,uuid) to authenticated;
revoke all on function public.service_centre_cancel_booking(uuid,uuid) from public, anon;
grant execute on function public.service_centre_cancel_booking(uuid,uuid) to authenticated;
revoke all on function public.service_centre_notification_context(uuid,text,uuid) from public, anon;
grant execute on function public.service_centre_notification_context(uuid,text,uuid) to authenticated;

commit;
