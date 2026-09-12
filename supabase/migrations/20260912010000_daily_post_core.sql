BEGIN;

CREATE TABLE IF NOT EXISTS public.daily_posts (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    author_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE RESTRICT,
    published_by uuid REFERENCES auth.users(id) ON DELETE SET NULL,
    state text NOT NULL DEFAULT 'DRAFT' CHECK (state IN ('DRAFT','SCHEDULED','PUBLISHED','ARCHIVED')),
    publication_type text NOT NULL DEFAULT 'NEWS' CHECK (publication_type IN ('NEWS','BREAKING')),
    template_key text NOT NULL DEFAULT 'standard_news' CHECK (char_length(template_key) BETWEEN 1 AND 80),
    canonical_language text NOT NULL DEFAULT 'en' CHECK (canonical_language ~ '^[a-z]{2,3}(-[A-Z]{2})?$'),
    headline text NOT NULL DEFAULT '' CHECK (char_length(headline) <= 180),
    excerpt text NOT NULL DEFAULT '' CHECK (char_length(excerpt) <= 600),
    content_blocks jsonb NOT NULL DEFAULT '[]'::jsonb CHECK (jsonb_typeof(content_blocks) = 'array'),
    quoted_post_id uuid REFERENCES public.daily_posts(id) ON DELETE SET NULL,
    scheduled_for timestamptz,
    published_at timestamptz,
    push_enabled boolean NOT NULL DEFAULT false,
    preview_popup_enabled boolean NOT NULL DEFAULT true,
    revision integer NOT NULL DEFAULT 1 CHECK (revision > 0),
    comment_count integer NOT NULL DEFAULT 0 CHECK (comment_count >= 0),
    metadata jsonb NOT NULL DEFAULT '{}'::jsonb CHECK (jsonb_typeof(metadata) = 'object'),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT daily_posts_schedule_consistency CHECK (
        (state = 'SCHEDULED' AND scheduled_for IS NOT NULL) OR
        (state <> 'SCHEDULED')
    ),
    CONSTRAINT daily_posts_publish_consistency CHECK (
        (state IN ('PUBLISHED','ARCHIVED') AND published_at IS NOT NULL) OR
        (state NOT IN ('PUBLISHED','ARCHIVED'))
    )
);

CREATE INDEX IF NOT EXISTS daily_posts_public_feed_idx
    ON public.daily_posts (published_at DESC, id DESC)
    WHERE state = 'PUBLISHED';
CREATE INDEX IF NOT EXISTS daily_posts_scheduled_idx
    ON public.daily_posts (scheduled_for, id)
    WHERE state = 'SCHEDULED';
CREATE INDEX IF NOT EXISTS daily_posts_author_idx ON public.daily_posts (author_id, updated_at DESC);

CREATE TABLE IF NOT EXISTS public.daily_post_media (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    post_id uuid NOT NULL REFERENCES public.daily_posts(id) ON DELETE CASCADE,
    storage_path text NOT NULL CHECK (char_length(storage_path) BETWEEN 1 AND 512),
    media_type text NOT NULL CHECK (media_type IN ('IMAGE','VIDEO','AUDIO')),
    mime_type text NOT NULL CHECK (char_length(mime_type) BETWEEN 3 AND 100),
    alt_text text NOT NULL DEFAULT '' CHECK (char_length(alt_text) <= 600),
    sort_order integer NOT NULL DEFAULT 0 CHECK (sort_order BETWEEN 0 AND 100),
    width integer CHECK (width IS NULL OR width BETWEEN 1 AND 16384),
    height integer CHECK (height IS NULL OR height BETWEEN 1 AND 16384),
    duration_ms bigint CHECK (duration_ms IS NULL OR duration_ms BETWEEN 0 AND 86400000),
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE(post_id, storage_path)
);
CREATE INDEX IF NOT EXISTS daily_post_media_post_idx ON public.daily_post_media(post_id, sort_order, id);

CREATE TABLE IF NOT EXISTS public.daily_post_comments (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    post_id uuid NOT NULL REFERENCES public.daily_posts(id) ON DELETE CASCADE,
    author_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    parent_id uuid REFERENCES public.daily_post_comments(id) ON DELETE CASCADE,
    depth smallint NOT NULL DEFAULT 0 CHECK (depth BETWEEN 0 AND 3),
    body text NOT NULL CHECK (char_length(btrim(body)) BETWEEN 1 AND 2000),
    state text NOT NULL DEFAULT 'VISIBLE' CHECK (state IN ('VISIBLE','HIDDEN','DELETED')),
    moderation_reason text CHECK (moderation_reason IS NULL OR char_length(moderation_reason) <= 500),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS daily_post_comments_post_idx ON public.daily_post_comments(post_id, created_at, id);
CREATE INDEX IF NOT EXISTS daily_post_comments_parent_idx ON public.daily_post_comments(parent_id, created_at, id);

CREATE TABLE IF NOT EXISTS public.daily_post_preview_receipts (
    post_id uuid NOT NULL REFERENCES public.daily_posts(id) ON DELETE CASCADE,
    user_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    first_presented_at timestamptz NOT NULL DEFAULT now(),
    opened_at timestamptz,
    PRIMARY KEY(post_id, user_id)
);
CREATE INDEX IF NOT EXISTS daily_post_preview_user_idx ON public.daily_post_preview_receipts(user_id, first_presented_at DESC);

CREATE TABLE IF NOT EXISTS public.daily_post_translations (
    post_id uuid NOT NULL REFERENCES public.daily_posts(id) ON DELETE CASCADE,
    revision integer NOT NULL CHECK (revision > 0),
    target_language text NOT NULL CHECK (target_language ~ '^[a-z]{2,3}(-[A-Z]{2})?$'),
    translated_headline text NOT NULL CHECK (char_length(translated_headline) <= 240),
    translated_excerpt text NOT NULL DEFAULT '' CHECK (char_length(translated_excerpt) <= 900),
    translated_blocks jsonb NOT NULL DEFAULT '[]'::jsonb CHECK (jsonb_typeof(translated_blocks) = 'array'),
    audio_storage_path text CHECK (audio_storage_path IS NULL OR char_length(audio_storage_path) <= 512),
    provider text NOT NULL DEFAULT 'gemini',
    created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY(post_id, revision, target_language)
);

CREATE TABLE IF NOT EXISTS public.daily_post_publication_jobs (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    post_id uuid NOT NULL REFERENCES public.daily_posts(id) ON DELETE CASCADE,
    run_at timestamptz NOT NULL,
    state text NOT NULL DEFAULT 'PENDING' CHECK (state IN ('PENDING','CLAIMED','DONE','FAILED','CANCELLED')),
    push_enabled boolean NOT NULL DEFAULT false,
    dispatch_key text NOT NULL UNIQUE CHECK (char_length(dispatch_key) BETWEEN 12 AND 200),
    attempts integer NOT NULL DEFAULT 0 CHECK (attempts BETWEEN 0 AND 20),
    last_error text CHECK (last_error IS NULL OR char_length(last_error) <= 2000),
    claimed_at timestamptz,
    completed_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX IF NOT EXISTS daily_post_one_live_job_per_post_idx
    ON public.daily_post_publication_jobs(post_id)
    WHERE state IN ('PENDING','CLAIMED');
CREATE INDEX IF NOT EXISTS daily_post_jobs_due_idx ON public.daily_post_publication_jobs(run_at, id) WHERE state = 'PENDING';

CREATE TABLE IF NOT EXISTS public.daily_post_audit_events (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_id uuid REFERENCES auth.users(id) ON DELETE SET NULL,
    post_id uuid REFERENCES public.daily_posts(id) ON DELETE SET NULL,
    event_type text NOT NULL CHECK (char_length(event_type) BETWEEN 1 AND 100),
    result text NOT NULL CHECK (result IN ('ALLOWED','DENIED','FAILED')),
    metadata jsonb NOT NULL DEFAULT '{}'::jsonb CHECK (jsonb_typeof(metadata) = 'object'),
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS daily_post_audit_post_idx ON public.daily_post_audit_events(post_id, created_at DESC);

CREATE OR REPLACE FUNCTION public.daily_post_touch_updated_at()
RETURNS trigger
LANGUAGE plpgsql
SET search_path = public
AS $$
BEGIN
    NEW.updated_at := now();
    IF TG_OP = 'UPDATE' AND (
        NEW.headline IS DISTINCT FROM OLD.headline OR
        NEW.excerpt IS DISTINCT FROM OLD.excerpt OR
        NEW.content_blocks IS DISTINCT FROM OLD.content_blocks OR
        NEW.template_key IS DISTINCT FROM OLD.template_key OR
        NEW.canonical_language IS DISTINCT FROM OLD.canonical_language
    ) THEN
        NEW.revision := OLD.revision + 1;
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS daily_posts_touch_updated_at ON public.daily_posts;
CREATE TRIGGER daily_posts_touch_updated_at
BEFORE UPDATE ON public.daily_posts
FOR EACH ROW EXECUTE FUNCTION public.daily_post_touch_updated_at();

CREATE OR REPLACE FUNCTION public.daily_post_validate_comment_parent()
RETURNS trigger
LANGUAGE plpgsql
SET search_path = public
AS $$
DECLARE
    parent_post uuid;
    parent_depth smallint;
BEGIN
    IF NEW.parent_id IS NULL THEN
        NEW.depth := 0;
        RETURN NEW;
    END IF;
    SELECT post_id, depth INTO parent_post, parent_depth
      FROM public.daily_post_comments WHERE id = NEW.parent_id;
    IF parent_post IS NULL OR parent_post <> NEW.post_id THEN
        RAISE EXCEPTION 'Invalid comment parent';
    END IF;
    IF parent_depth >= 3 THEN
        RAISE EXCEPTION 'Maximum comment depth exceeded';
    END IF;
    NEW.depth := parent_depth + 1;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS daily_post_comment_parent_guard ON public.daily_post_comments;
CREATE TRIGGER daily_post_comment_parent_guard
BEFORE INSERT OR UPDATE OF parent_id, post_id ON public.daily_post_comments
FOR EACH ROW EXECUTE FUNCTION public.daily_post_validate_comment_parent();

CREATE OR REPLACE FUNCTION public.daily_post_comment_count_sync()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        UPDATE public.daily_posts SET comment_count = comment_count + 1 WHERE id = NEW.post_id;
        RETURN NEW;
    ELSIF TG_OP = 'DELETE' THEN
        UPDATE public.daily_posts SET comment_count = GREATEST(comment_count - 1, 0) WHERE id = OLD.post_id;
        RETURN OLD;
    END IF;
    RETURN NULL;
END;
$$;
REVOKE ALL ON FUNCTION public.daily_post_comment_count_sync() FROM PUBLIC;

DROP TRIGGER IF EXISTS daily_post_comment_count_insert ON public.daily_post_comments;
CREATE TRIGGER daily_post_comment_count_insert AFTER INSERT ON public.daily_post_comments
FOR EACH ROW EXECUTE FUNCTION public.daily_post_comment_count_sync();
DROP TRIGGER IF EXISTS daily_post_comment_count_delete ON public.daily_post_comments;
CREATE TRIGGER daily_post_comment_count_delete AFTER DELETE ON public.daily_post_comments
FOR EACH ROW EXECUTE FUNCTION public.daily_post_comment_count_sync();

CREATE OR REPLACE FUNCTION public.daily_post_page_v1(
    p_cursor_published_at timestamptz DEFAULT NULL,
    p_cursor_id uuid DEFAULT NULL,
    p_limit integer DEFAULT 20
)
RETURNS SETOF public.daily_posts
LANGUAGE sql
STABLE
SECURITY INVOKER
SET search_path = public
AS $$
    SELECT p.*
    FROM public.daily_posts p
    WHERE p.state = 'PUBLISHED'
      AND p.published_at IS NOT NULL
      AND (
        p_cursor_published_at IS NULL OR
        (p.published_at, p.id) < (p_cursor_published_at, COALESCE(p_cursor_id, 'ffffffff-ffff-ffff-ffff-ffffffffffff'::uuid))
      )
    ORDER BY p.published_at DESC, p.id DESC
    LIMIT LEAST(GREATEST(p_limit, 1), 50);
$$;

CREATE OR REPLACE FUNCTION public.daily_post_get_v1(p_post_id uuid)
RETURNS public.daily_posts
LANGUAGE sql
STABLE
SECURITY INVOKER
SET search_path = public
AS $$
    SELECT p FROM public.daily_posts p
    WHERE p.id = p_post_id AND p.state = 'PUBLISHED';
$$;

CREATE OR REPLACE FUNCTION public.daily_post_comments_page_v1(
    p_post_id uuid,
    p_after_created_at timestamptz DEFAULT NULL,
    p_after_id uuid DEFAULT NULL,
    p_limit integer DEFAULT 100
)
RETURNS SETOF public.daily_post_comments
LANGUAGE sql
STABLE
SECURITY INVOKER
SET search_path = public
AS $$
    SELECT c.*
    FROM public.daily_post_comments c
    JOIN public.daily_posts p ON p.id = c.post_id
    WHERE c.post_id = p_post_id
      AND p.state = 'PUBLISHED'
      AND c.state <> 'DELETED'
      AND (
        p_after_created_at IS NULL OR
        (c.created_at, c.id) > (p_after_created_at, COALESCE(p_after_id, '00000000-0000-0000-0000-000000000000'::uuid))
      )
    ORDER BY c.created_at, c.id
    LIMIT LEAST(GREATEST(p_limit, 1), 200);
$$;

ALTER TABLE public.daily_posts ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.daily_post_media ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.daily_post_comments ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.daily_post_preview_receipts ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.daily_post_translations ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.daily_post_publication_jobs ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.daily_post_audit_events ENABLE ROW LEVEL SECURITY;

GRANT EXECUTE ON FUNCTION public.daily_post_page_v1(timestamptz, uuid, integer) TO anon, authenticated;
GRANT EXECUTE ON FUNCTION public.daily_post_get_v1(uuid) TO anon, authenticated;
GRANT EXECUTE ON FUNCTION public.daily_post_comments_page_v1(uuid, timestamptz, uuid, integer) TO anon, authenticated;

COMMIT;
