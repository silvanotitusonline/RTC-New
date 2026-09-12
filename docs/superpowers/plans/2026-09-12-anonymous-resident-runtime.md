# Anonymous Resident Runtime Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove resident authenticated identity as an app requirement while preserving anonymous continuity, server-authoritative civic truth, and authenticated staff/admin protection.

**Architecture:** Normal resident navigation becomes anonymous-first and never depends on a Supabase session. A locally generated installation identifier replaces authenticated user IDs only for local persistence/idempotency and is explicitly non-authoritative. Staff/admin flows keep Supabase authentication and server role guards. Existing authoritative Community/Public Reports wrappers remain the source of truth for server-visible state.

**Tech Stack:** Kotlin, Jetpack Compose, Hilt, Room, DataStore, Supabase Kotlin, WorkManager, GitHub Actions, Deno Edge tests.

**Spec:** `docs/superpowers/specs/2026-09-12-anonymous-resident-runtime-design.md`

## Global Constraints

- Resident launch must not require sign-in.
- Installation identity is local continuity only and must never grant auth or roles.
- Privileged staff/admin access still requires a live Supabase session plus server authorization.
- Client-visible success for civic/community mutations requires server confirmation.
- Existing local drafts/outbox must not be silently reassigned across previous authenticated owners.
- `main` remains untouched until the integration branch compiles and verification evidence is available.

---

### Task 1: Map and remove resident auth gating

**Files:**
- Modify the root navigation/session coordinator files discovered under `app/src/main/java/za/org/rtc/community/`.
- Test: `tools/tests/test_anonymous_resident_runtime.py`.

**Interfaces:**
- Consumes: existing app/session/navigation state.
- Produces: resident startup path that enters public resident navigation without Supabase authentication.

- [ ] **Step 1: Write failing source contracts** asserting resident startup has no sign-in gate and privileged routes remain guarded.
- [ ] **Step 2: Run the targeted source contract and verify failure.**
- [ ] **Step 3: Change startup/navigation state so normal residents enter the public shell directly, while staff/admin auth routes remain explicit protected routes.**
- [ ] **Step 4: Re-run the source contract and verify pass.**
- [ ] **Step 5: Commit the resident navigation/auth-gate change.**

### Task 2: Introduce installation-scoped local continuity identity

**Files:**
- Create: focused installation identity provider under the existing data/session package.
- Modify: draft/outbox persistence accessors and dependency injection bindings as required.
- Test: `tools/tests/test_anonymous_resident_runtime.py` plus existing draft/outbox contracts.

**Interfaces:**
- Produces: `InstallationIdentity.currentId(): String` (or repository-consistent equivalent) used only for local persistence/idempotency.
- Must not produce `SessionAuthority`, `RtcRole`, JWT claims, or protected-route authorization.

- [ ] **Step 1: Add a failing contract that prohibits installation identity from creating `SUPABASE_AUTH` or privileged roles.**
- [ ] **Step 2: Run and verify failure.**
- [ ] **Step 3: Implement a persisted installation UUID/provider using the project’s existing local preference pattern.**
- [ ] **Step 4: Re-key anonymous resident draft/outbox access through this provider without reassigning legacy private rows.**
- [ ] **Step 5: Run local persistence/source contracts and verify pass.**
- [ ] **Step 6: Commit.**

### Task 3: Decouple privileged authentication from resident continuity

**Files:**
- Modify: authentication coordinator, session model, admin guard, protected-route entry points.
- Test: existing truth-boundary tests plus new anonymous-runtime contracts.

**Interfaces:**
- Resident domain: no authenticated identity required.
- Privileged domain: live Supabase user + server guard required.

- [ ] **Step 1: Add tests that privileged sign-out cannot delete anonymous resident drafts/outbox.**
- [ ] **Step 2: Remove resident-facing synthesized/cached authenticated authority paths still reachable from runtime.**
- [ ] **Step 3: Ensure staff/admin login state is isolated from anonymous continuity state.**
- [ ] **Step 4: Re-run auth/admin source contracts and Deno shared auth tests.**
- [ ] **Step 5: Commit.**

### Task 4: Make anonymous capability handling truthful

**Files:**
- Modify: Community/Public Reports/resident feature view models or repositories only where they currently assume authenticated ownership.
- Test: truth-boundary contracts.

**Interfaces:**
- Server-confirmed operations stay authoritative.
- Unsupported anonymous operations return explicit failure/capability state; no fake user/session is created.

- [ ] **Step 1: Identify resident mutations that still read authenticated user/session solely to obtain an owner ID.**
- [ ] **Step 2: For server contracts supporting anonymous access, remove unnecessary client auth preconditions.**
- [ ] **Step 3: For contracts that require authentication, fail closed with a typed capability error rather than manufacturing identity or success.**
- [ ] **Step 4: Verify Community/Public Reports truth-boundary contracts.**
- [ ] **Step 5: Commit.**

### Task 5: Repair Kotlin compilation blockers on the integration branch

**Files:**
- Modify only files reported by `:app:compileDebugKotlin`, including existing administration, notification, Community, Marketplace, Service Centre, navigation files where errors are genuine source drift.
- Test: `gradle --no-daemon testDebugUnitTest`.

**Interfaces:**
- No new product behavior beyond restoring intended existing references/imports/signatures.

- [ ] **Step 1: Run compile and capture exact diagnostics.**
- [ ] **Step 2: Fix import/signature/reference errors in minimal focused commits, preserving existing behavior.**
- [ ] **Step 3: Re-run `testDebugUnitTest` until Kotlin compilation succeeds or a new independent blocker is proven.**
- [ ] **Step 4: Commit each coherent repair group.**

### Task 6: Verification and release gate

**Files:**
- Modify: `.github/workflows/verify-truth-boundary.yml` only if verification commands need correction.
- Test: source contracts, Deno auth tests, JVM tests, debug APK/lint where environment permits.

**Interfaces:**
- Produces: verifiable evidence for the anonymous resident + privileged admin trust split.

- [ ] **Step 1: Run targeted anonymous-runtime and truth-boundary source contracts.**
- [ ] **Step 2: Run `deno test supabase/functions/_shared/auth_test.ts`.**
- [ ] **Step 3: Run `gradle --no-daemon testDebugUnitTest`.**
- [ ] **Step 4: Run debug APK assembly and lint after compilation succeeds.**
- [ ] **Step 5: Run the full production contract suite; classify inherited unrelated failures separately.**
- [ ] **Step 6: Open an integration PR only after the branch is source-coherent and report remaining release blockers without overstating readiness.**
