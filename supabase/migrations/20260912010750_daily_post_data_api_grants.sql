BEGIN;

GRANT SELECT ON public.daily_posts TO anon, authenticated;
GRANT SELECT ON public.daily_post_media TO anon, authenticated;
GRANT INSERT, DELETE ON public.daily_post_media TO authenticated;
GRANT SELECT ON public.daily_post_comments TO anon, authenticated;
GRANT SELECT ON public.daily_post_translations TO anon, authenticated;
GRANT SELECT ON public.daily_post_preview_receipts TO authenticated;
GRANT SELECT ON public.daily_post_publication_jobs TO authenticated;
GRANT SELECT ON public.daily_post_audit_events TO authenticated;

GRANT ALL ON public.daily_posts TO service_role;
GRANT ALL ON public.daily_post_media TO service_role;
GRANT ALL ON public.daily_post_comments TO service_role;
GRANT ALL ON public.daily_post_preview_receipts TO service_role;
GRANT ALL ON public.daily_post_translations TO service_role;
GRANT ALL ON public.daily_post_publication_jobs TO service_role;
GRANT ALL ON public.daily_post_audit_events TO service_role;

COMMIT;
