
-- PILLAR 5: INFRASTRUCTURE & PRODUCTION READINESS
BEGIN;

-- 1. N+1 Elimination: Advanced Feed View with Pre-aggregated Counts
-- Instead of counting likes/replies per post in the application loop, 
-- we use a materialized-style view or optimized joins.
CREATE OR REPLACE VIEW public.v_community_feed_optimized WITH (security_invoker = true) AS
SELECT 
    p.id, p.body, p.created_at, p.parent_id, p.thread_depth,
    u.username, u.avatar_url, u.is_verified,
    COALESCE(l.like_count, 0) as like_count,
    COALESCE(r.reply_count, 0) as reply_count
FROM public.community_posts p
JOIN public.profiles u ON p.author_id = u.id
LEFT JOIN (
    SELECT post_id, count(*) as like_count FROM public.post_likes GROUP BY post_id
) l ON l.post_id = p.id
LEFT JOIN (
    SELECT parent_id, count(*) as reply_count FROM public.community_posts WHERE parent_id IS NOT NULL GROUP BY parent_id
) r ON r.parent_id = p.id;

-- 2. API Rate Limiting & Abuse Prevention
-- Create a table to track API request buckets for rate limiting
CREATE TABLE IF NOT EXISTS public.api_rate_limits (
    user_id uuid PRIMARY KEY REFERENCES auth.users(id),
    request_count int DEFAULT 0,
    reset_at timestamptz DEFAULT now(),
    last_request_at timestamptz DEFAULT now()
);

-- 3. Observability: Global Error Log
CREATE TABLE IF NOT EXISTS public.app_error_logs (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id uuid REFERENCES auth.users(id),
    error_message text,
    stack_trace text,
    device_info text,
    created_at timestamptz DEFAULT now()
);

COMMIT;
