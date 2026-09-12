-- Harden view security: ensure every public view evaluates RLS as the querying user,
-- not as the view owner. Supabase linter rule 0010 (security_definer_view).
-- ALTER VIEW IF EXISTS is used so this is safe to re-run and safe on any environment.

BEGIN;

-- Repo-tracked pillar views
ALTER VIEW IF EXISTS public.v_community_feed            SET (security_invoker = true);
ALTER VIEW IF EXISTS public.v_community_feed_optimized  SET (security_invoker = true);

-- Views present in the production database but not defined by a repository migration.
-- Securing them here closes the linter finding; see the drift note below.
ALTER VIEW IF EXISTS public.v_profiles_standardized     SET (security_invoker = true);
ALTER VIEW IF EXISTS public.v_notification_payloads     SET (security_invoker = true);

COMMIT;
