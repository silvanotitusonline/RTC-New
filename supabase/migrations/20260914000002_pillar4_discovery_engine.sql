
-- PILLAR 4: SEARCH, DISCOVERY & TRENDS
BEGIN;

-- 1. Reuse the stored generated vector introduced by the canonical search migration.
-- A trigger must not assign it, and author_name is a feed field, not a post column.
ALTER TABLE public.community_posts
ADD COLUMN IF NOT EXISTS search_vector tsvector
GENERATED ALWAYS AS (to_tsvector('english', coalesce(body, ''))) STORED;

CREATE INDEX IF NOT EXISTS idx_posts_search_vector ON public.community_posts USING GIN (search_vector);

-- 2. Trending Hashtags Engine
CREATE TABLE IF NOT EXISTS public.trending_hashtags (
    hashtag text PRIMARY KEY,
    mention_count int DEFAULT 0,
    last_mentioned_at timestamptz DEFAULT now()
);

-- 3. Search only the canonical visible feed and keep result ordering deterministic.
CREATE OR REPLACE FUNCTION public.community_search(search_term text)
RETURNS TABLE (post_id uuid, body text, username text, rank float4)
LANGUAGE sql
STABLE
SECURITY INVOKER
SET search_path = public, pg_temp
AS $$
    SELECT f.id, f.body, f.author_handle,
           ts_rank(p.search_vector, websearch_to_tsquery('english', search_term)) AS rank
    FROM public.community_post_feed f
    JOIN public.community_posts p ON p.id = f.id
    WHERE nullif(btrim(search_term), '') IS NOT NULL
      AND p.search_vector @@ websearch_to_tsquery('english', search_term)
    ORDER BY rank DESC, f.created_at DESC, f.id DESC
    LIMIT 50;
$$;

REVOKE ALL ON FUNCTION public.community_search(text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.community_search(text) TO authenticated;

COMMIT;
