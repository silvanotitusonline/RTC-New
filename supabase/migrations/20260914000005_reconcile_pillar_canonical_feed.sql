-- Reconcile already-applied pillar projections without changing the canonical schema.
BEGIN;

DROP TRIGGER IF EXISTS trg_posts_search_update ON public.community_posts;
DROP FUNCTION IF EXISTS public.community_posts_search_trigger();

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

-- 1. Reuse canonical pre-aggregated reaction/comment counts and visibility rules.
CREATE OR REPLACE VIEW public.v_community_feed_optimized WITH (security_invoker = true) AS
SELECT * FROM public.v_community_feed;

REVOKE ALL ON public.v_community_feed_optimized FROM PUBLIC, anon;
GRANT SELECT ON public.v_community_feed_optimized TO authenticated, service_role;

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
