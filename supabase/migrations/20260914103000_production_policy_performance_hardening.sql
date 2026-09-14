BEGIN;

-- Evaluate caller identity and role helpers once per statement rather than once
-- per row, and avoid overlapping permissive SELECT policies on Daily Post tables.
DROP POLICY IF EXISTS bookmarks_own ON public.community_bookmarks;
CREATE POLICY bookmarks_own ON public.community_bookmarks
FOR ALL TO authenticated
USING (user_id = (SELECT auth.uid()))
WITH CHECK (user_id = (SELECT auth.uid()));

DROP POLICY IF EXISTS reposts_own ON public.community_reposts;
CREATE POLICY reposts_own ON public.community_reposts
FOR ALL TO authenticated
USING (user_id = (SELECT auth.uid()))
WITH CHECK (user_id = (SELECT auth.uid()));

DROP POLICY IF EXISTS daily_post_preview_own_read ON public.daily_post_preview_receipts;
CREATE POLICY daily_post_preview_own_read ON public.daily_post_preview_receipts
FOR SELECT TO authenticated
USING (user_id = (SELECT auth.uid()));

DROP POLICY IF EXISTS daily_posts_editor_insert ON public.daily_posts;
CREATE POLICY daily_posts_editor_insert ON public.daily_posts
FOR INSERT TO authenticated
WITH CHECK (
  (SELECT public.daily_post_can_edit())
  AND author_id = (SELECT auth.uid())
  AND state = 'DRAFT'
);

DROP POLICY IF EXISTS daily_post_comments_owner_read ON public.daily_post_comments;
DROP POLICY IF EXISTS daily_post_comments_public_read ON public.daily_post_comments;
CREATE POLICY daily_post_comments_public_read ON public.daily_post_comments
FOR SELECT TO anon
USING (
  state = 'VISIBLE'
  AND EXISTS (
    SELECT 1 FROM public.daily_posts p
    WHERE p.id = daily_post_comments.post_id
      AND p.state = 'PUBLISHED'
  )
);
CREATE POLICY daily_post_comments_authenticated_read ON public.daily_post_comments
FOR SELECT TO authenticated
USING (
  (
    state = 'VISIBLE'
    AND EXISTS (
      SELECT 1 FROM public.daily_posts p
      WHERE p.id = daily_post_comments.post_id
        AND p.state = 'PUBLISHED'
    )
  )
  OR author_id = (SELECT auth.uid())
  OR (SELECT public.daily_post_can_moderate())
);

DROP POLICY IF EXISTS daily_post_media_editor_all ON public.daily_post_media;
DROP POLICY IF EXISTS daily_post_media_public_read ON public.daily_post_media;
CREATE POLICY daily_post_media_public_read ON public.daily_post_media
FOR SELECT TO anon
USING (
  EXISTS (
    SELECT 1 FROM public.daily_posts p
    WHERE p.id = daily_post_media.post_id
      AND p.state = 'PUBLISHED'
  )
);
CREATE POLICY daily_post_media_authenticated_read ON public.daily_post_media
FOR SELECT TO authenticated
USING (
  EXISTS (
    SELECT 1 FROM public.daily_posts p
    WHERE p.id = daily_post_media.post_id
      AND p.state = 'PUBLISHED'
  )
  OR (SELECT public.daily_post_can_edit())
);
CREATE POLICY daily_post_media_editor_insert ON public.daily_post_media
FOR INSERT TO authenticated
WITH CHECK ((SELECT public.daily_post_can_edit()));
CREATE POLICY daily_post_media_editor_update ON public.daily_post_media
FOR UPDATE TO authenticated
USING ((SELECT public.daily_post_can_edit()))
WITH CHECK ((SELECT public.daily_post_can_edit()));
CREATE POLICY daily_post_media_editor_delete ON public.daily_post_media
FOR DELETE TO authenticated
USING ((SELECT public.daily_post_can_edit()));

DROP POLICY IF EXISTS daily_post_translations_editor_read ON public.daily_post_translations;
DROP POLICY IF EXISTS daily_post_translations_public_read ON public.daily_post_translations;
CREATE POLICY daily_post_translations_public_read ON public.daily_post_translations
FOR SELECT TO anon
USING (
  EXISTS (
    SELECT 1 FROM public.daily_posts p
    WHERE p.id = daily_post_translations.post_id
      AND p.state = 'PUBLISHED'
  )
);
CREATE POLICY daily_post_translations_authenticated_read ON public.daily_post_translations
FOR SELECT TO authenticated
USING (
  EXISTS (
    SELECT 1 FROM public.daily_posts p
    WHERE p.id = daily_post_translations.post_id
      AND p.state = 'PUBLISHED'
  )
  OR (SELECT public.daily_post_can_edit())
);

DROP POLICY IF EXISTS daily_posts_editor_read ON public.daily_posts;
DROP POLICY IF EXISTS daily_posts_public_read ON public.daily_posts;
CREATE POLICY daily_posts_public_read ON public.daily_posts
FOR SELECT TO anon
USING (
  state = 'PUBLISHED'
  AND published_at IS NOT NULL
  AND published_at <= now()
);
CREATE POLICY daily_posts_authenticated_read ON public.daily_posts
FOR SELECT TO authenticated
USING (
  (
    state = 'PUBLISHED'
    AND published_at IS NOT NULL
    AND published_at <= now()
  )
  OR (SELECT public.daily_post_can_edit())
);

-- Cover high-frequency foreign keys used by feed, Daily Post, notification,
-- event and messaging projections. These indexes are intentionally narrow.
CREATE INDEX IF NOT EXISTS community_bookmarks_post_id_idx
  ON public.community_bookmarks(post_id);
CREATE INDEX IF NOT EXISTS community_events_created_by_idx
  ON public.community_events(created_by);
CREATE INDEX IF NOT EXISTS community_posts_parent_id_idx
  ON public.community_posts(parent_id);
CREATE INDEX IF NOT EXISTS community_posts_original_post_id_idx
  ON public.community_posts(original_post_id);
CREATE INDEX IF NOT EXISTS daily_post_comments_author_id_idx
  ON public.daily_post_comments(author_id);
CREATE INDEX IF NOT EXISTS daily_posts_published_by_idx
  ON public.daily_posts(published_by);
CREATE INDEX IF NOT EXISTS daily_posts_quoted_post_id_idx
  ON public.daily_posts(quoted_post_id);
CREATE INDEX IF NOT EXISTS conversation_members_user_id_idx
  ON public.conversation_members(user_id);
CREATE INDEX IF NOT EXISTS messages_conversation_id_idx
  ON public.messages(conversation_id);
CREATE INDEX IF NOT EXISTS messages_sender_id_idx
  ON public.messages(sender_id);
CREATE INDEX IF NOT EXISTS notifications_user_id_idx
  ON public.notifications(user_id);
CREATE INDEX IF NOT EXISTS notifications_actor_id_idx
  ON public.notifications(actor_id);
CREATE INDEX IF NOT EXISTS notifications_post_id_idx
  ON public.notifications(post_id);
CREATE INDEX IF NOT EXISTS app_error_logs_user_id_idx
  ON public.app_error_logs(user_id);

COMMIT;
