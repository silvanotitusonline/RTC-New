BEGIN;

CREATE OR REPLACE FUNCTION public.daily_post_has_role(p_roles text[])
RETURNS boolean
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT auth.uid() IS NOT NULL AND EXISTS (
        SELECT 1 FROM public.user_roles ur
        WHERE ur.user_id = auth.uid() AND ur.role = ANY(p_roles)
    );
$$;
REVOKE ALL ON FUNCTION public.daily_post_has_role(text[]) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.daily_post_has_role(text[]) TO authenticated;

CREATE OR REPLACE FUNCTION public.daily_post_can_edit()
RETURNS boolean
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT public.daily_post_has_role(ARRAY['CONTENT_EDITOR','SYSTEM_ADMIN']::text[]);
$$;
REVOKE ALL ON FUNCTION public.daily_post_can_edit() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.daily_post_can_edit() TO authenticated;

CREATE OR REPLACE FUNCTION public.daily_post_can_moderate()
RETURNS boolean
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT public.daily_post_has_role(ARRAY['MODERATOR','SYSTEM_ADMIN']::text[]);
$$;
REVOKE ALL ON FUNCTION public.daily_post_can_moderate() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.daily_post_can_moderate() TO authenticated;

CREATE OR REPLACE FUNCTION public.daily_post_require_editor()
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    is_admin boolean;
BEGIN
    IF auth.uid() IS NULL OR NOT public.daily_post_can_edit() THEN
        RAISE EXCEPTION 'Not authorised';
    END IF;
    SELECT EXISTS(
        SELECT 1 FROM public.user_roles WHERE user_id = auth.uid() AND role = 'SYSTEM_ADMIN'
    ) INTO is_admin;
    IF is_admin AND COALESCE(auth.jwt()->>'aal', 'aal1') <> 'aal2' THEN
        RAISE EXCEPTION 'Administrator MFA required';
    END IF;
END;
$$;
REVOKE ALL ON FUNCTION public.daily_post_require_editor() FROM PUBLIC;

CREATE OR REPLACE FUNCTION public.daily_post_validate_blocks(p_blocks jsonb)
RETURNS boolean
LANGUAGE plpgsql
IMMUTABLE
SET search_path = public
AS $$
DECLARE
    block jsonb;
    block_type text;
BEGIN
    IF jsonb_typeof(p_blocks) <> 'array' OR jsonb_array_length(p_blocks) > 40 OR pg_column_size(p_blocks) > 131072 THEN
        RETURN false;
    END IF;
    FOR block IN SELECT value FROM jsonb_array_elements(p_blocks) LOOP
        IF jsonb_typeof(block) <> 'object' THEN RETURN false; END IF;
        block_type := block->>'type';
        IF block_type IS NULL OR block_type NOT IN (
            'HEADLINE','PARAGRAPH','SUBHEADING','IMAGE','VIDEO','MEDIA_GALLERY',
            'PULL_QUOTE','INFO_CALLOUT','DIVIDER','QUOTED_PUBLICATION','CTA'
        ) THEN RETURN false; END IF;
        IF char_length(COALESCE(block->>'id','')) NOT BETWEEN 1 AND 100 THEN RETURN false; END IF;
        IF char_length(COALESCE(block->>'text','')) > 12000 THEN RETURN false; END IF;
        IF block ? 'actionUrl' AND char_length(COALESCE(block->>'actionUrl','')) > 2048 THEN RETURN false; END IF;
    END LOOP;
    RETURN true;
END;
$$;

ALTER TABLE public.daily_posts DROP CONSTRAINT IF EXISTS daily_posts_blocks_valid;
ALTER TABLE public.daily_posts ADD CONSTRAINT daily_posts_blocks_valid CHECK (public.daily_post_validate_blocks(content_blocks));

DROP POLICY IF EXISTS daily_posts_public_read ON public.daily_posts;
CREATE POLICY daily_posts_public_read ON public.daily_posts
FOR SELECT TO anon, authenticated
USING (state = 'PUBLISHED' AND published_at IS NOT NULL AND published_at <= now());

DROP POLICY IF EXISTS daily_posts_editor_read ON public.daily_posts;
CREATE POLICY daily_posts_editor_read ON public.daily_posts
FOR SELECT TO authenticated
USING (public.daily_post_can_edit());

DROP POLICY IF EXISTS daily_posts_editor_insert ON public.daily_posts;
CREATE POLICY daily_posts_editor_insert ON public.daily_posts
FOR INSERT TO authenticated
WITH CHECK (public.daily_post_can_edit() AND author_id = auth.uid() AND state = 'DRAFT');

DROP POLICY IF EXISTS daily_posts_editor_update ON public.daily_posts;
CREATE POLICY daily_posts_editor_update ON public.daily_posts
FOR UPDATE TO authenticated
USING (public.daily_post_can_edit())
WITH CHECK (public.daily_post_can_edit());

DROP POLICY IF EXISTS daily_post_media_public_read ON public.daily_post_media;
CREATE POLICY daily_post_media_public_read ON public.daily_post_media
FOR SELECT TO anon, authenticated
USING (EXISTS (SELECT 1 FROM public.daily_posts p WHERE p.id = post_id AND p.state = 'PUBLISHED'));
DROP POLICY IF EXISTS daily_post_media_editor_all ON public.daily_post_media;
CREATE POLICY daily_post_media_editor_all ON public.daily_post_media
FOR ALL TO authenticated
USING (public.daily_post_can_edit()) WITH CHECK (public.daily_post_can_edit());

DROP POLICY IF EXISTS daily_post_comments_public_read ON public.daily_post_comments;
CREATE POLICY daily_post_comments_public_read ON public.daily_post_comments
FOR SELECT TO anon, authenticated
USING (
    state = 'VISIBLE' AND EXISTS (
        SELECT 1 FROM public.daily_posts p WHERE p.id = post_id AND p.state = 'PUBLISHED'
    )
);
DROP POLICY IF EXISTS daily_post_comments_owner_read ON public.daily_post_comments;
CREATE POLICY daily_post_comments_owner_read ON public.daily_post_comments
FOR SELECT TO authenticated USING (author_id = auth.uid() OR public.daily_post_can_moderate());

DROP POLICY IF EXISTS daily_post_preview_own_read ON public.daily_post_preview_receipts;
CREATE POLICY daily_post_preview_own_read ON public.daily_post_preview_receipts
FOR SELECT TO authenticated USING (user_id = auth.uid());

DROP POLICY IF EXISTS daily_post_translations_public_read ON public.daily_post_translations;
CREATE POLICY daily_post_translations_public_read ON public.daily_post_translations
FOR SELECT TO anon, authenticated
USING (EXISTS (SELECT 1 FROM public.daily_posts p WHERE p.id = post_id AND p.state = 'PUBLISHED'));
DROP POLICY IF EXISTS daily_post_translations_editor_read ON public.daily_post_translations;
CREATE POLICY daily_post_translations_editor_read ON public.daily_post_translations
FOR SELECT TO authenticated USING (public.daily_post_can_edit());

DROP POLICY IF EXISTS daily_post_jobs_editor_read ON public.daily_post_publication_jobs;
CREATE POLICY daily_post_jobs_editor_read ON public.daily_post_publication_jobs
FOR SELECT TO authenticated USING (public.daily_post_can_edit());
DROP POLICY IF EXISTS daily_post_audit_editor_read ON public.daily_post_audit_events;
CREATE POLICY daily_post_audit_editor_read ON public.daily_post_audit_events
FOR SELECT TO authenticated USING (public.daily_post_can_edit() OR public.daily_post_can_moderate());

CREATE OR REPLACE FUNCTION public.daily_post_comment_create_v1(
    p_post_id uuid,
    p_body text,
    p_parent_id uuid DEFAULT NULL
)
RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    new_id uuid;
    recent_count integer;
BEGIN
    IF auth.uid() IS NULL THEN RAISE EXCEPTION 'Authentication required'; END IF;
    IF NOT EXISTS (SELECT 1 FROM public.daily_posts WHERE id = p_post_id AND state = 'PUBLISHED') THEN
        RAISE EXCEPTION 'Publication unavailable';
    END IF;
    p_body := btrim(p_body);
    IF char_length(p_body) NOT BETWEEN 1 AND 2000 THEN RAISE EXCEPTION 'Invalid comment length'; END IF;
    SELECT count(*) INTO recent_count FROM public.daily_post_comments
      WHERE author_id = auth.uid() AND created_at > now() - interval '1 minute';
    IF recent_count >= 12 THEN RAISE EXCEPTION 'Comment rate limit reached'; END IF;
    INSERT INTO public.daily_post_comments(post_id, author_id, parent_id, body)
    VALUES (p_post_id, auth.uid(), p_parent_id, p_body)
    RETURNING id INTO new_id;
    RETURN new_id;
END;
$$;

CREATE OR REPLACE FUNCTION public.daily_post_comment_update_v1(p_comment_id uuid, p_body text)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
    IF auth.uid() IS NULL THEN RAISE EXCEPTION 'Authentication required'; END IF;
    p_body := btrim(p_body);
    IF char_length(p_body) NOT BETWEEN 1 AND 2000 THEN RAISE EXCEPTION 'Invalid comment length'; END IF;
    UPDATE public.daily_post_comments
       SET body = p_body, updated_at = now()
     WHERE id = p_comment_id AND author_id = auth.uid() AND state = 'VISIBLE';
    IF NOT FOUND THEN RAISE EXCEPTION 'Comment unavailable'; END IF;
END;
$$;

CREATE OR REPLACE FUNCTION public.daily_post_comment_delete_v1(p_comment_id uuid)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
    IF auth.uid() IS NULL THEN RAISE EXCEPTION 'Authentication required'; END IF;
    UPDATE public.daily_post_comments
       SET body = '', state = 'DELETED', updated_at = now()
     WHERE id = p_comment_id AND (author_id = auth.uid() OR public.daily_post_can_moderate());
    IF NOT FOUND THEN RAISE EXCEPTION 'Comment unavailable'; END IF;
END;
$$;

CREATE OR REPLACE FUNCTION public.daily_post_comment_moderate_v1(p_comment_id uuid, p_reason text)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
    IF NOT public.daily_post_can_moderate() THEN RAISE EXCEPTION 'Not authorised'; END IF;
    p_reason := btrim(p_reason);
    IF char_length(p_reason) NOT BETWEEN 3 AND 500 THEN RAISE EXCEPTION 'Moderation reason required'; END IF;
    UPDATE public.daily_post_comments SET state = 'HIDDEN', moderation_reason = p_reason, updated_at = now() WHERE id = p_comment_id;
    IF NOT FOUND THEN RAISE EXCEPTION 'Comment unavailable'; END IF;
    INSERT INTO public.daily_post_audit_events(actor_id, post_id, event_type, result, metadata)
    SELECT auth.uid(), post_id, 'COMMENT_MODERATED', 'ALLOWED', jsonb_build_object('commentId', id, 'reason', p_reason)
      FROM public.daily_post_comments WHERE id = p_comment_id;
END;
$$;

CREATE OR REPLACE FUNCTION public.daily_post_preview_next_v1()
RETURNS public.daily_posts
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT p
    FROM public.daily_posts p
    WHERE auth.uid() IS NOT NULL
      AND p.state = 'PUBLISHED'
      AND p.preview_popup_enabled
      AND p.published_at IS NOT NULL
      AND p.published_at > now() - interval '14 days'
      AND NOT EXISTS (
          SELECT 1 FROM public.daily_post_preview_receipts r
          WHERE r.post_id = p.id AND r.user_id = auth.uid()
      )
    ORDER BY (p.publication_type = 'BREAKING') DESC, p.published_at DESC, p.id DESC
    LIMIT 1;
$$;

CREATE OR REPLACE FUNCTION public.daily_post_preview_mark_v1(p_post_id uuid)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
    IF auth.uid() IS NULL THEN RAISE EXCEPTION 'Authentication required'; END IF;
    IF NOT EXISTS (SELECT 1 FROM public.daily_posts WHERE id = p_post_id AND state = 'PUBLISHED' AND preview_popup_enabled) THEN
        RAISE EXCEPTION 'Publication unavailable';
    END IF;
    INSERT INTO public.daily_post_preview_receipts(post_id, user_id)
    VALUES (p_post_id, auth.uid()) ON CONFLICT (post_id, user_id) DO NOTHING;
END;
$$;

CREATE OR REPLACE FUNCTION public.daily_post_admin_page_v1(p_state text DEFAULT NULL, p_limit integer DEFAULT 100)
RETURNS SETOF public.daily_posts
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
    PERFORM public.daily_post_require_editor();
    IF p_state IS NOT NULL AND p_state NOT IN ('DRAFT','SCHEDULED','PUBLISHED','ARCHIVED') THEN RAISE EXCEPTION 'Invalid state'; END IF;
    RETURN QUERY SELECT p.* FROM public.daily_posts p
      WHERE p_state IS NULL OR p.state = p_state
      ORDER BY p.updated_at DESC, p.id DESC
      LIMIT LEAST(GREATEST(p_limit,1),200);
END;
$$;

CREATE OR REPLACE FUNCTION public.daily_post_save_draft_v1(
    p_post_id uuid DEFAULT NULL,
    p_publication_type text DEFAULT 'NEWS',
    p_template_key text DEFAULT 'standard_news',
    p_canonical_language text DEFAULT 'en',
    p_headline text DEFAULT '',
    p_excerpt text DEFAULT '',
    p_content_blocks jsonb DEFAULT '[]'::jsonb,
    p_quoted_post_id uuid DEFAULT NULL,
    p_push_enabled boolean DEFAULT false,
    p_preview_popup_enabled boolean DEFAULT true
)
RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE target_id uuid;
BEGIN
    PERFORM public.daily_post_require_editor();
    p_headline := btrim(p_headline); p_excerpt := btrim(p_excerpt); p_template_key := btrim(p_template_key);
    IF p_publication_type NOT IN ('NEWS','BREAKING') THEN RAISE EXCEPTION 'Invalid publication type'; END IF;
    IF char_length(p_template_key) NOT BETWEEN 1 AND 80 OR char_length(p_headline) > 180 OR char_length(p_excerpt) > 600 THEN RAISE EXCEPTION 'Invalid publication fields'; END IF;
    IF p_canonical_language !~ '^[a-z]{2,3}(-[A-Z]{2})?$' THEN RAISE EXCEPTION 'Invalid language'; END IF;
    IF NOT public.daily_post_validate_blocks(p_content_blocks) THEN RAISE EXCEPTION 'Invalid content blocks'; END IF;
    IF p_quoted_post_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM public.daily_posts WHERE id = p_quoted_post_id AND state IN ('PUBLISHED','ARCHIVED')) THEN RAISE EXCEPTION 'Quoted publication unavailable'; END IF;

    IF p_post_id IS NULL THEN
        INSERT INTO public.daily_posts(author_id, publication_type, template_key, canonical_language, headline, excerpt, content_blocks, quoted_post_id, push_enabled, preview_popup_enabled)
        VALUES(auth.uid(), p_publication_type, p_template_key, p_canonical_language, p_headline, p_excerpt, p_content_blocks, p_quoted_post_id, p_push_enabled, p_preview_popup_enabled)
        RETURNING id INTO target_id;
    ELSE
        UPDATE public.daily_posts SET publication_type=p_publication_type, template_key=p_template_key,
            canonical_language=p_canonical_language, headline=p_headline, excerpt=p_excerpt,
            content_blocks=p_content_blocks, quoted_post_id=p_quoted_post_id,
            push_enabled=p_push_enabled, preview_popup_enabled=p_preview_popup_enabled
        WHERE id=p_post_id AND state IN ('DRAFT','SCHEDULED')
        RETURNING id INTO target_id;
        IF target_id IS NULL THEN RAISE EXCEPTION 'Draft unavailable'; END IF;
        UPDATE public.daily_post_publication_jobs SET state='CANCELLED', completed_at=now()
          WHERE post_id=target_id AND state IN ('PENDING','CLAIMED');
        UPDATE public.daily_posts SET state='DRAFT', scheduled_for=NULL WHERE id=target_id;
    END IF;
    INSERT INTO public.daily_post_audit_events(actor_id, post_id, event_type, result)
    VALUES(auth.uid(), target_id, 'DRAFT_SAVED', 'ALLOWED');
    RETURN target_id;
END;
$$;

CREATE OR REPLACE FUNCTION public.daily_post_publish_v1(
    p_post_id uuid,
    p_mode text DEFAULT 'NOW',
    p_scheduled_for timestamptz DEFAULT NULL,
    p_push_enabled boolean DEFAULT false,
    p_preview_popup_enabled boolean DEFAULT true
)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE rev integer; dispatch text;
BEGIN
    PERFORM public.daily_post_require_editor();
    IF p_mode NOT IN ('NOW','SCHEDULE') THEN RAISE EXCEPTION 'Invalid publish mode'; END IF;
    SELECT revision INTO rev FROM public.daily_posts
      WHERE id=p_post_id AND state IN ('DRAFT','SCHEDULED') AND char_length(btrim(headline))>0 AND jsonb_array_length(content_blocks)>0
      FOR UPDATE;
    IF rev IS NULL THEN RAISE EXCEPTION 'Publication is incomplete or unavailable'; END IF;
    UPDATE public.daily_post_publication_jobs SET state='CANCELLED', completed_at=now()
      WHERE post_id=p_post_id AND state IN ('PENDING','CLAIMED');

    IF p_mode='SCHEDULE' THEN
        IF p_scheduled_for IS NULL OR p_scheduled_for <= now() + interval '1 minute' THEN RAISE EXCEPTION 'Schedule must be in the future'; END IF;
        UPDATE public.daily_posts SET state='SCHEDULED', scheduled_for=p_scheduled_for, published_at=NULL,
            push_enabled=p_push_enabled, preview_popup_enabled=p_preview_popup_enabled WHERE id=p_post_id;
        dispatch := 'daily-post:'||p_post_id::text||':'||rev::text||':'||extract(epoch from p_scheduled_for)::bigint::text;
        INSERT INTO public.daily_post_publication_jobs(post_id,run_at,push_enabled,dispatch_key)
        VALUES(p_post_id,p_scheduled_for,p_push_enabled,dispatch);
        INSERT INTO public.daily_post_audit_events(actor_id,post_id,event_type,result,metadata)
        VALUES(auth.uid(),p_post_id,'PUBLICATION_SCHEDULED','ALLOWED',jsonb_build_object('scheduledFor',p_scheduled_for,'push',p_push_enabled));
    ELSE
        UPDATE public.daily_posts SET state='PUBLISHED', scheduled_for=NULL, published_at=now(), published_by=auth.uid(),
            push_enabled=p_push_enabled, preview_popup_enabled=p_preview_popup_enabled WHERE id=p_post_id;
        IF p_push_enabled THEN
            dispatch := 'daily-post:'||p_post_id::text||':'||rev::text||':now';
            INSERT INTO public.daily_post_publication_jobs(post_id,run_at,push_enabled,dispatch_key)
            VALUES(p_post_id,now(),true,dispatch);
        END IF;
        INSERT INTO public.daily_post_audit_events(actor_id,post_id,event_type,result,metadata)
        VALUES(auth.uid(),p_post_id,'PUBLICATION_PUBLISHED','ALLOWED',jsonb_build_object('push',p_push_enabled));
    END IF;
END;
$$;

CREATE OR REPLACE FUNCTION public.daily_post_archive_v1(p_post_id uuid)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
    PERFORM public.daily_post_require_editor();
    UPDATE public.daily_posts SET state='ARCHIVED', scheduled_for=NULL WHERE id=p_post_id AND state='PUBLISHED';
    IF NOT FOUND THEN RAISE EXCEPTION 'Published item unavailable'; END IF;
    UPDATE public.daily_post_publication_jobs SET state='CANCELLED',completed_at=now() WHERE post_id=p_post_id AND state IN ('PENDING','CLAIMED');
    INSERT INTO public.daily_post_audit_events(actor_id,post_id,event_type,result) VALUES(auth.uid(),p_post_id,'PUBLICATION_ARCHIVED','ALLOWED');
END;
$$;

CREATE OR REPLACE FUNCTION public.daily_post_claim_due_jobs_v1(p_limit integer DEFAULT 20)
RETURNS TABLE(job_id uuid, post_id uuid, push_enabled boolean, dispatch_key text)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
    IF auth.role() <> 'service_role' THEN RAISE EXCEPTION 'Service role required'; END IF;
    RETURN QUERY
    WITH due AS (
        SELECT j.id FROM public.daily_post_publication_jobs j
        WHERE j.state='PENDING' AND j.run_at <= now()
        ORDER BY j.run_at,j.id FOR UPDATE SKIP LOCKED LIMIT LEAST(GREATEST(p_limit,1),50)
    ), claimed AS (
        UPDATE public.daily_post_publication_jobs j SET state='CLAIMED',claimed_at=now(),attempts=attempts+1
        FROM due WHERE j.id=due.id RETURNING j.id,j.post_id,j.push_enabled,j.dispatch_key
    ) SELECT id,claimed.post_id,claimed.push_enabled,claimed.dispatch_key FROM claimed;
END;
$$;

CREATE OR REPLACE FUNCTION public.daily_post_execute_job_v1(p_job_id uuid)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE j public.daily_post_publication_jobs%ROWTYPE; p public.daily_posts%ROWTYPE;
BEGIN
    IF auth.role() <> 'service_role' THEN RAISE EXCEPTION 'Service role required'; END IF;
    SELECT * INTO j FROM public.daily_post_publication_jobs WHERE id=p_job_id AND state='CLAIMED' FOR UPDATE;
    IF j.id IS NULL THEN RAISE EXCEPTION 'Job unavailable'; END IF;
    SELECT * INTO p FROM public.daily_posts WHERE id=j.post_id FOR UPDATE;
    IF p.id IS NULL OR p.state='ARCHIVED' THEN RAISE EXCEPTION 'Publication unavailable'; END IF;
    IF p.state='SCHEDULED' THEN
        UPDATE public.daily_posts SET state='PUBLISHED',published_at=now(),scheduled_for=NULL WHERE id=p.id RETURNING * INTO p;
    END IF;
    RETURN jsonb_build_object('jobId',j.id,'postId',p.id,'headline',p.headline,'excerpt',p.excerpt,
        'publicationType',p.publication_type,'pushEnabled',j.push_enabled,'dispatchKey',j.dispatch_key,'revision',p.revision);
END;
$$;

CREATE OR REPLACE FUNCTION public.daily_post_complete_job_v1(p_job_id uuid, p_success boolean, p_error text DEFAULT NULL)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE p_id uuid;
BEGIN
    IF auth.role() <> 'service_role' THEN RAISE EXCEPTION 'Service role required'; END IF;
    UPDATE public.daily_post_publication_jobs SET state=CASE WHEN p_success THEN 'DONE' ELSE 'FAILED' END,
        completed_at=now(),last_error=CASE WHEN p_success THEN NULL ELSE left(COALESCE(p_error,'Unknown error'),2000) END
      WHERE id=p_job_id AND state='CLAIMED' RETURNING post_id INTO p_id;
    IF p_id IS NOT NULL THEN INSERT INTO public.daily_post_audit_events(post_id,event_type,result,metadata)
      VALUES(p_id,'PUBLICATION_JOB',CASE WHEN p_success THEN 'ALLOWED' ELSE 'FAILED' END,jsonb_build_object('jobId',p_job_id,'error',p_error)); END IF;
END;
$$;

GRANT EXECUTE ON FUNCTION public.daily_post_comment_create_v1(uuid,text,uuid) TO authenticated;
GRANT EXECUTE ON FUNCTION public.daily_post_comment_update_v1(uuid,text) TO authenticated;
GRANT EXECUTE ON FUNCTION public.daily_post_comment_delete_v1(uuid) TO authenticated;
GRANT EXECUTE ON FUNCTION public.daily_post_comment_moderate_v1(uuid,text) TO authenticated;
GRANT EXECUTE ON FUNCTION public.daily_post_preview_next_v1() TO authenticated;
GRANT EXECUTE ON FUNCTION public.daily_post_preview_mark_v1(uuid) TO authenticated;
GRANT EXECUTE ON FUNCTION public.daily_post_admin_page_v1(text,integer) TO authenticated;
GRANT EXECUTE ON FUNCTION public.daily_post_save_draft_v1(uuid,text,text,text,text,text,jsonb,uuid,boolean,boolean) TO authenticated;
GRANT EXECUTE ON FUNCTION public.daily_post_publish_v1(uuid,text,timestamptz,boolean,boolean) TO authenticated;
GRANT EXECUTE ON FUNCTION public.daily_post_archive_v1(uuid) TO authenticated;
REVOKE ALL ON FUNCTION public.daily_post_claim_due_jobs_v1(integer) FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.daily_post_execute_job_v1(uuid) FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.daily_post_complete_job_v1(uuid,boolean,text) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.daily_post_claim_due_jobs_v1(integer) TO service_role;
GRANT EXECUTE ON FUNCTION public.daily_post_execute_job_v1(uuid) TO service_role;
GRANT EXECUTE ON FUNCTION public.daily_post_complete_job_v1(uuid,boolean,text) TO service_role;

COMMIT;
