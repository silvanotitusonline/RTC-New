#!/usr/bin/env bash
set -euo pipefail

: "${SUPABASE_TEST_DB_URL:?Set SUPABASE_TEST_DB_URL to a non-production PostgreSQL URL}"
: "${SUPABASE_TEST_PROJECT_REF:?Set SUPABASE_TEST_PROJECT_REF to the non-production project ref}"

case "$SUPABASE_TEST_PROJECT_REF" in
  pbzzfzfgwzwdstvnwzqu|prod|production)
    echo 'refusing production load test: RTC Community Production is not a staging target.' >&2
    exit 1
    ;;
esac

command -v psql >/dev/null || { echo 'psql is required' >&2; exit 1; }

# Supabase PostgreSQL connections must use TLS unless the caller explicitly
# provides a stricter connection setting.
export PGSSLMODE="${PGSSLMODE:-require}"

required_objects="$(psql "$SUPABASE_TEST_DB_URL" -v ON_ERROR_STOP=1 -Atc "
  select concat_ws('|',
    to_regclass('public.daily_posts'),
    to_regclass('public.daily_post_comments'),
    to_regprocedure('public.daily_post_comments_page_v2(uuid,timestamptz,uuid,integer)'),
    to_regprocedure('public.daily_post_count_drift_check_v1()')
  )
")"

IFS='|' read -r daily_posts_table comments_table comments_rpc drift_rpc <<< "$required_objects"
for object_name in daily_posts_table comments_table comments_rpc drift_rpc; do
  if [[ -z "${!object_name}" ]]; then
    echo "Required staging object is missing: ${object_name}" >&2
    echo 'Apply the current Daily Post migrations to the non-production project before retrying.' >&2
    exit 1
  fi
done

post_id="${DAILY_POST_LOAD_POST_ID:-}"
if [[ -z "$post_id" ]]; then
  post_id="$(psql "$SUPABASE_TEST_DB_URL" -v ON_ERROR_STOP=1 -Atc \
    "select id from public.daily_posts where state = 'PUBLISHED' order by published_at desc nulls last, created_at desc limit 1")"
fi

if [[ -z "$post_id" ]]; then
  echo 'No published Daily Post fixture was found in the staging project.' >&2
  echo 'Set DAILY_POST_LOAD_POST_ID or create one non-production published fixture, then retry.' >&2
  exit 1
fi

if [[ ! "$post_id" =~ ^[0-9a-fA-F-]{36}$ ]]; then
  echo 'DAILY_POST_LOAD_POST_ID must be a UUID.' >&2
  exit 1
fi

export DAILY_POST_LOAD_POST_ID="$post_id"
printf 'Running bounded Daily Post query-plan checks for project %s, post %s\n' \
  "$SUPABASE_TEST_PROJECT_REF" "$DAILY_POST_LOAD_POST_ID"

psql "$SUPABASE_TEST_DB_URL" -v ON_ERROR_STOP=1 -v post_id="$DAILY_POST_LOAD_POST_ID" <<'SQL'
explain (analyze, buffers, format json)
select * from public.daily_post_comments_page_v2(:'post_id'::uuid, null, null, 50);

explain (analyze, buffers, format json)
select c.id, c.post_id, c.state, c.created_at
  from public.daily_post_comments c
 where c.post_id = :'post_id'::uuid
   and c.state = 'VISIBLE'
 order by c.created_at desc, c.id desc
 limit 50;

select public.daily_post_count_drift_check_v1();
SQL

if command -v pgbench >/dev/null && [[ "${RUN_PGBENCH:-0}" == "1" ]]; then
  pgbench \
    --no-vacuum \
    --client="${PGBENCH_CLIENTS:-4}" \
    --jobs="${PGBENCH_JOBS:-2}" \
    --time="${PGBENCH_SECONDS:-30}" \
    "$SUPABASE_TEST_DB_URL"
fi

echo 'non-production Daily Post load/query-plan checks passed'
