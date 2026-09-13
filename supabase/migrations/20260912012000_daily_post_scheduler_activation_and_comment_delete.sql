BEGIN;

-- Author deletion redacts the body. Visible and moderated comments still require validated text.
ALTER TABLE public.daily_post_comments
    DROP CONSTRAINT IF EXISTS daily_post_comments_body_check;
ALTER TABLE public.daily_post_comments
    ADD CONSTRAINT daily_post_comments_body_check CHECK (
        (state = 'DELETED' AND body = '') OR
        (state <> 'DELETED' AND char_length(btrim(body)) BETWEEN 1 AND 2000)
    );

-- Daily Post scheduling owns a dedicated secret. Do not reuse the retired Community Alert
-- dispatcher verifier; that trust boundary was intentionally removed by the recovery baseline.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM vault.secrets WHERE name = 'rtc_daily_post_scheduler_secret') THEN
        PERFORM vault.create_secret(
            gen_random_uuid()::text || gen_random_uuid()::text,
            'rtc_daily_post_scheduler_secret',
            'RTC internal secret for the Daily Post publication scheduler'
        );
    END IF;
END;
$$;

CREATE OR REPLACE FUNCTION public.assert_daily_post_scheduler_secret(p_secret text)
RETURNS boolean
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, vault, pg_temp
AS $$
DECLARE
    v_expected text;
BEGIN
    IF p_secret IS NULL OR char_length(p_secret) > 512 THEN
        RETURN false;
    END IF;
    SELECT decrypted_secret
      INTO v_expected
      FROM vault.decrypted_secrets
     WHERE name = 'rtc_daily_post_scheduler_secret';
    RETURN v_expected IS NOT NULL AND p_secret = v_expected;
END;
$$;
REVOKE ALL ON FUNCTION public.assert_daily_post_scheduler_secret(text) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.assert_daily_post_scheduler_secret(text) TO service_role;

-- The earlier production foundation already provisions these two non-secret endpoint values in
-- Vault. Fail migration replay explicitly if that prerequisite is absent rather than installing a
-- scheduler that can never reach the Edge Function.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM vault.secrets WHERE name = 'rtc_alert_scheduler_project_url') THEN
        RAISE EXCEPTION 'RTC scheduler project URL is not provisioned in Vault';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM vault.secrets WHERE name = 'rtc_alert_scheduler_publishable_key') THEN
        RAISE EXCEPTION 'RTC scheduler publishable key is not provisioned in Vault';
    END IF;
END;
$$;

SELECT cron.unschedule(jobid)
FROM cron.job
WHERE jobname = 'rtc-daily-post-scheduler';

SELECT cron.schedule(
    'rtc-daily-post-scheduler',
    '* * * * *',
    $cron$
        SELECT net.http_post(
            url := (SELECT decrypted_secret FROM vault.decrypted_secrets WHERE name = 'rtc_alert_scheduler_project_url') || '/functions/v1/daily-post-scheduler',
            headers := jsonb_build_object(
                'Content-Type', 'application/json',
                'apikey', (SELECT decrypted_secret FROM vault.decrypted_secrets WHERE name = 'rtc_alert_scheduler_publishable_key'),
                'x-rtc-daily-post-scheduler-secret', (SELECT decrypted_secret FROM vault.decrypted_secrets WHERE name = 'rtc_daily_post_scheduler_secret')
            ),
            body := jsonb_build_object('source', 'supabase-cron')
        );
    $cron$
);

COMMIT;
