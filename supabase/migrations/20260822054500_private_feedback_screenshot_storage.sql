begin;

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values (
  'rtc-feedback-media',
  'rtc-feedback-media',
  false,
  5242880,
  array['image/jpeg', 'image/png', 'image/webp']::text[]
)
on conflict (id) do update
set public = false,
    file_size_limit = 5242880,
    allowed_mime_types = array['image/jpeg', 'image/png', 'image/webp']::text[];

drop policy if exists feedback_media_insert_owner on storage.objects;
drop policy if exists feedback_media_select_owner_or_triage on storage.objects;
drop policy if exists feedback_media_delete_owner on storage.objects;

create policy feedback_media_insert_owner
on storage.objects for insert to authenticated
with check (
  bucket_id = 'rtc-feedback-media'
  and (storage.foldername(name))[1] = auth.uid()::text
);

create policy feedback_media_select_owner_or_triage
on storage.objects for select to authenticated
using (
  bucket_id = 'rtc-feedback-media'
  and (
    (storage.foldername(name))[1] = auth.uid()::text
    or private.is_content_authority()
  )
);

create policy feedback_media_delete_owner
on storage.objects for delete to authenticated
using (
  bucket_id = 'rtc-feedback-media'
  and (storage.foldername(name))[1] = auth.uid()::text
);

commit;
