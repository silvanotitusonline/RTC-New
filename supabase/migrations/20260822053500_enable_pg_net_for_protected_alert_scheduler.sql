begin;

-- The existing pg_cron alert schedule invokes the protected Edge Function through net.http_post.
-- pg_net creates the internal net schema while the extension itself is registered in extensions.
create extension if not exists pg_net with schema extensions;

commit;
