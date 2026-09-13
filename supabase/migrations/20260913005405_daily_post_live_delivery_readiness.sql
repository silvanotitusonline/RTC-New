BEGIN;

-- Hosted RTC already has this shared delivery ledger. Reproduce that canonical
-- schema for fresh installations before adding the Daily Post source type.
CREATE TABLE IF NOT EXISTS public.notification_delivery_attempts (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  source_type text NOT NULL,
  source_id uuid NOT NULL,
  device_registration_id uuid REFERENCES public.device_registrations(id) ON DELETE SET NULL,
  token_fingerprint text NOT NULL,
  state text NOT NULL DEFAULT 'PENDING'
    CHECK (state IN ('PENDING','RETRY_PENDING','ACCEPTED','PERMANENT_FAILURE')),
  attempt_count integer NOT NULL DEFAULT 0 CHECK (attempt_count BETWEEN 0 AND 20),
  last_http_status integer,
  last_error_code text,
  next_retry_at timestamptz,
  accepted_at timestamptz,
  permanently_failed_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT notification_delivery_attempt_source_type_source_id_token_f_key
    UNIQUE (source_type,source_id,token_fingerprint)
);
CREATE INDEX IF NOT EXISTS notification_delivery_attempts_retry_idx
  ON public.notification_delivery_attempts(state,next_retry_at) WHERE state='RETRY_PENDING';
CREATE INDEX IF NOT EXISTS notification_delivery_attempts_source_idx
  ON public.notification_delivery_attempts(source_type,source_id,created_at);
CREATE INDEX IF NOT EXISTS idx_notification_delivery_attempts_device
  ON public.notification_delivery_attempts(device_registration_id) WHERE device_registration_id IS NOT NULL;
ALTER TABLE public.notification_delivery_attempts ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.notification_delivery_attempts FROM PUBLIC,anon,authenticated;
GRANT ALL ON public.notification_delivery_attempts TO service_role;
DROP POLICY IF EXISTS notification_delivery_attempts_client_denied ON public.notification_delivery_attempts;
CREATE POLICY notification_delivery_attempts_client_denied ON public.notification_delivery_attempts
  FOR ALL TO authenticated USING (false) WITH CHECK (false);
DROP TRIGGER IF EXISTS notification_delivery_attempts_set_updated_at ON public.notification_delivery_attempts;
CREATE TRIGGER notification_delivery_attempts_set_updated_at
  BEFORE UPDATE ON public.notification_delivery_attempts
  FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();

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
