## Change summary

- Exact base SHA:
- Target branch:
- Scope owner:

## Architecture and ownership

- [ ] Changes stay inside the assigned feature/domain ownership boundary.
- [ ] Central integration-owner files were not edited unless this PR is the designated integration pass.
- [ ] Root `RtcViewModel`, root `RtcRepository`, shared `AppModule.kt`, and Room schema were not expanded without explicit ownership.
- [ ] Legacy routes/behaviour remain available until the approved replacement passes integration tests.

## Database and security

- [ ] New migrations were created as forward-only migrations and were not renamed/reordered after remote application.
- [ ] New tables/functions have explicit RLS/grants/revocations and least-privilege execution.
- [ ] SECURITY DEFINER functions use fixed `search_path`, schema-qualified access, caller/role assertions, classification, and allow/deny tests.
- [ ] Sensitive IDs, message content, exact location, credentials, tokens, and raw backend errors are not emitted to logs/analytics/FCM/UI errors.
- [ ] Production Supabase was not changed unless a separate explicit Production approval is linked here.

## Verification

- [ ] `python3 tools/tests/run_contract_tests.py`
- [ ] `deno test supabase/functions/_shared/auth_test.ts`
- [ ] `supabase db reset`
- [ ] `supabase test db`
- [ ] `gradle --no-daemon --stacktrace testDebugUnitTest`
- [ ] `gradle --no-daemon --stacktrace lintDebug`
- [ ] `gradle --no-daemon --stacktrace assembleDebug`
- [ ] `gradle --no-daemon --stacktrace assembleDebugAndroidTest`
- [ ] Exact PR-head CI is green.

## Rollout

- [ ] Release-facing feature flags default fail-closed/off until backend contracts are present and smoke-tested.
- [ ] Configuration prerequisites and environment-specific actions are documented.
- [ ] Production promotion is explicitly excluded from this PR unless separately approved.

## Evidence / notes

Record command results, schema/RPC signatures, advisor findings, compatibility evidence, configuration prerequisites, and known limitations here.
