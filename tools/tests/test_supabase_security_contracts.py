from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
RPC_DENY = ROOT / "supabase" / "migrations" / "20260828090000_explicit_rpc_only_denials.sql"
EDGE_PRIMITIVES = ROOT / "supabase" / "migrations" / "20260828091000_edge_function_security_primitives.sql"
MARKETPLACE_HARDENING = ROOT / "supabase" / "migrations" / "20260828092000_marketplace_authorization_hardening.sql"
SCHEDULER = ROOT / "supabase" / "migrations" / "20260828093000_restore_alert_scheduler_auth_chain.sql"
SHARED_AUTH = ROOT / "supabase" / "functions" / "_shared" / "auth.ts"
PGTAP_TEST = ROOT / "supabase" / "tests" / "rls_rpc_only_tables_test.sql"
RLS_RUNNER = ROOT / "tools" / "tests" / "run_supabase_rls_tests.sh"
CONFIG = ROOT / "supabase" / "config.toml"
CI = ROOT / ".github" / "workflows" / "android-ci.yml"

EDGE_FUNCTIONS = (
    "rtc-admin-ai",
    "rtc-fcm-dispatch",
    "community-media-url",
    "rtc-privacy-requests",
    "report-community-post",
    "dispatch-community-alerts",
)
USER_EDGE_FUNCTIONS = (
    "rtc-admin-ai",
    "rtc-fcm-dispatch",
    "community-media-url",
    "rtc-privacy-requests",
    "report-community-post",
)


def _text(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def test_rpc_only_hardening_is_forward_only_transactional_and_scoped_to_missing_denials():
    text = _text(RPC_DENY).lower().strip()
    assert text.startswith("begin;")
    assert text.endswith("commit;")
    assert "as restrictive" in text
    assert "using (false)" in text
    assert "with check (false)" in text
    assert "revoke all on table" in text
    for table in (
        "marketplace_businesses",
        "marketplace_business_members",
        "marketplace_audit_events",
        "marketplace_submissions",
        "ui_configuration_assets",
        "ui_configuration_events",
        "ui_configuration_versions",
    ):
        assert table in text


def test_rate_limit_primitive_is_server_only_and_bounded():
    text = _text(EDGE_PRIMITIVES).lower()
    assert "edge_function_rate_limits" in text
    assert "claim_edge_function_rate_limit" in text
    assert "revoke all on function" in text
    assert "grant execute" in text and "service_role" in text
    assert "p_max_requests" in text and "p_window_seconds" in text
    assert "authenticated" in text and "revoke all on table" in text


def test_marketplace_invitation_boundary_rejects_ownership_role_and_bounds_inputs():
    text = _text(MARKETPLACE_HARDENING).lower()
    compact = "".join(text.split())
    assert "marketplace_invite_member" in text
    # The executable allowlist itself is the ownership-escalation boundary:
    # invitations can assign MANAGER or EDITOR only, never OWNER.
    assert "notin('manager','editor')" in compact
    assert "char_length" in text
    assert "token" in text
    assert "email" in text
    assert "from auth.users" in text
    assert "lower(v_invite.email) <> v_actor_email" in text


def test_all_six_edge_functions_use_shared_authorization_module():
    for function_name in EDGE_FUNCTIONS:
        text = _text(ROOT / "supabase" / "functions" / function_name / "index.ts")
        assert "../_shared/auth.ts" in text


def test_all_user_called_edge_functions_bound_request_body_bytes():
    for function_name in USER_EDGE_FUNCTIONS:
        text = _text(ROOT / "supabase" / "functions" / function_name / "index.ts")
        assert "readJsonObject" in text
        assert "await req.json()" not in text
        assert "await request.json()" not in text


def test_shared_guard_uses_trusted_auth_role_lookup_bounded_json_rate_limits_and_audit():
    text = _text(SHARED_AUTH)
    assert "auth.getUser" in text
    assert '.from("user_roles")' in text
    assert "readJsonObject" in text
    assert "TextEncoder" in text
    assert "claim_edge_function_rate_limit" in text
    assert 'source: "supabase-edge-function"' in text
    assert "verifySchedulerCaller" in text
    assert "publicError" in text
    assert '"EVIDENCE_REVIEWER"' in text


def test_report_edge_never_returns_raw_postgres_error_message():
    text = _text(ROOT / "supabase" / "functions" / "report-community-post" / "index.ts")
    assert "re?.message" not in text
    assert "reportError?.message" not in text
    assert "readJsonObject" in text
    assert "isUuid" in text


def test_report_fcm_oauth_uses_standard_jwt_bearer_grant_type():
    text = _text(ROOT / "supabase" / "functions" / "report-community-post" / "index.ts")
    assert "urn:ietf:params:oauth:grant-type:jwt-bearer" in text
    assert "urn:ietf:params:oauth-grant-type:jwt-bearer" not in text


def test_admin_ai_uses_current_shared_role_error_and_never_rpc_error_message():
    text = _text(ROOT / "supabase" / "functions" / "rtc-admin-ai" / "index.ts")
    assert "ROLE_REQUIRED" in text
    assert "AI_ROLE_REQUIRED" not in text
    assert "data.message" not in text


def test_scheduler_edge_does_not_return_internal_failure_code_to_client():
    text = _text(ROOT / "supabase" / "functions" / "dispatch-community-alerts" / "index.ts")
    assert "verifySchedulerCaller" in text
    assert "code});" not in text.replace(" ", "")
    assert "COMMUNITY_ALERT_DISPATCH_FAILED" in text


def test_scheduler_auth_uses_separate_modern_api_key_legacy_jwt_and_dispatch_secret():
    text = _text(SCHEDULER)
    assert "rtc_alert_scheduler_publishable_key" in text
    assert "rtc_alert_scheduler_legacy_anon_jwt" in text
    assert "rtc_alert_dispatch_secret" in text
    assert "'apikey'" in text
    assert "'Authorization'" in text
    assert "'Bearer '" in text
    assert "x-rtc-alert-dispatch-secret" in text
    assert "rtc-community-alert-schedule" in text


def test_source_config_is_nonproduction_pinned_and_explicitly_jwt_gates_every_edge_function():
    text = _text(CONFIG)
    assert 'project_id = "eqwstpdjoineycrkhpht"' in text
    assert "pbzzfzfgwzwdstvnwzqu" not in text
    for function_name in EDGE_FUNCTIONS:
        assert f"[functions.{function_name}]\nverify_jwt = true" in text


def test_pgtap_contract_checks_direct_denial_scheduler_and_rate_limit_boundaries():
    text = _text(PGTAP_TEST).lower()
    assert "has_table_privilege" in text
    assert "marketplace_businesses" in text
    assert "ui_configuration_versions" in text
    assert "edge_function_rate_limits" in text
    assert "rtc-community-alert-schedule" in text
    assert text.rstrip().endswith("rollback;")


def test_rls_runner_requires_explicit_nonproduction_connection_and_refuses_production_ref():
    text = _text(RLS_RUNNER)
    assert "SUPABASE_TEST_DB_URL" in text
    assert "eqwstpdjoineycrkhpht" in text
    assert "pbzzfzfgwzwdstvnwzqu" in text
    assert "exit 1" in text
    assert "pg_prove" in text


def test_android_ci_runs_shared_auth_deno_tests():
    text = _text(CI)
    assert "denoland/setup-deno" in text
    assert "deno test supabase/functions/_shared/auth_test.ts" in text


def test_migrations_never_trust_user_metadata_or_create_shadow_moderation_domains():
    migration_text = "\n".join(
        _text(path).lower()
        for path in sorted((ROOT / "supabase" / "migrations").glob("*.sql"))
    )
    assert "user_metadata" not in migration_text
    for shadow_table in ("reports", "business_submissions", "support_requests"):
        assert f"create table public.{shadow_table}" not in migration_text
        assert f"create table if not exists public.{shadow_table}" not in migration_text
