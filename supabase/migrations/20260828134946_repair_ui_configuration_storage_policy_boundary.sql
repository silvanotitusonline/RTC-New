-- Keep Brand image Storage policies compatible with RPC-only UI configuration tables.
-- The helper functions retain server-side access to private tables without granting direct
-- SELECT to authenticated clients. Production deployment remains separately authorized.

create or replace function private.ui_configuration_can_insert_asset_object(p_object_path text)
returns boolean
language sql
stable
security definer
set search_path = auth, public, pg_temp
as $$
  select
    auth.uid() is not null
    and coalesce(auth.jwt() ->> 'aal', 'aal1') = 'aal2'
    and private.has_role('SYSTEM_ADMIN'::public.app_role)
    and exists (
      select 1
      from public.ui_configuration_assets a
      where a.object_path = p_object_path
        and a.created_by = auth.uid()
        and a.state = 'STAGED'
    );
$$;

create or replace function private.ui_configuration_can_select_asset_object(p_object_path text)
returns boolean
language sql
stable
security definer
set search_path = auth, public, pg_temp
as $$
  select
    auth.uid() is not null
    and exists (
      select 1
      from public.ui_configuration_assets a
      where a.object_path = p_object_path
        and a.state = 'FINALIZED'
        and (
          (
            private.has_role('SYSTEM_ADMIN'::public.app_role)
            and coalesce(auth.jwt() ->> 'aal', 'aal1') = 'aal2'
          )
          or exists (
            select 1
            from public.ui_configuration_versions v
            where v.audience_key = 'RESIDENT_GLOBAL'
              and v.state = 'PUBLISHED'
              and (
                private.ui_configuration_upgrade_to_v2(v.configuration)
                -> 'home' -> 'imageWidget' ->> 'assetId'
              ) = a.id::text
          )
        )
    );
$$;

create or replace function private.ui_configuration_can_delete_asset_object(p_object_path text)
returns boolean
language sql
stable
security definer
set search_path = auth, public, pg_temp
as $$
  select
    auth.uid() is not null
    and coalesce(auth.jwt() ->> 'aal', 'aal1') = 'aal2'
    and private.has_role('SYSTEM_ADMIN'::public.app_role)
    and exists (
      select 1
      from public.ui_configuration_assets a
      where a.object_path = p_object_path
        and a.created_by = auth.uid()
        and a.state in ('STAGED', 'FINALIZED')
        and not exists (
          select 1
          from public.ui_configuration_versions v
          where v.audience_key = 'RESIDENT_GLOBAL'
            and v.state = 'PUBLISHED'
            and (
              private.ui_configuration_upgrade_to_v2(v.configuration)
              -> 'home' -> 'imageWidget' ->> 'assetId'
            ) = a.id::text
        )
    );
$$;

revoke all on function private.ui_configuration_can_insert_asset_object(text)
  from public, anon, authenticated, service_role;
revoke all on function private.ui_configuration_can_select_asset_object(text)
  from public, anon, authenticated, service_role;
revoke all on function private.ui_configuration_can_delete_asset_object(text)
  from public, anon, authenticated, service_role;
grant execute on function private.ui_configuration_can_insert_asset_object(text) to authenticated;
grant execute on function private.ui_configuration_can_select_asset_object(text) to authenticated;
grant execute on function private.ui_configuration_can_delete_asset_object(text) to authenticated;

drop policy if exists rtc_ui_assets_insert on storage.objects;
create policy rtc_ui_assets_insert on storage.objects
for insert to authenticated
with check (private.ui_configuration_can_insert_asset_object(name));

drop policy if exists rtc_ui_assets_select on storage.objects;
create policy rtc_ui_assets_select on storage.objects
for select to authenticated
using (private.ui_configuration_can_select_asset_object(name));

drop policy if exists rtc_ui_assets_delete on storage.objects;
create policy rtc_ui_assets_delete on storage.objects
for delete to authenticated
using (private.ui_configuration_can_delete_asset_object(name));

-- Every authenticated SELECT policy is planned together. Replace the feedback policy's revoked
-- wrapper with the already-authorized, equivalent role predicate so unrelated Storage buckets do
-- not fail planning before the bucket-specific predicate is evaluated.
drop policy if exists feedback_media_select_owner_or_triage on storage.objects;
create policy feedback_media_select_owner_or_triage on storage.objects
for select to authenticated
using (
  bucket_id = 'rtc-feedback-media'
  and (
    (storage.foldername(name))[1] = (select auth.uid())::text
    or private.has_any_role(array['CONTENT_EDITOR', 'SYSTEM_ADMIN']::public.app_role[])
  )
);
