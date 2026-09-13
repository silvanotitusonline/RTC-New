BEGIN;

ALTER TABLE public.notification_delivery_attempts
  DROP CONSTRAINT IF EXISTS notification_delivery_attempts_source_type_check;
ALTER TABLE public.notification_delivery_attempts
  ADD CONSTRAINT notification_delivery_attempts_source_type_check
  CHECK (source_type IN ('NOTIFICATION_EVENT','COMMUNITY_ALERT','MODERATION_REPORT','DAILY_POST_JOB'));

CREATE OR REPLACE FUNCTION public.daily_post_claim_due_jobs_v1(p_limit integer DEFAULT 20)
RETURNS TABLE(job_id uuid, post_id uuid, push_enabled boolean, dispatch_key text)
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public, pg_temp
AS $$
BEGIN
  IF COALESCE(auth.role(),'') <> 'service_role' THEN RAISE EXCEPTION 'Service role required'; END IF;
  RETURN QUERY
  WITH due AS (
    SELECT j.id FROM public.daily_post_publication_jobs j
    WHERE j.attempts < 5 AND (
      (j.state IN ('PENDING','FAILED') AND j.run_at <= now())
      OR (j.state='CLAIMED' AND j.claimed_at < now()-interval '15 minutes')
    )
    ORDER BY j.run_at,j.id FOR UPDATE SKIP LOCKED
    LIMIT LEAST(GREATEST(COALESCE(p_limit,20),1),50)
  ), claimed AS (
    UPDATE public.daily_post_publication_jobs j
    SET state='CLAIMED',claimed_at=now(),completed_at=NULL,attempts=j.attempts+1
    FROM due WHERE j.id=due.id
    RETURNING j.id,j.post_id,j.push_enabled,j.dispatch_key
  ) SELECT c.id,c.post_id,c.push_enabled,c.dispatch_key FROM claimed c;
END;
$$;

CREATE OR REPLACE FUNCTION public.daily_post_complete_job_v1(p_job_id uuid,p_success boolean,p_error text DEFAULT NULL)
RETURNS void LANGUAGE plpgsql SECURITY DEFINER SET search_path = public, pg_temp
AS $$
DECLARE p_id uuid;
BEGIN
  IF COALESCE(auth.role(),'') <> 'service_role' THEN RAISE EXCEPTION 'Service role required'; END IF;
  UPDATE public.daily_post_publication_jobs SET
    state=CASE WHEN p_success THEN 'DONE' ELSE 'FAILED' END,
    completed_at=now(),
    run_at=CASE WHEN p_success THEN run_at ELSE now()+interval '5 minutes' END,
    last_error=CASE WHEN p_success THEN NULL ELSE left(COALESCE(p_error,'Unknown error'),2000) END
  WHERE id=p_job_id AND state='CLAIMED' RETURNING post_id INTO p_id;
  IF p_id IS NOT NULL THEN
    INSERT INTO public.daily_post_audit_events(post_id,event_type,result,metadata)
    VALUES(p_id,'PUBLICATION_JOB',CASE WHEN p_success THEN 'ALLOWED' ELSE 'FAILED' END,
      jsonb_build_object('jobId',p_job_id,'error',left(p_error,2000)));
  END IF;
END;
$$;
REVOKE ALL ON FUNCTION public.daily_post_claim_due_jobs_v1(integer) FROM PUBLIC,anon,authenticated;
REVOKE ALL ON FUNCTION public.daily_post_complete_job_v1(uuid,boolean,text) FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION public.daily_post_claim_due_jobs_v1(integer) TO service_role;
GRANT EXECUTE ON FUNCTION public.daily_post_complete_job_v1(uuid,boolean,text) TO service_role;

COMMIT;
