BEGIN;

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
        SELECT j.id
        FROM public.daily_post_publication_jobs j
        WHERE j.state = 'PENDING' AND j.run_at <= now()
        ORDER BY j.run_at, j.id
        FOR UPDATE SKIP LOCKED
        LIMIT LEAST(GREATEST(p_limit, 1), 50)
    ), claimed AS (
        UPDATE public.daily_post_publication_jobs j
        SET state = 'CLAIMED', claimed_at = now(), attempts = attempts + 1
        FROM due
        WHERE j.id = due.id
        RETURNING j.id, j.post_id, j.push_enabled, j.dispatch_key
    )
    SELECT claimed.id, claimed.post_id, claimed.push_enabled, claimed.dispatch_key
    FROM claimed;
END;
$$;

REVOKE ALL ON FUNCTION public.daily_post_claim_due_jobs_v1(integer) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.daily_post_claim_due_jobs_v1(integer) TO service_role;

COMMIT;
