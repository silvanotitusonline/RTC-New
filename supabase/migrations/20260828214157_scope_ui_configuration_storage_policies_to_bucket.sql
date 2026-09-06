-- Scope the Brand object policies to their dedicated bucket. The helpers remain
-- responsible for the authenticated administrator, ownership and published-state checks.
-- Production deployment remains separately authorized.

drop policy if exists rtc_ui_assets_insert on storage.objects;
create policy rtc_ui_assets_insert on storage.objects
for insert to authenticated
with check (
  bucket_id = 'rtc-ui-assets'
  and private.ui_configuration_can_insert_asset_object(name)
);

drop policy if exists rtc_ui_assets_select on storage.objects;
create policy rtc_ui_assets_select on storage.objects
for select to authenticated
using (
  bucket_id = 'rtc-ui-assets'
  and private.ui_configuration_can_select_asset_object(name)
);

drop policy if exists rtc_ui_assets_delete on storage.objects;
create policy rtc_ui_assets_delete on storage.objects
for delete to authenticated
using (
  bucket_id = 'rtc-ui-assets'
  and private.ui_configuration_can_delete_asset_object(name)
);
