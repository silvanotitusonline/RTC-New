begin;
set local search_path = extensions, public, pg_catalog;
select plan(25);

create temporary table deletion_test_identities(key text primary key, user_id uuid not null, session_id uuid not null) on commit drop;
insert into deletion_test_identities values
  ('owner','21000000-0000-4000-8000-000000000101','31000000-0000-4000-8000-000000000201'),
  ('other','22000000-0000-4000-8000-000000000102','32000000-0000-4000-8000-000000000202'),
  ('moderator','23000000-0000-4000-8000-000000000103','33000000-0000-4000-8000-000000000203');

reset role;
insert into auth.users(id,aud,role,email,created_at,updated_at)
select user_id,'authenticated','authenticated',key || '@delete-test.local',now(),now() from deletion_test_identities;
insert into auth.sessions(id,user_id,aal,created_at,updated_at,not_after)
select session_id,user_id,'aal1'::auth.aal_level,now(),now(),now()+interval '1 day' from deletion_test_identities;
insert into public.profiles(id,display_name)
select user_id, initcap(key) || ' Delete Test' from deletion_test_identities;
update public.community_profiles set guidelines_version=1, guidelines_accepted_at=now() where id in (select user_id from deletion_test_identities);
insert into public.user_roles(user_id,role)
values ((select user_id from deletion_test_identities where key='moderator'),'MODERATOR');

select ok(to_regprocedure('public.edit_community_post(uuid,text,text,text)') is not null,'owner edit RPC exists');
select ok(to_regprocedure('public.delete_community_post(uuid)') is not null,'owner hard-delete RPC exists');
select ok(to_regprocedure('public.moderation_decide_report(uuid,text,text)') is not null,'moderation decision RPC exists');
select is(has_function_privilege('anon','public.delete_community_post(uuid)','EXECUTE'),false,'anonymous deletion is denied');
select is(has_function_privilege('authenticated','public.delete_community_post(uuid)','EXECUTE'),true,'authenticated deletion reaches owner guard');
select is((select count(*)::integer from pg_constraint c join pg_class t on t.oid=c.conrelid where t.relname in ('community_bookmarks','community_comments','community_polls','community_post_hashtags','community_post_media','community_reposts','community_sentiment_logs','notifications','post_likes','promoted_content') and c.contype='f' and c.confrelid='public.community_posts'::regclass and c.confdeltype='c'),10,'all known post-owned foreign keys cascade');

create temporary table deletion_fixture(kind text primary key, post_id uuid not null, report_id uuid) on commit drop;
reset role;
insert into public.community_posts(id,author_id,category_id,body,state)
values ('41000000-0000-4000-8000-000000000001',(select user_id from deletion_test_identities where key='owner'),(select id from public.community_categories where active order by sort_order limit 1),'Delete fixture post','PUBLISHED');
insert into deletion_fixture(kind,post_id) values ('owner','41000000-0000-4000-8000-000000000001');
insert into public.community_comments(post_id,author_id,body) values ('41000000-0000-4000-8000-000000000001',(select user_id from deletion_test_identities where key='other'),'Child comment');
insert into public.community_post_media(post_id,storage_path,media_kind,mime_type,byte_size,position) values ('41000000-0000-4000-8000-000000000001','delete-test/media.jpg','IMAGE','image/jpeg',100,1);
insert into public.community_bookmarks(user_id,post_id) values ((select user_id from deletion_test_identities where key='other'),'41000000-0000-4000-8000-000000000001');
insert into public.community_reactions(actor_id,subject_type,subject_id,reaction_type) values ((select user_id from deletion_test_identities where key='other'),'POST','41000000-0000-4000-8000-000000000001','LIKE');
insert into public.community_reports(reporter_id,subject_type,subject_id,reason_code,details) values ((select user_id from deletion_test_identities where key='other'),'POST','41000000-0000-4000-8000-000000000001','SPAM','Delete test report');
insert into public.community_hashtags(tag) values ('delete_test');
insert into public.community_post_hashtags(post_id,hashtag_id) values ('41000000-0000-4000-8000-000000000001',(select id from public.community_hashtags where tag='delete_test'));

select set_config('request.jwt.claims',jsonb_build_object('sub',(select user_id from deletion_test_identities where key='owner'),'session_id',(select session_id from deletion_test_identities where key='owner'),'aal','aal1','role','authenticated')::text,true);
set local role authenticated;
select lives_ok(format('select public.delete_community_post(%L::uuid)',(select post_id from deletion_fixture where kind='owner')),'owner delete succeeds');
reset role;
select is((select count(*)::integer from public.community_posts where id='41000000-0000-4000-8000-000000000001'),0,'deleted post is absent from base table');
select is((select count(*)::integer from public.community_post_feed where id='41000000-0000-4000-8000-000000000001'),0,'deleted post is absent from public community feed');
select is((select count(*)::integer from public.community_personalized_feed where id='41000000-0000-4000-8000-000000000001'),0,'deleted post is absent from personalized user feed');
select is((select count(*)::integer from public.community_comments where post_id='41000000-0000-4000-8000-000000000001'),0,'post comments cascade away');
select is((select count(*)::integer from public.community_post_media where post_id='41000000-0000-4000-8000-000000000001'),0,'post media records cascade away');
select is((select count(*)::integer from public.community_bookmarks where post_id='41000000-0000-4000-8000-000000000001'),0,'user bookmarks cascade away');
select is((select count(*)::integer from public.community_reactions where subject_type='POST' and subject_id='41000000-0000-4000-8000-000000000001'),0,'generic post reactions are explicitly purged');
select is((select count(*)::integer from public.community_reports where subject_type='POST' and subject_id='41000000-0000-4000-8000-000000000001'),0,'legacy post reports are explicitly purged');
select is((select count(*)::integer from public.community_post_hashtags where post_id='41000000-0000-4000-8000-000000000001'),0,'post hashtag links cascade away');
select ok(exists(select 1 from public.audit_events where event_type='COMMUNITY_POST_DELETED' and entity_id='41000000-0000-4000-8000-000000000001' and metadata->>'hardDelete'='true'),'owner hard deletion is audited');

reset role;
insert into public.community_posts(id,author_id,category_id,body,state) values ('42000000-0000-4000-8000-000000000001',(select user_id from deletion_test_identities where key='owner'),(select id from public.community_categories where active order by sort_order limit 1),'Authorization fixture','PUBLISHED');
select set_config('request.jwt.claims',jsonb_build_object('sub',(select user_id from deletion_test_identities where key='other'),'session_id',(select session_id from deletion_test_identities where key='other'),'aal','aal1','role','authenticated')::text,true);
set local role authenticated;
select throws_ok('select public.delete_community_post(''42000000-0000-4000-8000-000000000001''::uuid)','P0001','DELETE_NOT_ALLOWED','non-owner cannot delete another user post');
reset role;
select is((select count(*)::integer from public.community_posts where id='42000000-0000-4000-8000-000000000001'),1,'non-owner denial preserves the post');

reset role;
insert into public.community_posts(id,author_id,category_id,body,state) values ('43000000-0000-4000-8000-000000000001',(select user_id from deletion_test_identities where key='owner'),(select id from public.community_categories where active order by sort_order limit 1),'Moderator removal fixture','PUBLISHED');
insert into public.moderation_items(id,reporter_id,subject_type,subject_id,reason,reason_code,state) values ('44000000-0000-4000-8000-000000000001',(select user_id from deletion_test_identities where key='other'),'POST','43000000-0000-4000-8000-000000000001','Moderator removal test','SPAM','OPEN');
select set_config('request.jwt.claims',jsonb_build_object('sub',(select user_id from deletion_test_identities where key='moderator'),'session_id',(select session_id from deletion_test_identities where key='moderator'),'aal','aal1','role','authenticated')::text,true);
set local role authenticated;
select lives_ok('select public.moderation_decide_report(''44000000-0000-4000-8000-000000000001''::uuid,''REMOVE'',''Confirmed policy violation for deletion'')','moderator REMOVE succeeds');
reset role;
select is((select count(*)::integer from public.community_posts where id='43000000-0000-4000-8000-000000000001'),0,'moderator REMOVE removes the base post');
select is((select state from public.moderation_items where id='44000000-0000-4000-8000-000000000001'),'RESOLVED','moderator report remains auditable and resolved');
select ok(exists(select 1 from public.community_moderation_actions where subject_id='43000000-0000-4000-8000-000000000001' and action_type='REMOVE'),'moderator REMOVE writes a moderation action');
select ok(exists(select 1 from public.audit_events where event_type='COMMUNITY_MODERATION_REMOVE' and entity_id='43000000-0000-4000-8000-000000000001'),'moderator REMOVE writes an audit event');
select is((select count(*)::integer from public.operational_work_items where source_type='MODERATION_REPORT' and source_id='44000000-0000-4000-8000-000000000001' and state not in ('RESOLVED','CANCELLED')),0,'moderator removal closes its work item');

select * from finish();
rollback;
