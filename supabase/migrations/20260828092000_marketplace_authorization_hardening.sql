-- Purpose: Harden Marketplace membership invitation and acceptance authorization.
-- Affected objects: public.marketplace_invite_member(...), public.marketplace_accept_invitation(text).
-- Grants/RLS effect: SECURITY DEFINER remains intentional; anon/public execution is revoked;
--                    authenticated callers still execute only through server-side owner/manager and identity checks.
-- Rollback consideration: use a new forward migration restoring the prior function definitions only after security review.

begin;

create or replace function public.marketplace_invite_member(
  p_business_id uuid,
  p_email text,
  p_role text,
  p_token_hash text,
  p_idempotency_key uuid
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $function$
declare
  v_actor uuid := private.marketplace_assert_editor(p_business_id);
  v_invite uuid;
  v_email text := lower(trim(coalesce(p_email, '')));
  v_role text := upper(trim(coalesce(p_role, '')));
  v_token_hash text := trim(coalesce(p_token_hash, ''));
begin
  if not exists(
    select 1
    from public.marketplace_business_members
    where business_id = p_business_id
      and user_id = v_actor
      and role in ('OWNER', 'MANAGER')
      and state = 'ACTIVE'
  ) then
    raise exception 'Only an owner or manager can invite members.' using errcode = '42501';
  end if;

  -- Ownership is never assignable through invitations; it remains exclusive to
  -- marketplace_transfer_ownership(), which separately requires the current owner.
  if upper(trim(coalesce(p_role, ''))) not in ('MANAGER', 'EDITOR') then
    raise exception 'Invite role must be MANAGER or EDITOR.' using errcode = '22023';
  end if;

  if char_length(v_email) not between 3 and 320
     or v_email !~ '^[^@[:space:]]+@[^@[:space:]]+\.[^@[:space:]]+$' then
    raise exception 'A valid bounded invitation email is required.' using errcode = '22023';
  end if;

  if char_length(v_token_hash) not between 32 and 256 then
    raise exception 'A valid bounded invitation token is required.' using errcode = '22023';
  end if;

  if p_idempotency_key is null then
    raise exception 'An idempotency key is required.' using errcode = '22023';
  end if;

  insert into public.marketplace_business_invitations(
    business_id,
    email,
    role,
    token_hash,
    invited_by
  ) values (
    p_business_id,
    v_email,
    v_role,
    v_token_hash,
    v_actor
  )
  returning id into v_invite;

  perform private.marketplace_audit(
    v_actor,
    'MEMBER_INVITED',
    p_business_id,
    null,
    jsonb_build_object('invitation_id', v_invite, 'role', v_role),
    p_idempotency_key
  );

  return jsonb_build_object('invitationId', v_invite, 'state', 'PENDING');
end
$function$;

revoke all on function public.marketplace_invite_member(uuid, text, text, text, uuid)
  from public, anon;
grant execute on function public.marketplace_invite_member(uuid, text, text, text, uuid)
  to authenticated;

create or replace function public.marketplace_accept_invitation(p_token_hash text)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $function$
declare
  v_actor uuid := private.marketplace_actor();
  v_actor_email text;
  v_token_hash text := trim(coalesce(p_token_hash, ''));
  v_invite public.marketplace_business_invitations%rowtype;
begin
  if char_length(v_token_hash) not between 32 and 256 then
    raise exception 'This invitation is not available.' using errcode = '22023';
  end if;

  select lower(email)
  into v_actor_email
  from auth.users
  where id = v_actor;

  if v_actor_email is null then
    raise exception 'This invitation is not available.' using errcode = '42501';
  end if;

  select *
  into v_invite
  from public.marketplace_business_invitations
  where token_hash = v_token_hash
    and state = 'PENDING'
    and expires_at > now()
  for update;

  if not found or lower(v_invite.email) <> v_actor_email then
    raise exception 'This invitation is not available.' using errcode = '42501';
  end if;

  if v_invite.role not in ('MANAGER', 'EDITOR') then
    raise exception 'This invitation is not available.' using errcode = '42501';
  end if;

  insert into public.marketplace_business_members(
    business_id,
    user_id,
    role,
    invited_by
  ) values (
    v_invite.business_id,
    v_actor,
    v_invite.role,
    v_invite.invited_by
  )
  on conflict (business_id, user_id) do update
  set role = excluded.role,
      state = 'ACTIVE',
      updated_at = now();

  update public.marketplace_business_invitations
  set state = 'ACCEPTED',
      accepted_by = v_actor,
      accepted_at = now()
  where id = v_invite.id;

  perform private.marketplace_audit(
    v_actor,
    'INVITATION_ACCEPTED',
    v_invite.business_id,
    null,
    jsonb_build_object('invitation_id', v_invite.id, 'role', v_invite.role),
    null
  );

  return jsonb_build_object('businessId', v_invite.business_id, 'role', v_invite.role);
end
$function$;

revoke all on function public.marketplace_accept_invitation(text) from public, anon;
grant execute on function public.marketplace_accept_invitation(text) to authenticated;

comment on function public.marketplace_invite_member(uuid, text, text, text, uuid) is
  'Intentional SECURITY DEFINER Marketplace RPC. Owner/manager authority, bounded role/email/token and idempotency are enforced server-side.';
comment on function public.marketplace_accept_invitation(text) is
  'Intentional SECURITY DEFINER Marketplace RPC. Bearer invitation token is bounded and must match the authenticated user email; OWNER cannot be granted.';

commit;
