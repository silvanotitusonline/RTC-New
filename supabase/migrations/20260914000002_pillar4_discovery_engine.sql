
-- PILLAR 4: SEARCH, DISCOVERY & TRENDS
BEGIN;

-- 1. Full-Text Search Implementation
-- We create a search vector column for fast searching across body and author
ALTER TABLE public.community_posts 
ADD COLUMN IF NOT EXISTS search_vector tsvector;

CREATE INDEX IF NOT EXISTS idx_posts_search_vector ON public.community_posts USING GIN (search_vector);

-- Trigger to keep search_vector updated automatically
CREATE OR REPLACE FUNCTION public.community_posts_search_trigger() RETURNS trigger AS $$
BEGIN
  new.search_vector := to_tsvector('english', coalesce(new.body, '') || ' ' || coalesce(new.author_name, ''));
  RETURN new;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_posts_search_update 
BEFORE INSERT OR UPDATE ON public.community_posts
FOR EACH ROW EXECUTE FUNCTION public.community_posts_search_trigger();

-- 2. Trending Hashtags Engine
CREATE TABLE IF NOT EXISTS public.trending_hashtags (
    hashtag text PRIMARY KEY,
    mention_count int DEFAULT 0,
    last_mentioned_at timestamptz DEFAULT now()
);

-- 3. Optimized Search Function ( la la la la-density result sets)
CREATE OR REPLACE FUNCTION public.community_search(search_term text)
RETURNS TABLE (
    post_id uuid, 
    body text, 
    username text, 
    rank float4
) AS $$
BEGIN
    RETURN QUERY
    SELECT 
        p.id, 
        p.body, 
        u.username, 
        ts_rank(p.search_vector, websearch_to_tsquery('english', search_term)) as rank
    FROM public.community_posts p
    JOIN public.profiles u ON p.author_id = u.id
    WHERE p.search_vector @@ websearch_to_tsquery('english', search_term)
    ORDER BY rank DESC
    LIMIT 50;
END;
$$ LANGUAGE plpgsql;

COMMIT;
