
-- PILLAR 1: CORE FEED & CONTENT ENGINE
BEGIN;

-- 1. Optimize for Cursor-based pagination
CREATE INDEX IF NOT EXISTS idx_posts_created_at_desc ON public.community_posts (created_at DESC, id DESC);

-- 2. Enable Threading (Nested Replies)
ALTER TABLE public.community_posts 
ADD COLUMN IF NOT EXISTS parent_id uuid REFERENCES public.community_posts(id) ON DELETE SET NULL,
ADD COLUMN IF NOT EXISTS thread_depth int DEFAULT 0 CHECK (thread_depth <= 5);

-- 3. Compatibility projection over the canonical, RLS-aware community feed.
-- Public handles and media paths belong to community_profiles, not profiles.
-- A staff badge is not identity verification; keep this legacy badge field false
-- until a separately defined public verification contract exists.
CREATE OR REPLACE VIEW public.v_community_feed WITH (security_invoker = true) AS
SELECT
    f.id, f.body, f.created_at, p.parent_id, p.thread_depth,
    f.author_handle AS username,
    f.avatar_path AS avatar_url,
    false AS is_verified,
    f.reaction_count::bigint AS like_count,
    f.comment_count::bigint AS reply_count
FROM public.community_post_feed f
JOIN public.community_posts p ON p.id = f.id;

REVOKE ALL ON public.v_community_feed FROM PUBLIC, anon;
GRANT SELECT ON public.v_community_feed TO authenticated, service_role;

COMMIT;
