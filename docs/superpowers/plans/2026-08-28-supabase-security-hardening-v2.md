# Supabase Security Hardening V2 Implementation Plan

> **Execution constraint:** Non-production only. Do not merge to `main`; do not deploy to production.

**Goal:** Reconcile the `security/supabase-hardening` donor intent against post-PR-#6 `main` and the deployed RTC Community Non-Production Supabase project, then establish explicit RPC-only denials, reusable Edge authorization, a defensible scheduler machine boundary, and auditable security evidence without weakening RLS or blindly removing `SECURITY DEFINER`.

**Starting main:** `ff083d90496632796104f98ebeae65b9c3b97eb2`

**Donor head:** `b9a0b42bfa5b3f427fb566d4b266195d0ab5ea2a`

**Working branch:** `hardening/supabase-security-v2`

## Donor reconciliation

1. `57e62fb971c8e50415d9751e6d533951eeff567d` / GAP-1 — **PORT WITH RECONCILIATION**. Preserve the existing false policies on older access/privacy/operations tables and add explicit restrictive deny policies only to the newer Marketplace/UI RPC-only tables that currently have zero policies. Keep direct anon/authenticated grants revoked.
2. `c465ca6440799ec33bd8dda9c472981ee100c117` / GAP-2 — **PORT WITH HARDENING**. Reuse the small shared-auth concept, add bounded JSON parsing and safe error mapping, and add a service-role-only database rate-limit claim.
3. `bbffe58fd3a1ba86e7aef2354632cc30a992cc22` / GAP-3 — **REPLACE**. Current Vault `rtc_alert_scheduler_publishable_key` is a modern `sb_publishable_…` key and cannot satisfy Edge gateway JWT verification. Use a distinct Vault-held legacy anon JWT for gateway verification, retain the independent Vault dispatch secret as the real machine authority, and keep the modern publishable key in the `apikey` header.
4. `b9a0b42bfa5b3f427fb566d4b266195d0ab5ea2a` / documentation — **PORT AND UPDATE** against actual post-PR-#6 schema, Edge deployment state, advisor findings and scheduler drift.

## Task 1 — Write failing security contracts first

**Files:**
- Create/replace `tools/tests/test_supabase_security_contracts.py`
- Create `supabase/functions/_shared/auth_test.ts`
- Create `supabase/tests/rls_rpc_only_tables_test.sql`
- Create/update `tools/tests/run_supabase_rls_tests.sh`

**Contracts:**
- all six active Edge Functions import the shared authorization primitive;
- shared auth rejects missing/malformed/invalid JWTs and wrong roles;
- shared auth accepts an allowed server-side role;
- bounded body parsing rejects oversized input;
- rate-limit denial maps to HTTP 429 without leaking internals;
- scheduler validation remains distinct from user authorization;
- all audited RPC-only tables retain no direct anon/authenticated CRUD and an explicit deny policy;
- scheduler source uses separate Vault names for modern API key, legacy JWT and independent dispatch secret;
- no production project ref appears in `supabase/config.toml`.

**RED evidence:** use current non-production SQL to demonstrate missing explicit Marketplace/UI deny policies, missing shared rate-limit primitive, and absent `rtc-community-alert-schedule`; repository contracts must fail before implementation.

## Task 2 — Add forward database hardening migrations

**Files:**
- `supabase/migrations/20260828090000_explicit_rpc_only_denials.sql`
- `supabase/migrations/20260828091000_edge_function_security_primitives.sql`
- `supabase/migrations/20260828092000_marketplace_authorization_hardening.sql`
- `supabase/migrations/20260828093000_restore_alert_scheduler_auth_chain.sql`

**Implementation:**
- add restrictive false policies to the Marketplace/UI tables currently policy-less while retaining RLS and revoking direct client grants;
- create `edge_function_rate_limits` as server-only/RPC-only and `claim_edge_function_rate_limit(...)` executable only by `service_role`;
- explicitly validate Marketplace invitation role/email/token bounds before persistence while preserving the existing owner/manager authority and separate ownership-transfer RPC;
- restore the missing alert cron job using pg_cron + pg_net + Vault, with `apikey` from the modern publishable key, `Authorization: Bearer` from a separate legacy anon JWT Vault entry, and the independent dispatch secret header;
- never embed credentials in repository SQL.

## Task 3 — Implement the minimal shared Edge authorization primitive

**Files:**
- `supabase/functions/_shared/auth.ts`
- `supabase/functions/_shared/auth_test.ts`

**Implementation:**
- authenticate bearer JWT with Supabase Auth;
- resolve privileged roles from `user_roles` server-side only;
- validate UUIDs and bounded text;
- parse JSON bodies with both declared and actual byte limits;
- claim database-backed rate-limit windows;
- create structured audit events without request payload/token logging;
- map internal failures to stable public errors;
- verify scheduler secret separately from user authentication.

## Task 4 — Integrate all six Edge Functions

**Files:**
- `supabase/functions/rtc-privacy-requests/index.ts`
- `supabase/functions/rtc-fcm-dispatch/index.ts`
- `supabase/functions/dispatch-community-alerts/index.ts`
- `supabase/functions/community-media-url/index.ts`
- `supabase/functions/report-community-post/index.ts`
- `supabase/functions/rtc-admin-ai/index.ts`
- `supabase/config.toml`

**Implementation:**
- user-called functions use shared JWT validation and trusted server-side authority;
- privileged functions keep existing role semantics unless audit evidence requires narrowing;
- request bodies and identifiers are bounded before privileged work;
- `report-community-post` no longer returns raw Postgres error messages;
- scheduled alert dispatch no longer returns internal failure codes;
- suitable user-called functions claim durable rate limits;
- `dispatch-community-alerts` remains scheduler-only and requires both gateway JWT verification and independent dispatch secret validation;
- source config is pinned to non-production for this hardening branch and sets JWT verification explicitly for every function.

## Task 5 — Apply and deploy to approved non-production only

1. Provision the dedicated Vault legacy anon JWT entry without putting the credential in source control.
2. Apply only the new forward migrations to project `eqwstpdjoineycrkhpht` in order.
3. Deploy all six Edge Functions to `eqwstpdjoineycrkhpht` with `verify_jwt=true`.
4. Verify scheduler job, Vault metadata, Edge versions/logs and no credential leakage.
5. Never call production project `pbzzfzfgwzwdstvnwzqu` during execution.

## Task 6 — Verify security behavior

**Database evidence:**
- anon denial;
- resident denial for privileged RPCs;
- authorized staff/admin success where a safe deterministic read can be exercised;
- direct-table denial on RPC-only surfaces;
- Marketplace owner/member authority invariants;
- audit writes;
- scheduler boundary and cron existence;
- deterministic rate-limit claim behavior inside a rolled-back transaction.

**Repository/Edge evidence:**
- `python3 tools/tests/run_contract_tests.py`
- `deno test supabase/functions/_shared/auth_test.ts`
- non-production pgTAP/equivalent checks;
- GitHub pull-request CI for `gradle --no-daemon testDebugUnitTest`, `lintDebug`, `assembleDebug`, and Compose smoke compilation because backend contracts are source-referenced by Android tests.

## Task 7 — Re-run advisors and publish the security record

**Files:**
- Update/create `docs/SUPABASE_SECURITY_GAP_LEDGER.md`
- Create `SUPABASE_SECURITY_INTEGRATION_REPORT.md`

Document starting/main/donor/final SHAs, every donor PORT/DROP/PARK/REPLACE decision, migration drift, all RLS/RPC-only findings, every advisor-flagged `SECURITY DEFINER` classification, Edge changes, scheduler chain/replay/retry model, Marketplace/Community/Brand findings, tests, advisor before/after, leaked-password dashboard action, residual risk, and exact production deployment order.

## Completion rule

Do not state completion, create a final integration claim, or recommend merge until fresh evidence exists for repository contracts, shared-auth tests, non-production database checks, Edge deployment state, scheduler behavior and advisor re-scan. Leave the branch unmerged.