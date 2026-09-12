-- Public-safe civic-report evidence listing.
-- Returns finalized metadata only when the parent report is visible in civic_reports_public.
-- Does not expose storage_path, owner_id, or private location fields.

create or replace function public.civic_report_evidence_public_v1(p_report_id uuid)
returns table (
  id uuid,
  report_id uuid,
  media_kind text,
  mime_type text,
  byte_size bigint,
  width integer,
  height integer,
  duration_seconds integer,
  sort_position smallint
)
language sql
stable
security definer
set search_path to ''
as $$
  select
    e.id,
    e.report_id,
    e.media_kind,
    e.mime_type,
    e.byte_size,
    e.width,
    e.height,
    e.duration_seconds,
    e.position
  from public.civic_report_evidence e
  where e.report_id = p_report_id
    and e.state = 'FINALIZED'
    and exists (
      select 1
      from public.civic_reports_public r
      where r.id = p_report_id
    )
  order by e.position asc, e.created_at asc;
$$;

revoke all on function public.civic_report_evidence_public_v1(uuid) from public;
grant execute on function public.civic_report_evidence_public_v1(uuid) to anon, authenticated;
