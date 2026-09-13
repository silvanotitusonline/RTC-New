
-- PILLAR 5: INFRASTRUCTURE & PRODUCTION READINESS
BEGIN;

-- 1. Reuse canonical pre-aggregated reaction/comment counts and visibility rules.
CREATE OR REPLACE VIEW public.v_community_feed_optimized WITH (security_invoker = true) AS
SELECT * FROM public.v_community_feed;

REVOKE ALL ON public.v_community_feed_optimized FROM PUBLIC, anon;
GRANT SELECT ON public.v_community_feed_optimized TO authenticated, service_role;

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
