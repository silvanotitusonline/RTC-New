begin;

-- Keep no-evidence drafts undiscoverable until the resident either finalizes at least
-- one evidence object or has supplied the validated safety/accessibility exception.
create or replace view public.civic_reports_public
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
where r.public_until > now()
  and (
    exists (
      select 1
      from public.civic_report_evidence e
      where e.report_id=r.id and e.state='FINALIZED'
    )
    or exists (
      select 1
      from public.civic_report_private_details d
      where d.report_id=r.id and d.no_evidence_reason is not null
    )
  );

-- Child public projections must inherit the same discovery gate; a guessed report UUID
-- must not expose history/comments while the parent report is intentionally hidden.
create or replace view public.civic_report_status_history_public
with (security_barrier=true)
as
select h.id, h.report_id, h.from_status, h.to_status, h.public_note, h.created_at
from public.civic_report_status_history h
join public.civic_reports_public visible on visible.id=h.report_id;

create or replace view public.civic_report_comments_public
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
join public.civic_reports_public visible on visible.id=r.id
left join public.profiles p on p.id=cm.author_id
where cm.state='PUBLISHED';

revoke all on public.civic_reports_public,
  public.civic_report_status_history_public,
  public.civic_report_comments_public
  from public, anon, authenticated;
grant select on public.civic_reports_public,
  public.civic_report_status_history_public,
  public.civic_report_comments_public
  to anon, authenticated;

-- A valid Auth session alone is insufficient when Auth has disabled/banned the account.
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
  if exists (
    select 1
    from auth.users u
    where u.id=v_actor
      and u.banned_until is not null
      and u.banned_until > now()
  ) then
    raise exception 'CIVIC_REPORT_ACCOUNT_DISABLED' using errcode='42501';
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

revoke all on function private.civic_report_assert_resident() from public, anon, authenticated;

-- Evidence paths use the stable shape <owner>/<report client_request_id>/<object>.
-- Binding segment two prevents a resident from finalizing an upload staged for a
-- different draft/report while retaining the existing owner and object checks.
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
  v_report_request_id uuid;
  v_existing uuid;
  v_id uuid;
begin
  if p_client_request_id is null then raise exception 'CIVIC_REPORT_REQUEST_ID_REQUIRED' using errcode='22023'; end if;
  select e.id into v_existing from public.civic_report_evidence e
  where e.owner_id=v_actor and e.finalize_request_id=p_client_request_id;
  if found then return v_existing; end if;

  select r.client_request_id into v_report_request_id
  from public.civic_reports r
  where r.id=p_report_id and r.reporter_id=v_actor;
  if not found then
    raise exception 'CIVIC_REPORT_EVIDENCE_OWNER_MISMATCH' using errcode='42501';
  end if;

  if split_part(v_path,'/',1)<>v_actor::text
     or split_part(v_path,'/',2)<>v_report_request_id::text then
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
  if not exists (
    select 1
    from storage.objects o
    where o.bucket_id='civic-report-evidence'
      and o.name=v_path
      and o.owner_id::text=v_actor::text
  ) then
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

revoke all on function public.civic_report_finalize_evidence_v1(uuid,text,text,text,bigint,integer,integer,integer,smallint,uuid)
  from public, anon;
grant execute on function public.civic_report_finalize_evidence_v1(uuid,text,text,text,bigint,integer,integer,integer,smallint,uuid)
  to authenticated;

commit;
