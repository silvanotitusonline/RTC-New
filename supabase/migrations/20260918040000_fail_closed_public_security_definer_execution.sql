begin;

-- Public SECURITY DEFINER functions are a privileged API boundary. Preserve
-- existing authenticated/service_role grants, but fail closed for anonymous
-- callers before restoring the explicit public-read allowlist below.
do $$
declare
  v_function record;
begin
  for v_function in
    select
      n.nspname as schema_name,
      p.proname as function_name,
      pg_get_function_identity_arguments(p.oid) as identity_arguments
    from pg_proc p
    join pg_namespace n on n.oid = p.pronamespace
    where n.nspname = 'public'
      and p.prosecdef = true
  loop
    execute format(
      'revoke execute on function %I.%I(%s) from public, anon',
      v_function.schema_name,
      v_function.function_name,
      v_function.identity_arguments
    );
  end loop;
end
$$;

-- Explicit public-read allowlist. Every other public SECURITY DEFINER function
-- remains unavailable to anonymous callers after the fail-closed sweep.
grant execute on function public.civic_report_categories_v1() to anon;
grant execute on function public.civic_report_comment_page_v1(uuid, timestamptz, uuid, integer) to anon;
grant execute on function public.civic_report_dashboard_v1() to anon;
grant execute on function public.civic_report_dashboard_v2() to anon;
grant execute on function public.civic_report_evidence_public_v1(uuid) to anon;
grant execute on function public.civic_report_get_v1(uuid) to anon;
grant execute on function public.civic_report_page_v1(text, text, text, boolean, text, timestamptz, uuid, integer, timestamptz) to anon;
grant execute on function public.civic_report_page_v2(text, text, text, text, timestamptz, uuid, integer, timestamptz) to anon;
grant execute on function public.civic_report_timeline_v1(uuid) to anon;
grant execute on function public.ui_configuration_effective_global() to anon;

commit;
