# Supabase security boundary

`rpc_authorization_manifest.json` is the checked-in allowlist for the exposed `public` RPC boundary. It records the only functions currently approved for anonymous execution as `SECURITY DEFINER` functions. Every entry is classified as `public-read` and includes a product-level justification.

The manifest also records the 25 tables identified by Supabase as RLS-enabled without policies. These tables are intentionally RPC-only. Migration `20260918001000_close_advisor_rpc_only_table_access.sql` revokes direct table privileges from `public`, `anon`, and `authenticated`; it does not change row data or function behavior.

Run the source-only check with:

```bash
python3 tools/security/verify_rpc_authorization_manifest.py
```

Before applying any security migration, reconcile the manifest against the approved non-production project. The reconciliation must inspect `pg_proc`, `information_schema.role_routine_grants`, `pg_policies`, and table privileges. It must confirm that every anonymous `SECURITY DEFINER` function is in the allowlist, every RPC-only table has RLS enabled and no direct client grants, and every authenticated administrative function has an intentional role boundary. Do not run this test against the Production project.

A future live checker may use `SUPABASE_TEST_DB_URL`, but credentials must remain outside the repository and the command must refuse the Production project reference.
