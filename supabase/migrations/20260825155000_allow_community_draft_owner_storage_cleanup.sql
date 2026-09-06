-- Community draft media is private to its authenticated author. The Storage API requires
-- selection of the target object before it can remove that object, so this narrow policy
-- permits the author to preview and clean up media only while the parent post is DRAFT.
-- It intentionally does not allow anonymous access, cross-user access, staff bypasses,
-- or reads from any non-draft post that is not otherwise eligible for media visibility.

create policy community_storage_read_draft_owner on storage.objects
for select to authenticated
using (
  bucket_id = 'rtc-community-media'
  and (storage.foldername(name))[1] = auth.uid()::text
  and exists (
    select 1
    from public.community_posts p
    where p.id::text = (storage.foldername(objects.name))[2]
      and p.author_id = auth.uid()
      and p.state = 'DRAFT'
  )
);
