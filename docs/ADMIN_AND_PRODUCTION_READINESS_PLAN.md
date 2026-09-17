# Administration Dashboard and Production Readiness Implementation Plan

> **Goal:** Make the administration workspace a reliable, user-friendly control plane for the entire resident frontend, then address the highest-priority production-readiness risks with verified migrations and tests.

**Architecture:** Keep the Android administration workspace as the single staff entry point, but replace legacy direct-table count queries with server-authoritative, bounded RPCs. Every mutable administrative action must expose a clear state, audit event, and preview/draft path before publication. Production fixes will be delivered as forward-only Supabase migrations and Edge Function changes, with source contracts and database tests.

**Tech Stack:** Kotlin/Jetpack Compose, Hilt, Supabase Kotlin client, PostgreSQL/Supabase RPCs and RLS, Supabase Edge Functions, pgTAP/source contract tests, GitHub Actions.

**Spec:** User request: upgrade the Administration Dashboard so it is efficiently connected to the entire frontend, easy to navigate, supports changes and previews, and then complete production-readiness work from highest priority downward.

## Global constraints

- Do not expose service-role credentials to Android clients.
- Keep privileged operations behind reviewed RPCs/Edge Functions with explicit role and MFA checks.
- Keep preview/draft state separate from published resident-facing state.
- Bound all dashboard queries and Edge Function request/work batches.
- Preserve existing RLS and fail-closed authorization behavior.
- Do not mutate the production Supabase project directly while implementing repository changes; deploy migrations/functions only through the normal reviewed release process.

## Phase 1 — Authoritative administration dashboard

### Task 1: Replace legacy moderation count aggregation

**Files:**
- Modify: `app/src/main/java/za/org/rtc/community/feature/administration/AdminDashboardViewModel.kt`
- Modify: `app/src/main/java/za/org/rtc/community/feature/administration/AdminWorkspace.kt` if metric labels/routes need alignment
- Test: administration ViewModel/contract tests

Use the existing authoritative `admin_get_moderation_dashboard_summary_v1()` RPC instead of selecting and decoding entire legacy tables. Preserve separate loading, authorization, stale-data, and error states. Remove the 15-second polling loop; use explicit refresh and lifecycle-scoped refresh, or a bounded refresh interval only when the screen is visible. Never convert query failures into a misleading zero count.

### Task 2: Add a frontend-surface health summary

**Files:**
- Modify: administration state/model files
- Modify: `AdminWorkspace.kt`
- Test: UI state and rendering tests

Expose a compact “Frontend surfaces” section showing the live status of content, notices/events, moderation, operations, marketplace, notifications, and configuration. Each tile must navigate to the existing route and show one of `Live`, `Drafts`, `Needs review`, `Unavailable`, or `Requires MFA`, based on actual state rather than static copy.

### Task 3: Make the dashboard action model explicit

**Files:**
- Modify: administration workspace components and relevant `RtcViewModel` admin methods
- Test: action/navigation contracts

Use a consistent action pattern: `Open`, `Preview`, `Save draft`, `Submit for review`, `Publish`, `Archive`, and `View audit`. Disable actions while work is in progress, show a user-readable result, and refresh the affected surface after success. Keep high-impact publication and operational actions behind existing confirmation/MFA workflows.

## Phase 2 — Preview and change management

### Task 4: Connect content preview to versioned backend state

Use `content_drafts`, `content_versions`, `app_content`, `ui_configuration_versions`, and the existing preview RPCs as the canonical change pipeline. The Android dashboard should show draft/published version, last editor, last publish time, and a preview route that uses draft data without mutating published resident content.

### Task 5: Add audit-first change history

Expose recent audit events for each dashboard module with bounded pagination and filters. Every mutation must show who changed what, when, why, and the resulting state. Avoid rendering raw metadata that may contain sensitive values.

## Phase 3 — Critical production fixes

### Task 6: Complete account deletion

Fix `supabase/functions/process-account-deletions/index.ts` so it claims bounded work, removes all user-owned records/storage through an idempotent workflow, calls the Auth Admin deletion API, updates request state, and reports partial failures accurately. Add integration/pgTAP coverage for retries and repeated invocations.

### Task 7: Close Supabase advisor findings by classification

- Revoke anonymous execution for security-definer RPCs that are not intentionally public.
- Ensure every exposed table has an intentional RLS policy or is moved out of the exposed schema.
- Add missing foreign-key indexes where dashboard queues and audit/history queries depend on them.
- Fix `auth_rls_initplan` findings by using `(select auth.uid())` in affected policies.

Do not blindly remove unused indexes; validate query plans and workload before changing them.

### Task 8: Restore reproducible Android builds

Commit the Gradle wrapper scripts/JAR, pin the wrapper version, and update local/CI documentation to use `./gradlew`. Add a CI check that the wrapper version matches the declared toolchain.

## Verification gates

1. Source regression contracts pass.
2. Android unit tests, lint, debug APK, and instrumentation compilation pass through the Gradle wrapper.
3. Edge authorization tests pass.
4. Supabase local reset and pgTAP tests pass.
5. Security and performance advisors are re-run after migrations.
6. A manual role-based smoke test covers resident, staff, moderator, content editor, and system administrator flows.
7. A production-readiness report records deferred work and any advisor findings intentionally accepted.
