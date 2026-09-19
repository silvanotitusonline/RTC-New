#!/usr/bin/env bash
set -euo pipefail

: "${SUPABASE_TEST_DB_URL:?Set SUPABASE_TEST_DB_URL to a non-production database URL}"
: "${SUPABASE_TEST_PROJECT_REF:?Set SUPABASE_TEST_PROJECT_REF to the non-production project ref}"
: "${DAILY_POST_LOAD_POST_ID:?Set DAILY_POST_LOAD_POST_ID to a staging Daily Post UUID}"

case "$SUPABASE_TEST_PROJECT_REF" in
  pbzzfzfgwzwdstvnwzqu|prod|production) echo 'refusing production load test' >&2; exit 1;;
esac
command -v psql >/dev/null || { echo 'psql is required' >&2; exit 1; }

psql "$SUPABASE_TEST_DB_URL" -v ON_ERROR_STOP=1 -v post_id="$DAILY_POST_LOAD_POST_ID" <<'SQL'
explain (analyze, buffers, format json)
select * from public.daily_post_comments_page_v2(:'post_id'::uuid, null, null, 50);
explain (analyze, buffers, format json)
select c.* from public.daily_post_comments c where c.post_id=:'post_id'::uuid and c.state='VISIBLE' order by c.created_at desc,c.id desc limit 50;
select public.daily_post_count_drift_check_v1();
SQL

if command -v pgbench >/dev/null && [[ "${RUN_PGBENCH:-0}" == "1" ]]; then
  pgbench --no-vacuum --client="${PGBENCH_CLIENTS:-4}" --jobs="${PGBENCH_JOBS:-2}" --time="${PGBENCH_SECONDS:-30}" "$SUPABASE_TEST_DB_URL"
fi

echo 'non-production Daily Post load/query-plan checks passed'
