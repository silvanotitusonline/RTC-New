#!/usr/bin/env bash
set -euo pipefail

NONPROD_REF="eqwstpdjoineycrkhpht"
PRODUCTION_REF="pbzzfzfgwzwdstvnwzqu"

if [[ -z "${SUPABASE_TEST_DB_URL:-}" ]]; then
  echo "SUPABASE_TEST_DB_URL must contain the approved non-production PostgreSQL connection URL." >&2
  exit 1
fi

if [[ -z "${SUPABASE_TEST_PROJECT_REF:-}" ]]; then
  echo "SUPABASE_TEST_PROJECT_REF must be explicitly set to the approved non-production project ref." >&2
  exit 1
fi

if [[ "${SUPABASE_TEST_PROJECT_REF}" == "${PRODUCTION_REF}" ]] || [[ "${SUPABASE_TEST_DB_URL}" == *"${PRODUCTION_REF}"* ]]; then
  echo "Refusing to run Daily Post transition tests against RTC Community Production." >&2
  exit 1
fi

if [[ "${SUPABASE_TEST_PROJECT_REF}" != "${NONPROD_REF}" ]] || [[ "${SUPABASE_TEST_DB_URL}" != *"${NONPROD_REF}"* ]]; then
  echo "Daily Post transition tests are restricted to RTC Community Non-Production (${NONPROD_REF})." >&2
  exit 1
fi

command -v psql >/dev/null 2>&1 || { echo "psql is required." >&2; exit 1; }
command -v pg_prove >/dev/null 2>&1 || { echo "pg_prove is required." >&2; exit 1; }

psql "${SUPABASE_TEST_DB_URL}" -v ON_ERROR_STOP=1 \
  -c "create extension if not exists pgtap with schema extensions;" >/dev/null

pg_prove --verbose --dbname "${SUPABASE_TEST_DB_URL}" \
  supabase/tests/daily_post_comment_count_transitions.sql \
  supabase/tests/daily_post_comment_security_performance.sql
