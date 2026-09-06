-- Replace direct client access to civic-report projection views with narrow RPC reads.
-- The views remain internal implementation details and are explicitly SECURITY INVOKER.
-- Public RPCs use fixed search paths, explicit sanitized outputs, and least-privilege grants.

begin;

alter view public.civic_report_categories_public
  set (security_invoker=true, security_barrier=true);
alter view public.civic_reports_public
  set (security_invoker=true, security_barrier=true);
alter view public.civic_report_status_history_public
  set (security_invoker=true, security_barrier=true);
alter view public.civic_report_comments_public
  set (security_invoker=true, security_barrier=true);

revoke all on table
  public.civic_report_categories_public,
  public.civic_reports_public,
  public.civic_report_status_history_public,
  public.civic_report_comments_public
from public, anon, authenticated;

-- These functions are the public read boundary. They run as their owner only so
-- callers never receive SELECT on the protected base tables or retained views.
alter function public.civic_report_page_v1(
  text,text,text,boolean,text,timestamptz,uuid,integer,timestamptz
) stable security definer;
alter function public.civic_report_page_v1(
  text,text,text,boolean,text,timestamptz,uuid,integer,timestamptz
) set search_path to '';

alter function public.civic_report_page_v2(
  text,text,text,text,timestamptz,uuid,integer,timestamptz
) stable security definer;
alter function public.civic_report_page_v2(
  text,text,text,text,timestamptz,uuid,integer,timestamptz
) set search_path to '';

alter function public.civic_report_get_v1(uuid) stable security definer;
alter function public.civic_report_get_v1(uuid) set search_path to '';

alter function public.civic_report_comment_page_v1(
  uuid,timestamptz,uuid,integer
) stable security definer;
alter function public.civic_report_comment_page_v1(
  uuid,timestamptz,uuid,integer
) set search_path to '';

alter function public.civic_report_dashboard_v1() stable security definer;
alter function public.civic_report_dashboard_v1() set search_path to '';

alter function public.civic_report_dashboard_v2() stable security definer;
alter function public.civic_report_dashboard_v2() set search_path to '';

alter function public.civic_report_my_page_v1(
  timestamptz,uuid,integer
) stable security definer;
alter function public.civic_report_my_page_v1(
  timestamptz,uuid,integer
) set search_path to '';

alter function public.civic_report_evidence_public_v1(uuid) stable security definer;
alter function public.civic_report_evidence_public_v1(uuid) set search_path to '';

create or replace function public.civic_report_categories_v1()
returns table(
  id uuid,
  slug text,
  label text,
  description text,
  sort_order smallint,
  is_active boolean
)
language sql
stable
security definer
set search_path=''
as $$
  select
    category.id,
    category.slug,
    category.label,
    category.description,
    category.sort_order,
    category.is_active
  from public.civic_report_categories_public category
  order by category.sort_order asc, category.id asc;
$$;

create or replace function public.civic_report_timeline_v1(p_report_id uuid)
returns table(
  id uuid,
  report_id uuid,
  from_status text,
  to_status text,
  public_note text,
  created_at timestamptz
)
language sql
stable
security definer
set search_path=''
as $$
  select
    entry.id,
    entry.report_id,
    entry.from_status,
    entry.to_status,
    entry.public_note,
    entry.created_at
  from public.civic_report_status_history_public entry
  where entry.report_id=p_report_id
  order by entry.created_at asc, entry.id asc;
$$;

revoke all on function public.civic_report_page_v1(
  text,text,text,boolean,text,timestamptz,uuid,integer,timestamptz
) from public, anon, authenticated;
revoke all on function public.civic_report_page_v2(
  text,text,text,text,timestamptz,uuid,integer,timestamptz
) from public, anon, authenticated;
revoke all on function public.civic_report_get_v1(uuid) from public, anon, authenticated;
revoke all on function public.civic_report_comment_page_v1(
  uuid,timestamptz,uuid,integer
) from public, anon, authenticated;
revoke all on function public.civic_report_dashboard_v1() from public, anon, authenticated;
revoke all on function public.civic_report_dashboard_v2() from public, anon, authenticated;
revoke all on function public.civic_report_my_page_v1(
  timestamptz,uuid,integer
) from public, anon, authenticated;
revoke all on function public.civic_report_evidence_public_v1(uuid) from public, anon, authenticated;
revoke all on function public.civic_report_categories_v1() from public, anon, authenticated;
revoke all on function public.civic_report_timeline_v1(uuid) from public, anon, authenticated;

grant execute on function public.civic_report_page_v1(
  text,text,text,boolean,text,timestamptz,uuid,integer,timestamptz
) to anon, authenticated;
grant execute on function public.civic_report_page_v2(
  text,text,text,text,timestamptz,uuid,integer,timestamptz
) to anon, authenticated;
grant execute on function public.civic_report_get_v1(uuid) to anon, authenticated;
grant execute on function public.civic_report_comment_page_v1(
  uuid,timestamptz,uuid,integer
) to anon, authenticated;
grant execute on function public.civic_report_dashboard_v1() to anon, authenticated;
grant execute on function public.civic_report_dashboard_v2() to anon, authenticated;
grant execute on function public.civic_report_my_page_v1(
  timestamptz,uuid,integer
) to authenticated;
grant execute on function public.civic_report_evidence_public_v1(uuid) to anon, authenticated;
grant execute on function public.civic_report_categories_v1() to anon, authenticated;
grant execute on function public.civic_report_timeline_v1(uuid) to anon, authenticated;

comment on function public.civic_report_categories_v1() is
  'Public-safe active civic-report category catalogue; views and base tables remain ungranted.';
comment on function public.civic_report_timeline_v1(uuid) is
  'Public-safe status timeline for a visible civic report; private notes and actors are never returned.';

commit;
