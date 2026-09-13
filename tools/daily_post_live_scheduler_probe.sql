-- Run only against a newly activated Daily Post workspace with no pending work.
-- Every fixture and state transition is rolled back; no HTTP requests are made.
BEGIN;
DO $$
DECLARE actor uuid:=gen_random_uuid(); post uuid:=gen_random_uuid(); job uuid:=gen_random_uuid(); n integer;
BEGIN
  IF EXISTS(SELECT 1 FROM public.daily_post_publication_jobs) THEN
    RAISE EXCEPTION 'Use disposable CI for populated publication workspaces';
  END IF;
  INSERT INTO auth.users(id,aud,role,email,created_at,updated_at)
    VALUES(actor,'authenticated','authenticated',actor::text||'@scheduler-check.invalid',now(),now());
  INSERT INTO public.daily_posts(id,author_id,state,headline,scheduled_for)
    VALUES(post,actor,'SCHEDULED','Transactional scheduler check',now()+interval '1 hour');
  INSERT INTO public.daily_post_publication_jobs(id,post_id,run_at,dispatch_key)
    VALUES(job,post,now()+interval '1 hour','check-'||job::text);
  INSERT INTO public.notification_delivery_attempts(source_type,source_id,token_fingerprint)
    VALUES('DAILY_POST_JOB',job,'transactional-check-'||job::text);
  PERFORM set_config('request.jwt.claims','{"role":"service_role"}',true);
  SELECT count(*) INTO n FROM public.daily_post_claim_due_jobs_v1(1);
  IF n<>0 THEN RAISE EXCEPTION 'Future job claimed'; END IF;
  UPDATE public.daily_post_publication_jobs SET run_at=now()-interval '1 minute' WHERE id=job;
  SELECT count(*) INTO n FROM public.daily_post_claim_due_jobs_v1(1);
  IF n<>1 THEN RAISE EXCEPTION 'Due job not claimed'; END IF;
  PERFORM public.daily_post_execute_job_v1(job);
  IF (SELECT state FROM public.daily_posts WHERE id=post)<>'PUBLISHED' THEN RAISE EXCEPTION 'Publication failed'; END IF;
  PERFORM public.daily_post_complete_job_v1(job,false,'VERIFICATION_TRANSIENT');
  SELECT count(*) INTO n FROM public.daily_post_claim_due_jobs_v1(1);
  IF n<>0 THEN RAISE EXCEPTION 'Retry delay ignored'; END IF;
  UPDATE public.daily_post_publication_jobs SET run_at=now()-interval '1 minute' WHERE id=job;
  SELECT count(*) INTO n FROM public.daily_post_claim_due_jobs_v1(1);
  IF n<>1 OR (SELECT attempts FROM public.daily_post_publication_jobs WHERE id=job)<>2 THEN RAISE EXCEPTION 'Retry failed'; END IF;
  UPDATE public.daily_post_publication_jobs SET state='FAILED',attempts=5,run_at=now()-interval '1 minute' WHERE id=job;
  SELECT count(*) INTO n FROM public.daily_post_claim_due_jobs_v1(1);
  IF n<>0 THEN RAISE EXCEPTION 'Retry limit ignored'; END IF;
  UPDATE public.daily_post_publication_jobs SET state='CLAIMED',attempts=1,claimed_at=now()-interval '16 minutes' WHERE id=job;
  SELECT count(*) INTO n FROM public.daily_post_claim_due_jobs_v1(1);
  IF n<>1 THEN RAISE EXCEPTION 'Stale lease recovery failed'; END IF;
END;
$$;
SELECT 'passed: delivery log, due publication, retry delay, retry cap, worker recovery; fixtures rolled back' as scheduler_check;
ROLLBACK;
