
-- PILLAR 1: CORE FEED & CONTENT ENGINE
BEGIN;

-- 1. Optimize for Cursor-based pagination
CREATE INDEX IF NOT EXISTS idx_posts_created_at_desc ON public.community_posts (created_at DESC, id DESC);

-- 2. Enable Threading (Nested Replies)
ALTER TABLE public.community_posts 
ADD COLUMN IF NOT EXISTS parent_id uuid REFERENCES public.community_posts(id) ON DELETE SET NULL,
ADD COLUMN IF NOT EXISTS thread_depth int DEFAULT 0 CHECK (thread_depth <= 5);

-- 3. The la-Density Optimized Feed View (Eliminates N+1 Queries)
CREATE OR REPLACE VIEW public.v_community_feed AS
SELECT 
    p.id, 
    p.body, 
    p.created_at, 
    p.parent_id,
    p.thread_depth,
    u.username, 
    u.avatar_url, 
    u.is_verified,
    (SELECT count(*) FROM public.post_likes pl WHERE pl.post_id = p.id) as like_count,
    (SELECT count(*) FROM public.community_posts replies WHERE replies.parent_id = p.id) as reply_count
FROM public.community_posts p
JOIN public.profiles u ON p.author_id = u.id;

COMMIT;
