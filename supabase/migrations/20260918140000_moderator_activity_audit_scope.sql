-- Moderator-scoped community audit activity.
-- The broader administrative activity RPC remains System Administrator-only.

create or replace function public.moderation_list_activity(p_limit integer default 100, p_offset integer default 0)
returns table(
  id uuid,
  actor_email text,
  category text,
  event_type text,
  outcome text,
  occurred_at timestamptz,
  target_label text,
  details text
)
language plpgsql
security definer
set search_path = auth, public, pg_temp
as $function$
declare
  v_actor uuid := private.ops_assert_staff();
  v_limit integer := greatest(1, least(coalesce(p_limit, 100), 200));
  v_offset integer := greatest(0, coalesce(p_offset, 0));
begin
  if not private.is_moderation_authority() then raise exception 'Moderator access is required.'; end if;
  return query
  select a.id,
         coalesce(lower(u.email), 'System'),
         'MODERATION'::text,
         a.event_type,
         a.result,
         a.occurred_at,
         coalesce(a.metadata ->> 'post_id', a.entity_type),
         case when a.metadata ? 'reason' then a.metadata ->> 'reason' else null end
    from public.audit_events a
    left join auth.users u on u.id = a.actor_id
   where a.source = 'community-rpc'
     and (a.event_type like 'COMMUNITY_MODERATION_%'
       or a.event_type in ('COMMUNITY_POST_EDITED', 'COMMUNITY_POST_DELETED'))
   order by a.occurred_at desc
   limit v_limit offset v_offset;
end;
$function$;
revoke all on function public.moderation_list_activity(integer, integer) from public, anon;
grant execute on function public.moderation_list_activity(integer, integer) to authenticated, service_role;
comment on function public.moderation_list_activity(integer, integer) is 'Moderator-scoped, bounded community post lifecycle and moderation audit feed.';
