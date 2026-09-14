begin;

-- PostgreSQL does not allow CREATE OR REPLACE FUNCTION to change a function's
-- return type. The canonical engagement RPCs currently return jsonb, while the
-- Android authoritative repository decodes PostgREST TABLE/SETOF row arrays.
-- Drop only the three incompatible scalar-returning signatures immediately
-- before the runtime-contract migration recreates them with TABLE returns.
-- No data is modified by this migration.

drop function if exists public.repost_community_post(uuid);
drop function if exists public.bookmark_community_post(uuid);
drop function if exists public.unbookmark_community_post(uuid);

commit;
