#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$repo_root"

python3 tools/tests/run_contract_tests.py
python3 tools/security/verify_rpc_authorization_manifest.py
python3 -m json.tool supabase/security/rpc_authorization_manifest.json >/dev/null
bash -n tools/tests/run_daily_post_comment_transition_tests.sh
python3 - <<'PY'
from pathlib import Path
migration = Path('supabase/migrations/20260919098000_daily_post_operational_health.sql').read_text()
required = [
    'daily_post_rpc_metric_record_v1',
    'daily_post_rpc_health_v1',
    'daily_post_report_queue_v1',
    'daily_post_report_transition_v1',
    'daily_post_report_summary_v1',
    'daily_post_count_drift_check_v1',
    'daily_post_comment_reports_reporter_idx',
    'daily_post_comment_reports_reviewed_by_idx',
    'daily_post_audit_events_actor_idx',
]
missing = [item for item in required if item not in migration]
if missing:
    raise SystemExit(f'missing release-gate migration assertions: {missing}')
print('release gate SQL artifact checks passed')
PY
git diff --check
printf 'release gate passed\n'
