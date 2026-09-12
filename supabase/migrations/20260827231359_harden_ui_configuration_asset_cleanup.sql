-- Complete private Brand & Experience Home-image cleanup in non-production.
-- A System Administrator with AAL2 may delete only their own staged/finalized UI asset,
-- and never while that asset is referenced by the effective published configuration.

drop policy if exists rtc_ui_assets_delete on storage.objects;
create policy rtc_ui_assets_delete on storage.objects
for delete to authenticated
using (
  bucket_id = 'rtc-ui-assets'
  and coalesce(auth.jwt() ->> 'aal', 'aal1') = 'aal2'
  and private.has_role('SYSTEM_ADMIN'::public.app_role)
  and exists (
    select 1
    from public.ui_configuration_assets a
    where a.object_path = name
      and a.created_by = auth.uid()
      and a.state in ('STAGED', 'FINALIZED')
      and not exists (
        select 1
        from public.ui_configuration_versions v
        where v.audience_key = 'RESIDENT_GLOBAL'
          and v.state = 'PUBLISHED'
          and (private.ui_configuration_upgrade_to_v2(v.configuration) -> 'home' -> 'imageWidget' ->> 'assetId') = a.id::text
      )
  )
);
