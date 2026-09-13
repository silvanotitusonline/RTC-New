BEGIN;
SET LOCAL search_path = extensions,public,pg_catalog;
SELECT plan(12);
SELECT ok(NOT has_function_privilege('authenticated','public.daily_post_claim_due_jobs_v1(integer)','EXECUTE'),
  'Residents cannot claim publication jobs');
SELECT ok((SELECT relrowsecurity FROM pg_class WHERE oid='public.notification_delivery_attempts'::regclass),
  'Delivery ledger enforces row-level security');
SELECT ok(NOT has_table_privilege('authenticated','public.notification_delivery_attempts','SELECT'),
  'Residents cannot read device delivery metadata');
SELECT lives_ok($q$INSERT INTO public.notification_delivery_attempts(source_type,source_id,token_fingerprint)
  VALUES('DAILY_POST_JOB',gen_random_uuid(),'scheduler-contract-only')$q$,
  'Daily Post delivery attempts satisfy the live delivery log contract');

INSERT INTO auth.users(id,aud,role,email,created_at,updated_at)
VALUES('f1000000-0000-4000-8000-000000000001','authenticated','authenticated','scheduler-test@local.invalid',now(),now());
INSERT INTO public.daily_posts(id,author_id,state,headline,scheduled_for)
VALUES('f2000000-0000-4000-8000-000000000002','f1000000-0000-4000-8000-000000000001',
  'SCHEDULED','Scheduler verification',now()+interval '1 hour');
INSERT INTO public.daily_post_publication_jobs(id,post_id,run_at,dispatch_key)
VALUES('f3000000-0000-4000-8000-000000000003','f2000000-0000-4000-8000-000000000002',
  now()+interval '1 hour','scheduler-contract-only');
SELECT set_config('request.jwt.claims','{"role":"service_role"}',true);
SELECT is((SELECT count(*)::integer FROM public.daily_post_claim_due_jobs_v1(50)),0,
  'Future publications are not claimed early');
UPDATE public.daily_post_publication_jobs SET run_at=now()-interval '1 minute';
SELECT is((SELECT count(*)::integer FROM public.daily_post_claim_due_jobs_v1(50)),1,
  'A due publication is claimed once');
SELECT public.daily_post_execute_job_v1('f3000000-0000-4000-8000-000000000003');
SELECT is((SELECT state FROM public.daily_posts WHERE id='f2000000-0000-4000-8000-000000000002'),
  'PUBLISHED','Claimed job publishes the scheduled item');
SELECT public.daily_post_complete_job_v1('f3000000-0000-4000-8000-000000000003',false,'TRANSIENT_CHECK');
SELECT ok((SELECT state='FAILED' AND run_at>now() FROM public.daily_post_publication_jobs
  WHERE id='f3000000-0000-4000-8000-000000000003'),'Transient failure schedules a delayed retry');
SELECT is((SELECT count(*)::integer FROM public.daily_post_claim_due_jobs_v1(50)),0,
  'Failed job respects retry delay');
UPDATE public.daily_post_publication_jobs SET run_at=now()-interval '1 minute';
SELECT public.daily_post_claim_due_jobs_v1(50);
SELECT is((SELECT attempts FROM public.daily_post_publication_jobs WHERE id='f3000000-0000-4000-8000-000000000003'),
  2,'Retry increments the bounded attempt counter');
UPDATE public.daily_post_publication_jobs SET state='FAILED',attempts=5,run_at=now()-interval '1 minute';
SELECT is((SELECT count(*)::integer FROM public.daily_post_claim_due_jobs_v1(50)),0,
  'Exhausted jobs require review instead of retrying forever');
UPDATE public.daily_post_publication_jobs SET state='CLAIMED',attempts=1,claimed_at=now()-interval '16 minutes';
SELECT is((SELECT count(*)::integer FROM public.daily_post_claim_due_jobs_v1(50)),1,
  'Expired worker lease can be recovered');
SELECT * FROM finish();
ROLLBACK;
