BEGIN;

CREATE OR REPLACE FUNCTION public.daily_post_storage_post_id(p_name text)
RETURNS uuid
LANGUAGE plpgsql
IMMUTABLE
SET search_path = public
AS $$
DECLARE first_segment text;
BEGIN
    first_segment := split_part(COALESCE(p_name, ''), '/', 1);
    IF first_segment ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$' THEN
        RETURN first_segment::uuid;
    END IF;
    RETURN NULL;
END;
$$;

DROP POLICY IF EXISTS daily_post_media_read ON storage.objects;
CREATE POLICY daily_post_media_read ON storage.objects
FOR SELECT TO anon, authenticated
USING (
    bucket_id = 'daily-post-media'
    AND public.daily_post_storage_post_id(name) IS NOT NULL
    AND EXISTS (
        SELECT 1 FROM public.daily_posts p
        WHERE p.id = public.daily_post_storage_post_id(name)
          AND (
            (p.state = 'PUBLISHED' AND p.published_at IS NOT NULL AND p.published_at <= now())
            OR (auth.uid() IS NOT NULL AND public.daily_post_can_edit())
          )
    )
);

DROP POLICY IF EXISTS daily_post_media_editor_insert ON storage.objects;
CREATE POLICY daily_post_media_editor_insert ON storage.objects
FOR INSERT TO authenticated
WITH CHECK (
    bucket_id = 'daily-post-media'
    AND public.daily_post_can_edit()
    AND public.daily_post_storage_post_id(name) IS NOT NULL
    AND EXISTS (
        SELECT 1 FROM public.daily_posts p
        WHERE p.id = public.daily_post_storage_post_id(name)
          AND p.state IN ('DRAFT','SCHEDULED')
    )
);

DROP POLICY IF EXISTS daily_post_media_editor_update ON storage.objects;
CREATE POLICY daily_post_media_editor_update ON storage.objects
FOR UPDATE TO authenticated
USING (
    bucket_id = 'daily-post-media'
    AND public.daily_post_can_edit()
    AND public.daily_post_storage_post_id(name) IS NOT NULL
)
WITH CHECK (
    bucket_id = 'daily-post-media'
    AND public.daily_post_can_edit()
    AND public.daily_post_storage_post_id(name) IS NOT NULL
    AND EXISTS (
        SELECT 1 FROM public.daily_posts p
        WHERE p.id = public.daily_post_storage_post_id(name)
          AND p.state IN ('DRAFT','SCHEDULED')
    )
);

DROP POLICY IF EXISTS daily_post_media_editor_delete ON storage.objects;
CREATE POLICY daily_post_media_editor_delete ON storage.objects
FOR DELETE TO authenticated
USING (
    bucket_id = 'daily-post-media'
    AND public.daily_post_can_edit()
    AND public.daily_post_storage_post_id(name) IS NOT NULL
    AND EXISTS (
        SELECT 1 FROM public.daily_posts p
        WHERE p.id = public.daily_post_storage_post_id(name)
          AND p.state IN ('DRAFT','SCHEDULED')
    )
);

-- AI narration objects are intentionally inaccessible through client RLS.
-- The trusted Edge Function creates short-lived signed URLs with service-role authority.
DROP POLICY IF EXISTS daily_post_ai_audio_client_read ON storage.objects;
DROP POLICY IF EXISTS daily_post_ai_audio_client_write ON storage.objects;

COMMIT;
