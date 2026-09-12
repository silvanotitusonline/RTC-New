begin;
set local search_path = extensions, public, pg_catalog;

select plan(40);

create temporary table civic_test_identities(
  key text primary key,
  user_id uuid not null,
  session_id uuid not null
) on commit drop;

-- Distinct leading UUID segments are intentional: the existing Community profile-sync
-- helper derives a deterministic handle from the first UUID characters.
insert into civic_test_identities(key,user_id,session_id) values
  ('owner','01000000-0000-4000-8000-000000000101','11000000-0000-4000-8000-000000000201'),
  ('other','02000000-0000-4000-8000-000000000102','12000000-0000-4000-8000-000000000202'),
  ('case_staff','03000000-0000-4000-8000-000000000103','13000000-0000-4000-8000-000000000203'),
  ('evidence','04000000-0000-4000-8000-000000000104','14000000-0000-4000-8000-000000000204'),
  ('admin','05000000-0000-4000-8000-000000000105','15000000-0000-4000-8000-000000000205'),
  ('moderator','06000000-0000-4000-8000-000000000106','16000000-0000-4000-8000-000000000206'),
  ('editor','07000000-0000-4000-8000-000000000107','17000000-0000-4000-8000-000000000207'),
  ('expired','08000000-0000-4000-8000-000000000108','18000000-0000-4000-8000-000000000208'),
  ('banned','09000000-0000-4000-8000-000000000109','19000000-0000-4000-8000-000000000209');

grant select on civic_test_identities to authenticated;

insert into auth.users(id,aud,role,email,created_at,updated_at,banned_until)
select user_id,'authenticated','authenticated',key || '@local.invalid',now(),now(),
       case when key='banned' then now()+interval '1 day' else null end
from civic_test_identities;

insert into auth.sessions(id,user_id,aal,created_at,updated_at,not_after)
select session_id,user_id,'aal1'::auth.aal_level,now(),now(),
       case when key='expired' then now()-interval '1 minute' else now()+interval '1 day' end
from civic_test_identities;

-- Inserting profiles invokes the repository's existing profile->Community profile sync.
insert into public.profiles(id,display_name)
select user_id,
       case key when 'owner' then 'Alice Resident'
                when 'other' then 'Bob Resident'
                else initcap(replace(key,'_',' ')) end
from civic_test_identities;

update public.community_profiles cp
set guidelines_version=1,
    guidelines_accepted_at=now(),
    updated_at=now()
where cp.id in (select user_id from civic_test_identities);

insert into public.user_roles(user_id,role) values
  ((select user_id from civic_test_identities where key='case_staff'),'CASE_STAFF'),
  ((select user_id from civic_test_identities where key='evidence'),'EVIDENCE_REVIEWER'),
  ((select user_id from civic_test_identities where key='admin'),'SYSTEM_ADMIN'),
  ((select user_id from civic_test_identities where key='moderator'),'MODERATOR'),
  ((select user_id from civic_test_identities where key='editor'),'CONTENT_EDITOR');

select set_config('civic_test.category_id',(select id::text from public.civic_report_categories where slug='water-sanitation'),true);

-- Owner context.
select set_config('request.jwt.claims',jsonb_build_object(
  'sub',(select user_id from civic_test_identities where key='owner'),
  'session_id',(select session_id from civic_test_identities where key='owner'),
  'aal','aal1','role','authenticated')::text,true);
set local role authenticated;

select is(auth.uid(),(select user_id from civic_test_identities where key='owner'),'owner fixture establishes auth.uid');

select set_config('civic_test.named_report', public.civic_report_create_v1(
  '10000000-0000-4000-8000-000000000001',
  'Water leaking near clinic',
  'A water pipe has been leaking near the clinic entrance since this morning.',
  null,current_setting('civic_test.category_id')::uuid,'HIGH','NAMED','MANUAL',
  'Near community clinic',null,null,'12 Clinic Road',
  'It is not safe to photograph the leak from the roadway.',false,'1')::text,true);
select ok(current_setting('civic_test.named_report')::uuid is not null,'named resident can create a report');
select is(
  public.civic_report_create_v1(
    '10000000-0000-4000-8000-000000000001','Water leaking near clinic',
    'A water pipe has been leaking near the clinic entrance since this morning.',null,
    current_setting('civic_test.category_id')::uuid,'HIGH','NAMED','MANUAL','Near community clinic',null,null,
    '12 Clinic Road','It is not safe to photograph the leak from the roadway.',false,'1'),
  current_setting('civic_test.named_report')::uuid,
  'duplicate create request UUID returns the original report ID');
select is((select author_display_name from public.civic_report_get_v1(current_setting('civic_test.named_report')::uuid)),'Alice Resident','named report exposes safe profile display name');
select is((select exact_address from public.civic_report_owner_private_details_v1(current_setting('civic_test.named_report')::uuid)),'12 Clinic Road','owner can retrieve exact private location through narrow RPC');

select set_config('civic_test.anonymous_report', public.civic_report_create_v1(
  '10000000-0000-4000-8000-000000000002','Streetlight not working',
  'The streetlight at the pedestrian crossing has been off for several nights.',null,
  current_setting('civic_test.category_id')::uuid,'NORMAL','ANONYMOUS','MANUAL','Pedestrian crossing',null,null,
  'Corner of Main and First','I cannot safely take a photo at this location after dark.',false,'1')::text,true);
select is((select author_display_name from public.civic_report_get_v1(current_setting('civic_test.anonymous_report')::uuid)),'Anonymous community member','anonymous report never exposes reporter display name publicly');

select set_config('civic_test.evidence_report', public.civic_report_create_v1(
  '10000000-0000-4000-8000-000000000003','Road surface collapsing',
  'A section of the road surface is collapsing and vehicles are swerving around it.',null,
  current_setting('civic_test.category_id')::uuid,'CRITICAL','ANONYMOUS','MAP','Main Road near school',-28.3001,23.1001,
  null,null,false,'1')::text,true);
select is((select count(*)::integer from public.civic_report_get_v1(current_setting('civic_test.evidence_report')::uuid)),0,'report without finalized evidence or valid exception is not publicly discoverable');

reset role;

insert into storage.objects(bucket_id,name,owner,owner_id) values
  ('civic-report-evidence',
   (select user_id::text from civic_test_identities where key='owner') || '/99999999-0000-4000-8000-000000000999/20000000-0000-4000-8000-000000000001',
   (select user_id from civic_test_identities where key='owner'),
   (select user_id::text from civic_test_identities where key='owner')),
  ('civic-report-evidence',
   (select user_id::text from civic_test_identities where key='owner') || '/10000000-0000-4000-8000-000000000003/20000000-0000-4000-8000-000000000002',
   (select user_id from civic_test_identities where key='owner'),
   (select user_id::text from civic_test_identities where key='owner'));

select set_config('request.jwt.claims',jsonb_build_object(
  'sub',(select user_id from civic_test_identities where key='owner'),
  'session_id',(select session_id from civic_test_identities where key='owner'),
  'aal','aal1','role','authenticated')::text,true);
set local role authenticated;

select throws_ok(
  format('select public.civic_report_finalize_evidence_v1(%L::uuid,%L::text,%L::text,%L::text,%s::bigint,%s::integer,%s::integer,null::integer,%s::smallint,%L::uuid)',
    current_setting('civic_test.evidence_report'),
    (select user_id::text from civic_test_identities where key='owner') || '/99999999-0000-4000-8000-000000000999/20000000-0000-4000-8000-000000000001',
    'IMAGE','image/jpeg',100000,1200,800,6,'30000000-0000-4000-8000-000000000001'),
  '42501','CIVIC_REPORT_EVIDENCE_PATH_INVALID','evidence path draft segment must bind to report request UUID');

reset role;
delete from public.civic_report_evidence where report_id=current_setting('civic_test.evidence_report')::uuid and position=6;

select set_config('request.jwt.claims',jsonb_build_object(
  'sub',(select user_id from civic_test_identities where key='owner'),
  'session_id',(select session_id from civic_test_identities where key='owner'),
  'aal','aal1','role','authenticated')::text,true);
set local role authenticated;
select ok(public.civic_report_finalize_evidence_v1(
    current_setting('civic_test.evidence_report')::uuid,
    ((select user_id::text from civic_test_identities where key='owner') || '/10000000-0000-4000-8000-000000000003/20000000-0000-4000-8000-000000000002')::text,
    'IMAGE'::text,'image/jpeg'::text,100000::bigint,1200::integer,800::integer,null::integer,1::smallint,'30000000-0000-4000-8000-000000000002'::uuid) is not null,
  'owner can finalize valid evidence bound to report draft path');
select is((select count(*)::integer from public.civic_report_get_v1(current_setting('civic_test.evidence_report')::uuid)),1,'finalized evidence makes report publicly discoverable');
select is((select evidence_count from public.civic_report_get_v1(current_setting('civic_test.evidence_report')::uuid)),1,'public projection reports finalized evidence count');

select is((public.civic_report_set_vote_v1(current_setting('civic_test.named_report')::uuid,1::smallint)->>'current_user_vote')::integer,1,'vote +1 is authoritative');
select is((public.civic_report_set_vote_v1(current_setting('civic_test.named_report')::uuid,(-1)::smallint)->>'current_user_vote')::integer,-1,'vote switches from +1 to -1');
select is((public.civic_report_set_vote_v1(current_setting('civic_test.named_report')::uuid,0::smallint)->>'current_user_vote')::integer,0,'vote 0 clears resident vote');

select set_config('civic_test.comment_id',public.civic_report_add_comment_v1(
  current_setting('civic_test.named_report')::uuid,'The leak is still active this afternoon.','40000000-0000-4000-8000-000000000001')::text,true);
select ok(current_setting('civic_test.comment_id')::uuid is not null,'authenticated resident can add report comment');
select is(public.civic_report_add_comment_v1(current_setting('civic_test.named_report')::uuid,'The leak is still active this afternoon.','40000000-0000-4000-8000-000000000001'),current_setting('civic_test.comment_id')::uuid,'duplicate comment request UUID returns original comment ID');
select is((select count(*)::integer from public.civic_report_comment_page_v1(current_setting('civic_test.named_report')::uuid,null,null,50) where id=current_setting('civic_test.comment_id')::uuid),1,'published comment is exposed through sanitized projection');

reset role;
select set_config('request.jwt.claims',jsonb_build_object(
  'sub',(select user_id from civic_test_identities where key='other'),
  'session_id',(select session_id from civic_test_identities where key='other'),
  'aal','aal1','role','authenticated')::text,true);
set local role authenticated;
select throws_ok(format('select * from public.civic_report_owner_private_details_v1(%L::uuid)',current_setting('civic_test.named_report')),'42501','CIVIC_REPORT_PRIVATE_ACCESS_DENIED','another resident cannot read owner private details');
select throws_ok(
  format('select public.civic_report_finalize_evidence_v1(%L::uuid,%L::text,%L::text,%L::text,%s::bigint,%s::integer,%s::integer,null::integer,%s::smallint,%L::uuid)',
    current_setting('civic_test.evidence_report'),
    (select user_id::text from civic_test_identities where key='owner') || '/10000000-0000-4000-8000-000000000003/20000000-0000-4000-8000-000000000002',
    'IMAGE','image/jpeg',100000,1200,800,2,'30000000-0000-4000-8000-000000000003'),
  '42501','CIVIC_REPORT_EVIDENCE_OWNER_MISMATCH','another resident cannot finalize owner evidence');
reset role;

select set_config('request.jwt.claims',jsonb_build_object(
  'sub',(select user_id from civic_test_identities where key='case_staff'),
  'session_id',(select session_id from civic_test_identities where key='case_staff'),
  'aal','aal2','role','authenticated')::text,true);
set local role authenticated;
select lives_ok('select * from public.admin_civic_report_page_v1(null,null,null,null,20)','CASE_STAFF can open review queue with AAL2');
select is((select reporter_id from public.admin_civic_report_page_v1(null,null,null,null,20) where id=current_setting('civic_test.named_report')::uuid),null::uuid,'CASE_STAFF review projection redacts reporter identity');
select throws_ok(format('select public.admin_civic_report_set_verification_v1(%L::uuid,true,%L,%L::uuid)',current_setting('civic_test.named_report'),'Evidence checked','50000000-0000-4000-8000-000000000001'),'42501','CIVIC_REPORT_STAFF_ROLE_REQUIRED','CASE_STAFF cannot verify reports');
select lives_ok(format('select public.admin_civic_report_transition_v1(%L::uuid,%L,%L,null,null,%L::uuid)',current_setting('civic_test.named_report'),'ACKNOWLEDGED','Case accepted','50000000-0000-4000-8000-000000000002'),'CASE_STAFF can acknowledge submitted report');
select throws_ok(format('select public.admin_civic_report_transition_v1(%L::uuid,%L,null,null,null,%L::uuid)',current_setting('civic_test.named_report'),'CLOSED','50000000-0000-4000-8000-000000000003'),'22023','CIVIC_REPORT_TRANSITION_REASON_REQUIRED','closing report requires public reason');
select throws_ok(format('select public.admin_civic_report_transition_v1(%L::uuid,%L,%L,null,null,%L::uuid)',current_setting('civic_test.named_report'),'SUBMITTED','Backwards','50000000-0000-4000-8000-000000000004'),'22023','CIVIC_REPORT_TRANSITION_INVALID','unsupported backward lifecycle transition is rejected');
reset role;

select set_config('request.jwt.claims',jsonb_build_object(
  'sub',(select user_id from civic_test_identities where key='evidence'),
  'session_id',(select session_id from civic_test_identities where key='evidence'),
  'aal','aal1','role','authenticated')::text,true);
set local role authenticated;
select throws_ok(format('select public.admin_civic_report_set_verification_v1(%L::uuid,true,%L,%L::uuid)',current_setting('civic_test.named_report'),'Evidence checked','50000000-0000-4000-8000-000000000005'),'42501','CIVIC_REPORT_STAFF_MFA_REQUIRED','Evidence Reviewer verification requires AAL2');
reset role;
select set_config('request.jwt.claims',jsonb_build_object(
  'sub',(select user_id from civic_test_identities where key='evidence'),
  'session_id',(select session_id from civic_test_identities where key='evidence'),
  'aal','aal2','role','authenticated')::text,true);
set local role authenticated;
select lives_ok(format('select public.admin_civic_report_set_verification_v1(%L::uuid,true,%L,%L::uuid)',current_setting('civic_test.named_report'),'Evidence checked','50000000-0000-4000-8000-000000000006'),'Evidence Reviewer can verify with AAL2');
select is((select reporter_id from public.admin_civic_report_page_v1(null,null,null,null,20) where id=current_setting('civic_test.named_report')::uuid),(select user_id from civic_test_identities where key='owner'),'Evidence Reviewer can see reporter identity in sensitive review projection');
reset role;

select set_config('request.jwt.claims',jsonb_build_object('sub',(select user_id from civic_test_identities where key='moderator'),'session_id',(select session_id from civic_test_identities where key='moderator'),'aal','aal2','role','authenticated')::text,true);
set local role authenticated;
select throws_ok('select * from public.admin_civic_report_page_v1(null,null,null,null,20)','42501','CIVIC_REPORT_STAFF_ROLE_REQUIRED','MODERATOR does not receive civic case-review authority');
reset role;
select set_config('request.jwt.claims',jsonb_build_object('sub',(select user_id from civic_test_identities where key='editor'),'session_id',(select session_id from civic_test_identities where key='editor'),'aal','aal2','role','authenticated')::text,true);
set local role authenticated;
select throws_ok('select * from public.admin_civic_report_page_v1(null,null,null,null,20)','42501','CIVIC_REPORT_STAFF_ROLE_REQUIRED','CONTENT_EDITOR does not receive civic case-review authority');
reset role;

select set_config('request.jwt.claims',jsonb_build_object('sub',(select user_id from civic_test_identities where key='admin'),'session_id',(select session_id from civic_test_identities where key='admin'),'aal','aal1','role','authenticated')::text,true);
set local role authenticated;
select throws_ok('select * from public.admin_civic_report_page_v1(null,null,null,null,20)','42501','CIVIC_REPORT_STAFF_MFA_REQUIRED','SYSTEM_ADMIN civic review requires AAL2');
reset role;
select set_config('request.jwt.claims',jsonb_build_object('sub',(select user_id from civic_test_identities where key='admin'),'session_id',(select session_id from civic_test_identities where key='admin'),'aal','aal2','role','authenticated')::text,true);
set local role authenticated;
select lives_ok('select * from public.admin_civic_report_page_v1(null,null,null,null,20)','SYSTEM_ADMIN civic review succeeds with AAL2');
reset role;

select set_config('request.jwt.claims',jsonb_build_object('sub',(select user_id from civic_test_identities where key='expired'),'session_id',(select session_id from civic_test_identities where key='expired'),'aal','aal1','role','authenticated')::text,true);
set local role authenticated;
select throws_ok(
  format('select public.civic_report_create_v1(%L::uuid,%L,%L,null,%L::uuid,%L,%L,%L,%L,null,null,%L,%L,false,%L)',
    '60000000-0000-4000-8000-000000000001','Expired session report','This report must not be accepted from an expired session.',current_setting('civic_test.category_id'),'LOW','NAMED','MANUAL','Test location','Test address','Unable to safely provide evidence for this local test.','1'),
  '42501','CIVIC_REPORT_AUTH_REQUIRED','expired session cannot create civic report');
reset role;

select set_config('request.jwt.claims',jsonb_build_object('sub',(select user_id from civic_test_identities where key='banned'),'session_id',(select session_id from civic_test_identities where key='banned'),'aal','aal1','role','authenticated')::text,true);
set local role authenticated;
select throws_ok(
  format('select public.civic_report_create_v1(%L::uuid,%L,%L,null,%L::uuid,%L,%L,%L,%L,null,null,%L,%L,false,%L)',
    '60000000-0000-4000-8000-000000000002','Disabled account report','This report must not be accepted from a banned account.',current_setting('civic_test.category_id'),'LOW','NAMED','MANUAL','Test location','Test address','Unable to safely provide evidence for this local test.','1'),
  '42501','CIVIC_REPORT_ACCOUNT_DISABLED','banned account cannot create civic report');
reset role;

select set_config('request.jwt.claims',jsonb_build_object('sub',(select user_id from civic_test_identities where key='owner'),'session_id',(select session_id from civic_test_identities where key='owner'),'aal','aal1','role','authenticated')::text,true);
set local role authenticated;
select lives_ok(format('select public.civic_report_withdraw_v1(%L::uuid,%L,%L::uuid)',current_setting('civic_test.anonymous_report'),'Issue no longer present','70000000-0000-4000-8000-000000000001'),'reporter can withdraw before completion');
reset role;
select is((select status from public.civic_reports where id=current_setting('civic_test.anonymous_report')::uuid),'WITHDRAWN','withdrawal persists terminal lifecycle state');
select ok(exists(select 1 from public.audit_events where entity_type='CIVIC_REPORT' and entity_id=current_setting('civic_test.named_report')::uuid and event_type='CIVIC_REPORT_VERIFICATION_SET'),'verification mutation writes audit event');
select ok(exists(select 1 from public.notification_events where recipient_id=(select user_id from civic_test_identities where key='owner') and notification_type in ('CIVIC_REPORT_VERIFICATION','CIVIC_REPORT_STATUS')),'privileged report changes enqueue owner notifications');

update public.civic_reports
set created_at=now()-interval '25 months',
    public_until=(now()-interval '25 months')+interval '24 months'
where id=current_setting('civic_test.named_report')::uuid;
set local role anon;
select is((select count(*)::integer from public.civic_report_get_v1(current_setting('civic_test.named_report')::uuid)),0,'report older than 24 months disappears from public discovery');
reset role;
select set_config('request.jwt.claims',jsonb_build_object('sub',(select user_id from civic_test_identities where key='owner'),'session_id',(select session_id from civic_test_identities where key='owner'),'aal','aal1','role','authenticated')::text,true);
set local role authenticated;
select is((select exact_address from public.civic_report_owner_private_details_v1(current_setting('civic_test.named_report')::uuid)),'12 Clinic Road','public expiry preserves owner-authorized private record access');

select * from finish();
rollback;
