# Supabase security boundary

`rpc_authorization_manifest.json` is the checked-in allowlist for the exposed `public` RPC boundary. It records the only functions currently approved for anonymous execution as `SECURITY DEFINER` functions. Every entry is classified as `public-read` and includes a product-level justification.

The manifest also records the 25 tables identified by Supabase as RLS-enabled without policies. These tables are intentionally RPC-only. Migration `20260918001000_close_advisor_rpc_only_table_access.sql` revokes direct table privileges from `public`, `anon`, and `authenticated`; it does not change row data or function behavior.

Migration `20260918040000_fail_closed_public_security_definer_execution.sql` performs a fail-closed sweep over public `SECURITY DEFINER` functions. It revokes `PUBLIC` and `anon` execution while preserving existing signed-in and service-role grants, then restores anonymous execution only for the ten manifest entries. Review the resulting RPC response payloads and public privacy model before applying this migration to Production.

Run the source-only check with:

```bash
python3 tools/security/verify_rpc_authorization_manifest.py
```

For a direct, read-only check in the Supabase SQL editor, open `production_boundary_check.sql` and paste only its SQL contents. It returns a `PASS` or `FAIL` summary for the anonymous RPC allowlist and the 25 RPC-only tables, followed by detail rows for unexpected anonymous functions or direct table privileges. Do not paste `cd`, `python3`, or the Python checker into the SQL editor.

For the performance-advisor findings, use `performance_index_review.sql`. It reports foreign-key constraints without covering indexes, estimated table size, modification volume, analyze timestamps, and a workload-based recommendation. It also reports index scan statistics. Because the current Production Marketplace and Service Centre tables are mostly empty and several relationships are already covered by composite indexes, no speculative index DDL is included; add indexes only after this review identifies a high-value workload candidate and its query plan is inspected.

Before applying any security migration, reconcile the manifest against **RTC Community Production** (`pbzzfzfgwzwdstvnwzqu`). The reconciliation must inspect `pg_proc`, `information_schema.role_routine_grants`, `pg_policies`, and table privileges. It must confirm that every anonymous `SECURITY DEFINER` function is in the allowlist, every RPC-only table has RLS enabled and no direct client grants, and every authenticated administrative function has an intentional role boundary.

The repository's Android debug workflow may still use compile-only non-production placeholders. That is separate from this security manifest and must not be interpreted as the deployment target for Production hardening. Credentials must remain outside the repository.
