-- RTC Community isolated non-production remediation.
-- This closes unexpected explicit anon/service_role EXECUTE grants observed after
-- applying the Phase 1 configuration lifecycle migration. It contains no data change.

revoke all on function public.ui_configuration_create_draft(jsonb, text, uuid) from public, anon, authenticated, service_role;
revoke all on function public.ui_configuration_publish_draft(uuid, text, text) from public, anon, authenticated, service_role;
revoke all on function public.ui_configuration_revert(uuid, text, text) from public, anon, authenticated, service_role;
revoke all on function public.ui_configuration_effective_global_home() from public, anon, authenticated, service_role;

grant execute on function public.ui_configuration_create_draft(jsonb, text, uuid) to authenticated;
grant execute on function public.ui_configuration_publish_draft(uuid, text, text) to authenticated;
grant execute on function public.ui_configuration_revert(uuid, text, text) to authenticated;
grant execute on function public.ui_configuration_effective_global_home() to authenticated;
