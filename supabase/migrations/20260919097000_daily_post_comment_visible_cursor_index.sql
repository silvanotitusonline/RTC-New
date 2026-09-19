begin;

create index if not exists daily_post_comments_visible_cursor_idx
  on public.daily_post_comments(post_id, created_at, id)
  where state = 'VISIBLE';

commit;
