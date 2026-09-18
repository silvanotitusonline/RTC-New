# RTC Implementation Skills Catalog

This document records the skills used during today’s RTC Community Android work and the additional skills that are most useful for improving future implementation quality. It is organized around the actual work completed: defect diagnosis, Compose UI changes, navigation verification, account settings behavior, Supabase-backed persistence, source-contract testing, and repository integration.

## Skills used today

| Skill | Source | How it was used |
| --- | --- | --- |
| **automation-and-scheduling** | `/home/ubuntu/skills/automation-and-scheduling` | Reviewed before working with external API integrations and persistent service behavior, especially xKiro and Supabase-related workflows. |
| **writing-plans** | `/home/ubuntu/skills/writing-plans` | Used to define a bounded implementation plan before making the multi-subsystem Android defect fixes. |
| **vue-best-practices** | `/home/ubuntu/.agents/skills/vue-best-practices` | Used to inspect the repository’s frontend/test surface and verify Vue-oriented integration guidance where applicable. |
| **navigation-3** | `/home/ubuntu/.agents/skills/navigation-3` | Used to assess whether the Android project had migrated to Navigation 3. The audit confirmed that the project still uses Navigation 2 APIs and dependencies. |
| **apple-design** | `/home/ubuntu/upload/SKILL(3).md` | Used to guide restrained interaction and visual refinements: responsive feedback, spatial consistency, material hierarchy, accessibility, and smooth content expansion. |
| **Android/Compose navigation guidance** | `navigation-3` references and existing project navigation contracts | Used to preserve the existing `NavHost`/`NavController` architecture rather than introducing an unrequested high-risk migration. |
| **Repository source-contract testing** | `tools/tests/run_contract_tests.py` and focused contracts | Used to validate profile navigation, Home Assistant replacement, Community Map removal, media recovery, idempotency, account settings, and theme persistence without requiring a configured Android SDK. |

## Skills most applicable to the completed work

### 1. Diagnosing bugs

Recommended source: `/home/ubuntu/.agents/skills/diagnosing-bugs`

This is the best fit for tracing failures such as an unclickable profile menu, lost picker URI permissions, stuck post composers, image propagation races, duplicate submissions, and stale synchronized views. The preferred workflow is:

1. Reproduce or characterize the symptom.
2. Trace the event from UI callback through ViewModel/coordinator/repository/backend.
3. Identify the first broken state transition rather than patching only the visible symptom.
4. Add a regression contract before or alongside the fix.
5. Validate both success and failure paths.

### 2. Implement

Recommended source: `/home/ubuntu/.agents/skills/implement`

Useful for carrying a scoped change from requirements through source edits and validation. It is especially applicable to the Home screen replacement, removal of Community Map entry points, and functional Account Preferences implementation.

### 3. Codebase design

Recommended source: `/home/ubuntu/.agents/skills/codebase-design`

Useful for preserving the project’s boundaries: Compose UI, ViewModels, coordinators, repositories, Supabase adapters, navigation, and source contracts. This helps avoid placing persistence or navigation logic directly inside reusable UI components.

### 4. Improve codebase architecture

Recommended source: `/home/ubuntu/.agents/skills/improve-codebase-architecture`

Applicable to the shared dashboard stream, repository-level state propagation, account preference persistence, and the decision to reuse the existing RTC Assistant card instead of duplicating assistant behavior on Home.

### 5. TDD and regression contracts

Recommended source: `/home/ubuntu/.agents/skills/tdd`

Applicable whenever a defect is fixed or a product surface is removed. For this repository, source contracts are particularly valuable because they can verify route wiring, feature removal, callback behavior, migration presence, and persistence contracts even when local Android SDK/device execution is unavailable.

### 6. Code review

Recommended source: `/home/ubuntu/.agents/skills/code-review`

Useful after changes are implemented to check for accidental scope expansion, stale imports, placeholder callbacks, accessibility regressions, incorrect descriptions, duplicate state ownership, and migration compatibility.

### 7. Review animations

Recommended source: `/home/ubuntu/skills/review-animations`

Applicable to Apple-style polish and Compose motion review. The key checks are interruptibility, appropriate easing/spring behavior, reduced-motion support, no input lockout during transitions, and avoiding animation that does not communicate state.

### 8. CI/CD and automation

Recommended source: `/home/ubuntu/skills/ci-cd-and-automation`

Applicable to ensuring the source-contract suite, Android unit tests, lint, and debug/release build gates run consistently in GitHub Actions. It is especially useful here because the local sandbox lacks the Android SDK while CI is expected to provide it.

### 9. Validate data

Recommended source: `/home/ubuntu/skills/validate-data`

Applicable to verifying synchronization claims, dashboard/report consistency, notification preference persistence, and migration effects. It helps distinguish a UI refresh from a genuinely authoritative data update.

### 10. SQL queries and SQL optimization

Recommended sources:

- `/home/ubuntu/skills/sql-queries`
- `/home/ubuntu/skills/sql-optimization-patterns`

Applicable to Supabase RPC contracts, idempotency indexes, migration verification, pagination, and ensuring production queries remain bounded and correctly indexed.

### 11. Manus configuration and Supabase integration

Recommended sources:

- `/home/ubuntu/skills/manus-config`
- Supabase MCP connector guidance

Applicable when inspecting connectors, identifying the correct RTC production project, applying migrations, verifying migration history, checking table/index state, and running security/performance advisors.

### 12. Dispatching parallel agents and workflow composition

Recommended sources:

- `/home/ubuntu/skills/dispatching-parallel-agents`
- `/home/ubuntu/skills/workflow-composer`

Applicable when independently auditing multiple feature areas such as Home, profile settings, media handling, navigation, Supabase state, and tests. Parallel work should be used only when the subtasks have clear boundaries and do not edit the same files concurrently.

### 13. Technical writing and writing plans

Recommended sources:

- `/home/ubuntu/skills/technical-writing`
- `/home/ubuntu/skills/writing-plans`

Applicable for implementation plans, defect reports, migration notes, release limitations, and reusable project documentation such as this catalog.

## Recommended skill combinations by task

### Compose UI feature or redesign

Use **implement**, **codebase-design**, **apple-design**, **review-animations**, and **tdd**. Start with the existing design tokens and shared components, define the interaction state machine, implement the smallest reusable surface, then add source contracts for required labels, callbacks, and state transitions.

### Navigation or route change

Use **navigation-3**, **codebase-design**, **diagnosing-bugs**, and **tdd**. First identify whether the project is actually on Navigation 2 or Navigation 3. Do not mix APIs. Verify deep links, back behavior, protected routes, and profile/settings entry points.

### Account preferences and persistence

Use **diagnosing-bugs**, **implement**, **validate-data**, **sql-queries**, and **tdd**. Every visible description should correspond to an actual callback and persistence path. Test optimistic UI state, failure feedback, session rehydration, local fallback behavior, and authenticated persistence.

### Supabase migration or production schema change

Use **automation-and-scheduling**, **manus-config**, **sql-queries**, **sql-optimization-patterns**, **validate-data**, and **code-review**. Identify the project first, review the exact SQL, apply forward-only changes, verify migration history and indexes, and run security/performance checks.

### Repository integration and release preparation

Use **code-review**, **ci-cd-and-automation**, **resolving-merge-conflicts**, **writing-plans**, and **technical-writing**. Inspect branch state, run focused and full contracts, run the available build gates, document environmental limitations, commit with a scoped message, and verify the final branch and remote state.

## Practical checklist

- Trace UI actions to their real persistence or navigation endpoint.
- Keep descriptions truthful; remove placeholder copy when functionality is unavailable.
- Prefer shared ViewModels, repositories, and components over duplicated feature logic.
- Preserve existing navigation architecture unless a migration is explicitly requested.
- Use semantic design tokens rather than scattered dimensions or colors.
- Make settings changes immediate, persistent, reversible, and observable.
- Add focused regression contracts for every defect or feature removal.
- Run the full source-contract suite after focused tests pass.
- Treat a missing Android SDK as a validation limitation, not as a passing build.
- Verify Git status, commit contents, branch ancestry, and remote state before reporting completion.
