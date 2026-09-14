begin;

-- PostgreSQL does not allow CREATE OR REPLACE FUNCTION to change a function's
-- result type or OUT-parameter row shape. The following canonical RPCs have
-- historical signatures whose result contracts differ from the Android runtime
-- contracts restored by the immediately-following 10:30 migration.
-- Drop only the incompatible signatures here; the 10:30 migration recreates
-- every one before COMMIT. No table data is modified by this migration.

drop function if exists public.repost_community_post(uuid);
drop function if exists public.bookmark_community_post(uuid);
drop function if exists public.unbookmark_community_post(uuid);
drop function if exists public.search_community_posts_cursor(text, real, uuid, integer);
drop function if exists public.search_community_posts(text, integer, integer);

commit;
