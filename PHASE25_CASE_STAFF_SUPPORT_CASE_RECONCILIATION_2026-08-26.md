# Phase 25 — Case Staff Support-Case Reconciliation

**Date:** 2026-08-26  
**Workspace:** `/home/ubuntu/rtc-community-workspace` on local branch `recovery/nonproduction-baseline`  
**Environment boundary:** Approved isolated non-production Supabase project `eqwstpdjoineycrkhpht` only. **No production project, remote Git branch, Edge Function, secret, scheduler, or deployment was changed.**

## Purpose

The restored feature-branch archive contained resident support-case submission and messaging paths but did not contain the previously recovered source-controlled support-case migration or the Case Staff assignment-scoped Android workflow. This checkpoint reconciles the restored source with the already-existing, isolated non-production database contract without applying database DDL to that project.

> The reconstructed migration is a reproducibility artifact derived from read-only non-production catalogue inspection. It is not asserted to be an original production migration and must not be applied to an environment where the recovery migration has already been recorded.

## Verified Isolated Non-Production Contract

Read-only metadata inspection established the following contract. No application rows were queried.

| Area | Verified contract |
|---|---|
| Persisted state | `case_state`: `OPEN`, `IN_REVIEW`, `IN_PROGRESS`, `RESOLVED`, `CLOSED`. |
| Direct data access | `community_cases` and `case_messages` have RLS enabled and a `FOR ALL` deny policy using and checking `false`; clients must not select or mutate either table directly. |
| Resident operations | Authenticated residents can submit, list their own cases, list messages only for an accessible case, and add a message only for an accessible case through guarded RPCs. |
| Case access boundary | The private helper permits access only to the resident owner, currently assigned Case Staff member, or a verified System Administrator. |
| Case Staff list | `list_assigned_support_cases()` requires the `CASE_STAFF` role and returns cases assigned to the caller, limited to 100 ordered by `updated_at DESC`. |
| Case Staff update | `update_assigned_support_case_state(uuid, case_state, text)` requires a signed-in assigned staff member or verified System Administrator; valid state transitions are limited to `IN_REVIEW`, `IN_PROGRESS`, `RESOLVED`, and `CLOSED`; notes must be 3–1000 trimmed characters. Updates write an audit event and a case message server-side. |
| Administrative assignment | `assign_support_case(uuid, uuid, text)` remains server-side System Administrator functionality guarded by the existing MFA-aware access helper. No assignment UI was added in this checkpoint. |

## Source Changes

| File | Change |
|---|---|
| `supabase/migrations/20260825098500_recover_hardened_support_case_persistence.sql` | Restored the missing source-controlled non-production baseline migration: enum, tables, constraints, direct-access-deny RLS policies, private access helper, seven guarded RPCs, audit calls, index names used by the later performance cleanup, private-helper revocation, and authenticated RPC grants. The migration was **not applied**. |
| `core/Models.kt` | Added `AssignedSupportCase`, deliberately excluding `resident_id`, attachments, message contents, and assignment metadata. |
| `supabase/ProductionUxRepository.kt` | Added only `list_assigned_support_cases` and `update_assigned_support_case_state` wrappers. The list decoder deliberately ignores the server-returned resident identifier. The update wrapper client-validates the exact four states and a trimmed 3–1000 character note before calling the RPC. No direct `community_cases` or `case_messages` client-table path was introduced. |
| `data/RtcRepository.kt` | Added isolated assigned-case state. It loads only for a live `CASE_STAFF` Supabase session, clears on sign-out and role downgrade, and refreshes from the server only after a successful update. There is no optimistic local case-state transition. |
| `app/RtcViewModel.kt` | Added dedicated refresh/update actions and feedback state for the Case Staff workflow. |
| `MainActivity.kt` | Added a Case Staff-only panel within the existing protected **My Work** destination. It exposes assigned summary fields and a constrained state/note dialog only. It does not expose resident identity, case messages, attachments, staff assignment, case search, administrator list access, or general case management. |
| `tools/tests/test_android_compile_contracts.py` | Added source contracts for exact RPC names, state/note bounds, direct-table prohibition, Case Staff-only live-session scope, clearance semantics, non-optimistic refresh behavior, and migration RLS/grant safeguards. |

## Local Validation

All commands used the restored local Android SDK, JDK 21, and Gradle 8.13 with sequential execution and temporary 1 GB Gradle / 768 MB Kotlin daemon memory settings.

| Validation | Result | Notes |
|---|---:|---|
| Source-contract suite | **48/48 passed** | Includes the new Case Staff and reconstructed-migration assertions. |
| `testDebugUnitTest` | **Passed** | Compiler emitted only pre-existing non-blocking deprecation/future-annotation warnings. |
| `assembleDebug` | **Passed** | Local debug assembly only; it is not a release artifact or distribution approval. |
| `lintDebug` | **Passed** | No lint suppression or baseline was added. |
| Fresh synthetic `support-cases` session test | **Not run** | The ignored runtime properties, credential vault, state file, and TOTP vault were absent after environment restoration. The runner was inspected and not executed; no secrets, identities, rows, or synthetic records were created. |

## Scope and Readiness

This checkpoint restores source fidelity and a narrow Case Staff client path against the isolated contract. It does **not** establish production readiness, a release candidate, device-media validation, end-to-end notification behavior, or a fresh live-session authorization rerun. The overall status remains **NO-GO**.

## Follow-Up Controls

A future fresh real-session support-case run may occur only after owner-only ignored runtime configuration is intentionally restored and verified to target `eqwstpdjoineycrkhpht`. It must use the existing fail-closed runner, retain synthetic-only data, and update Phase 1 evidence only with the newly observed results. The separate source migration-history discrepancy involving the unsupported alert retry RPC remains unresolved and is outside this checkpoint.
