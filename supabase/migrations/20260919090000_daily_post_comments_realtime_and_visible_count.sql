begin;

-- Keep the client-facing count aligned with the rows returned by
-- daily_post_comments_page_v1: visible comments only. This also handles the
-- audited soft-delete and moderator-hide UPDATE paths.
create or replace function public.daily_post_comment_count_sync()
returns trigger
language plpgsql
security definer
set search_path = public, pg_temp
as $$
begin
  if tg_op = 'INSERT' then
    if new.state = 'VISIBLE' then
      update public.daily_posts
         set comment_count = comment_count + 1
       where id = new.post_id;
    end if;
    return new;
  elsif tg_op = 'DELETE' then
    if old.state = 'VISIBLE' then
      update public.daily_posts
         set comment_count = greatest(comment_count - 1, 0)
       where id = old.post_id;
    end if;
    return old;
  elsif tg_op = 'UPDATE' then
    if old.post_id is distinct from new.post_id
       or old.state is distinct from new.state then
      if old.state = 'VISIBLE' then
        update public.daily_posts
           set comment_count = greatest(comment_count - 1, 0)
         where id = old.post_id;
      end if;
      if new.state = 'VISIBLE' then
        update public.daily_posts
           set comment_count = comment_count + 1
         where id = new.post_id;
      end if;
    end if;
    return new;
  end if;
  return null;
end;
$$;

revoke all on function public.daily_post_comment_count_sync() from public, anon, authenticated;

drop trigger if exists daily_post_comment_count_insert on public.daily_post_comments;
drop trigger if exists daily_post_comment_count_delete on public.daily_post_comments;
drop trigger if exists daily_post_comment_count_sync on public.daily_post_comments;
create trigger daily_post_comment_count_sync
after insert or update of post_id, state or delete on public.daily_post_comments
for each row execute function public.daily_post_comment_count_sync();

-- Repair historical drift caused by the previous insert/delete-only trigger.
update public.daily_posts p
   set comment_count = counts.visible_count
  from (
    select p2.id as post_id,
           count(c.id)::integer as visible_count
      from public.daily_posts p2
      left join public.daily_post_comments c
        on c.post_id = p2.id
       and c.state = 'VISIBLE'
     group by p2.id
  ) counts
 where p.id = counts.post_id
   and p.comment_count is distinct from counts.visible_count;

-- Keep this migration safe when applied to a project that already includes the
-- table in the publication, while making the intended client contract explicit.
do $$
begin
  if not exists (
    select 1
      from pg_publication_rel pr
      join pg_class c on c.oid = pr.prrelid
      join pg_namespace n on n.oid = c.relnamespace
      join pg_publication p on p.oid = pr.prpubid
     where p.pubname = 'supabase_realtime'
       and n.nspname = 'public'
       and c.relname = 'daily_post_comments'
  ) then
    execute 'alter publication supabase_realtime add table public.daily_post_comments';
  end if;
end;
$$;

commit;
