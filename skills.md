# RTC Engineering Skills Reference

**Purpose:** This document is a detailed operating guide for the skills that were used during the RTC Community Android work and for the additional skills that are most useful for future implementation. It explains what each skill is for, when to use it, how to apply it, what a good result looks like, and how it maps to this repository.

**Repository context:** RTC is a native Jetpack Compose Android application with a shared design system, ViewModels and coordinators, repository adapters, Supabase migrations and Edge Functions, typed domain models, centralized navigation, source-level regression contracts, and GitHub Actions build gates.

**Primary principle:** A skill is not merely a topic label. It is a repeatable method for producing a better implementation, a sharper feedback loop, a safer change, or a more maintainable result.

---

## 1. Skill selection guide

Use the following decision table before starting work.

| Situation | Start with | Add these skills |
| --- | --- | --- |
| A user reports a broken or intermittent feature | `diagnosing-bugs` | `tdd`, `code-review`, `validate-data` |
| A new Compose screen or redesign is requested | `implement` | `codebase-design`, `apple-design`, `review-animations`, `tdd` |
| A route, deep link, back-stack, or profile entry point changes | `navigation-3` | `codebase-design`, `diagnosing-bugs`, `tdd` |
| A setting must actually persist and affect the app | `implement` | `validate-data`, `sql-queries`, `tdd`, `code-review` |
| A Supabase schema or RPC changes | `manus-config` | `sql-queries`, `sql-optimization-patterns`, `validate-data`, `code-review` |
| Multiple independent feature areas must be audited | `dispatching-parallel-agents` | `workflow-composer`, `code-review` |
| The repository is ready for integration | `code-review` | `ci-cd-and-automation`, `writing-plans`, `technical-writing` |
| The task is a report, plan, or reusable guide | `technical-writing` | `writing-plans`, `knowledge-synthesis` |
| The change includes motion, sheets, drawers, or transitions | `apple-design` | `review-animations`, `image-processing` only if visual assets are involved |
| A Vue frontend is involved | `vue-best-practices` | `vue-router-best-practices`, `vue-pinia-best-practices`, `vue-testing-best-practices` |

### The standard implementation loop

1. **Clarify the behavior.** Convert the request into observable outcomes, not only implementation nouns.
2. **Find the seam.** Locate the UI, state, navigation, repository, database, or external-service seam that owns the behavior.
3. **Build a red-capable check.** Create a focused test or contract that would fail for the reported defect or missing feature.
4. **Trace before editing.** Follow the event through the actual call chain.
5. **Implement at the correct layer.** Keep UI rendering, state ownership, persistence, and navigation in their existing responsibilities.
6. **Run focused validation.** Confirm the requested behavior first.
7. **Run the broader suite.** Catch regressions outside the immediate feature.
8. **Review the diff.** Look for placeholders, duplicated logic, stale imports, accidental scope, and inconsistent descriptions.
9. **Document limitations.** A missing SDK, unavailable device, blocked credential, or skipped end-to-end test must be reported explicitly.
10. **Integrate safely.** Verify Git status, commit contents, branch ancestry, remote state, and final artifacts.

---

## 2. Skills used during today’s work

### 2.1 `automation-and-scheduling`

**Source:** `/home/ubuntu/skills/automation-and-scheduling`

#### What it is

This skill governs work involving external APIs, background execution, recurring work, webhooks, synchronization, persistent workers, and integrations whose behavior continues after the initiating UI action.

#### When to use it

Use it before:

- Integrating xKiro or another external API.
- Adding Supabase synchronization or webhook behavior.
- Creating background upload or retry workers.
- Adding polling, scheduled tasks, or event-triggered processing.
- Designing a feature that can outlive the current Activity or screen.

#### Core workflow

1. Identify the trigger: user action, timer, webhook, push event, or app lifecycle event.
2. Define ownership: which process, worker, coordinator, or server owns the job.
3. Define retries and idempotency before implementing the network call.
4. Define cancellation and lifecycle behavior.
5. Bound payload size, duration, concurrency, and retry count.
6. Ensure secrets remain in environment/configuration, never source or logs.
7. Add an observable state model for pending, running, succeeded, failed, and cancelled.
8. Test duplicate delivery and partial failure.

#### RTC application

This was applicable to the xKiro demo, Supabase migration work, image upload recovery, client post idempotency, and synchronization status behavior. The Community post fix demonstrates the expected pattern: generate a stable client UUID, carry it through the coordinator and repository, enforce a database uniqueness constraint, and prevent repeated taps while submission is active.

#### Good output

A good result includes a state diagram, bounded retries, explicit ownership, safe secret handling, an idempotency strategy, and tests for duplicate, offline, timeout, and cancellation paths.

---

### 2.2 `writing-plans`

**Source:** `/home/ubuntu/skills/writing-plans`

#### What it is

This skill turns a multi-file or multi-subsystem request into a reviewable sequence of implementation steps before source changes begin.

#### When to use it

Use it when a request affects more than one layer, such as:

- Compose UI plus ViewModel plus repository.
- Navigation plus routes plus deep links.
- Supabase migration plus client contract.
- A defect involving several independent symptoms.
- A feature removal that requires source, configuration, and tests to change together.

#### Core workflow

1. Record the request and non-goals.
2. Identify affected files and ownership boundaries.
3. List dependencies between steps.
4. Identify risks and unavailable validation environments.
5. Define focused tests and full-suite checks.
6. Write the plan before editing.
7. Update the plan as findings change the implementation.

#### RTC application

The defect plan covered the profile click target, Community post lifecycle, URI permissions, media propagation, idempotency, synchronization, database migration, and source contracts. The plan prevented the symptoms from being treated as unrelated UI bugs.

#### Good output

A good plan states what changes, why the file owns that change, how it will be tested, and what will not be changed.

---

### 2.3 `navigation-3`

**Source:** `/home/ubuntu/.agents/skills/navigation-3`

#### What it is

This skill explains Jetpack Navigation 3, migration from Navigation 2, type-safe route modeling, `NavKey`, `NavDisplay`, scenes, deep links, multiple back stacks, dialogs, bottom sheets, Hilt integration, and returning results.

#### When to use it

Use it whenever a task mentions:

- Navigation 3.
- A migration from string routes or `NavHost`.
- Deep links or typed destinations.
- Multiple back stacks.
- List-detail or adaptive two-pane navigation.
- Route-scoped ViewModels or Hilt navigation integration.

#### Verification rule

Do not claim Navigation 3 integration from a conceptual route model alone. Verify dependencies and APIs. In RTC, the project uses Navigation 2 APIs:

- `NavHostController`
- `NavHost`
- `rememberNavController`
- `composable`
- `NavType`
- string route constants

The correct outcome of the audit was therefore **Navigation 3 is not integrated**, not an unrequested migration.

#### Migration workflow

1. Inventory current route strings and deep links.
2. Identify top-level destinations and independent back stacks.
3. Define serializable destination keys.
4. Add the Navigation 3 dependencies in a controlled change.
5. Introduce a compatibility layer if migration must be incremental.
6. Move one route family at a time.
7. Verify back behavior, state restoration, protected routes, deep links, dialogs, and process recreation.
8. Remove Navigation 2 only after all route families and tests migrate.

#### RTC application

The profile settings fix was validated against existing Navigation 2 wiring. Profile navigation routes through `RtcRoute.MY_WORK`, notifications through `RtcRoute.NOTIFICATIONS`, and settings stays within the Account screen. No Navigation 3 migration was introduced during a focused UI task.

---

### 2.4 `apple-design`

**Source:** `/home/ubuntu/upload/SKILL(3).md`

#### What it is

Apple Design is a motion and interaction discipline centered on direct response, physical continuity, spatial consistency, material hierarchy, restraint, and accessible feedback.

#### Core principles

- Respond immediately on press rather than only after release.
- Keep direct manipulation synchronized with the user’s gesture.
- Make transitions interruptible.
- Animate from the current presented value rather than a stale target value.
- Use springs for physical interactions, with critical damping as the default.
- Hand off gesture velocity into settling motion.
- Keep an entering surface spatially connected to its trigger.
- Use depth and material weight to encode hierarchy.
- Avoid motion that adds noise without communicating state.
- Respect reduced-motion and reduced-transparency settings.

#### When to use it

Use this skill for:

- Bottom sheets, drawers, dialogs, menus, and expanding cards.
- Dragging, swiping, paging, and snapping.
- Loading and success transitions.
- Floating surfaces and translucent toolbars.
- Redesigns where the request is to make the application feel calmer, more direct, or more premium.

#### RTC application

The RTC Assistant card was refined with a restrained material surface and `animateContentSize()` so the conversation grows smoothly as messages arrive. This was intentionally small: it improved continuity without adding decorative motion or introducing a new animation dependency.

#### Review checklist

- Does the UI respond on press?
- Can the user interrupt or reverse the interaction?
- Does the surface emerge from the source that opened it?
- Is motion necessary for comprehension?
- Does the interaction remain usable with reduced motion?
- Do hit targets meet accessibility requirements?
- Are color and elevation used to communicate hierarchy rather than decoration?

---

### 2.5 `vue-best-practices`

**Source:** `/home/ubuntu/.agents/skills/vue-best-practices`

#### What it is

This skill covers Vue component structure, Composition API reactivity, SFC organization, props and emits, composables, state ownership, and data flow.

#### When to use it

Use it for a Vue frontend, Vue-based automation harness, or a repository that contains a Vue surface even when the primary product is Android.

#### Core rules

- Keep state close to the component or composable that owns it.
- Use props down and events up for component communication.
- Avoid mutating props.
- Make reactive dependencies explicit.
- Keep side effects in composables or lifecycle hooks.
- Use stable keys for rendered collections.
- Keep templates declarative and move complex logic into composables.

#### RTC application

The repository’s main application is native Android, but the Vue skill was used to inspect the available frontend/test surface and ensure a profile settings investigation did not incorrectly assume a Vue production frontend existed in the selected repository.

---

### 2.6 Source-contract testing

**Source:** `tools/tests/run_contract_tests.py` and focused tests in `tools/tests`

#### What it is

Source-contract testing checks that required architectural and behavioral patterns exist in source code. It is not a substitute for device tests, but it is a fast and deterministic guard when a full Android environment is unavailable.

#### When to use it

Use it for:

- Route and callback wiring.
- Required migration content.
- Feature removal.
- Forbidden placeholder behavior.
- Persistence calls.
- Idempotency keys and database constraints.
- Required accessibility labels.
- Design-system usage.

#### RTC application

The focused contracts verified:

- Profile settings actions are real click targets.
- The Home screen uses RTC Assistant instead of removed sections.
- The Community Map is absent from Explore and onboarding.
- Civic Report picker state survives recreation.
- Community posts carry client idempotency IDs.
- Public Reports consume the shared dashboard stream.
- Account preferences invoke theme, notification, reading-mode, and locality persistence paths.

The full repository suite reached **193/193 passing contracts** after the Account Preferences changes.

#### Limitations

A source contract cannot prove that a real device renders correctly, that a Compose gesture feels right, or that a remote Supabase request succeeds. It must be complemented by unit tests, instrumentation tests, CI builds, and device or emulator validation.

---

## 3. Detailed skills for future implementation

### 3.1 `diagnosing-bugs`

**Source:** `/home/ubuntu/.agents/skills/diagnosing-bugs`

#### Purpose

This is the primary method for difficult bugs and performance regressions. Its central idea is to build a tight, deterministic, red-capable feedback loop before forming a theory.

#### Required phases

1. **Build a feedback loop.** Prefer a failing test, then a local HTTP script, CLI fixture, browser test, replayable trace, or minimal harness.
2. **Reproduce and minimize.** Confirm the exact user symptom and reduce the scenario to load-bearing inputs.
3. **Generate ranked hypotheses.** Produce three to five falsifiable hypotheses before testing.
4. **Instrument selectively.** Add targeted logs or measurements that distinguish hypotheses. Tag temporary logs and remove them.
5. **Fix and regress.** Convert the minimized repro into a permanent regression test, then fix the source seam.
6. **Clean up.** Re-run the original loop, remove debug instrumentation, and document the cause.

#### Example: unclickable profile menu

- Feedback loop: source contract asserting a real clickable modifier and a route callback.
- Repro: open Account, tap the profile/settings card, observe that no route or state change occurs.
- Hypotheses: a missing callback, an overlay intercepting taps, a modifier ordering problem, or a route that is not registered.
- Instrumentation: inspect the modifier chain, callback wiring, and navigation graph.
- Fix: make the card an explicit click target and connect its callback.
- Regression: assert the click modifier and route callback remain present.

#### Common failure modes

- Reading large amounts of code before creating a failing check.
- Fixing the visible error message rather than the first broken state transition.
- Using a test seam that cannot reproduce the actual caller chain.
- Adding untagged logs that survive into production.
- Declaring success after one passing happy-path test.

---

### 3.2 `implement`

**Source:** `/home/ubuntu/.agents/skills/implement`

#### Purpose

This skill governs turning a clear requirement into a complete implementation rather than stopping at analysis or a partial patch.

#### Workflow

1. Confirm the target behavior and non-goals.
2. Inspect existing patterns before introducing new abstractions.
3. Identify the smallest complete vertical slice.
4. Implement state, UI, persistence, navigation, and error behavior together where required.
5. Add tests at the correct seam.
6. Run focused checks.
7. Run the broader suite.
8. Review and document limitations.

#### RTC application

For Account Preferences, implementation meant more than adding three theme labels. It required connecting the controls to `RtcViewModel`, `RtcAuthenticationCoordinator`, `RtcRepository`, local preference storage, and the root theme boundary. The feature is only complete because selection affects the running UI and survives persistence.

---

### 3.3 `codebase-design`

**Source:** `/home/ubuntu/.agents/skills/codebase-design`

#### Purpose

This skill promotes deep modules: a small interface that hides substantial behavior, with a clean seam where callers and tests interact.

#### Important vocabulary

- **Module:** anything with an interface and implementation.
- **Interface:** everything a caller must know, including error modes and invariants.
- **Implementation:** the internal behavior.
- **Adapter:** a concrete implementation satisfying an interface.
- **Seam:** the place where behavior can be changed without editing every caller.
- **Depth:** how much behavior the interface provides per unit of complexity.
- **Leverage:** how many callers benefit from one implementation.
- **Locality:** how concentrated future changes and fixes remain.

#### Design tests

- Can the public interface be smaller?
- Does the module hide persistence, retry, and error handling behind one operation?
- Would deleting the module cause complexity to reappear across many callers?
- Do tests cross the same seam as production callers?
- Are dependencies injected rather than created internally?

#### RTC application

The shared RTC Assistant card was reused rather than duplicated on Home. The repository remains the persistence seam for theme, notification, locality, and experience preferences. The navigation graph remains the route seam.

---

### 3.4 `improve-codebase-architecture`

**Source:** `/home/ubuntu/.agents/skills/improve-codebase-architecture`

#### Purpose

This skill is for making a codebase easier to change without spreading one concern across unrelated files. It is useful after repeated bug fixes reveal that state ownership or interfaces are in the wrong place.

#### Workflow

1. Identify repeated change patterns.
2. Find the current state owner and all mirrors.
3. Detect pass-through layers and scattered conditionals.
4. Choose one authoritative state source.
5. Move behavior behind a deeper seam.
6. Preserve compatibility where necessary.
7. Add tests around the new seam.
8. Remove obsolete paths instead of layering a second implementation.

#### RTC application

The Public Reports feed was connected to the repository’s shared dashboard update stream so Home and Public Reports do not maintain independently stale snapshot state. The Account Preferences implementation also uses the existing repository persistence methods rather than adding a second local theme mechanism.

---

### 3.5 `tdd`

**Source:** `/home/ubuntu/.agents/skills/tdd`

#### Purpose

Test-driven development makes the desired behavior executable before implementation is considered complete.

#### Practical cycle

1. Write the smallest failing check.
2. Confirm that it fails for the intended reason.
3. Implement the smallest change that passes.
4. Refactor while keeping the check green.
5. Run the nearest larger suite.

#### What to test in RTC

- UI callback presence and route destinations.
- State transitions such as submitting, success, failure, and dismissal.
- Persistence calls and session rehydration.
- Migration contents and uniqueness constraints.
- Feature removal by asserting obsolete symbols and files are absent.
- Error and retry behavior.

#### Avoid

- Tests that only assert a file contains a word without connecting it to behavior.
- Tests that duplicate implementation details so heavily that harmless refactors fail.
- Tests that pass while the user’s original symptom remains possible.

---

### 3.6 `code-review`

**Source:** `/home/ubuntu/.agents/skills/code-review`

#### Purpose

This skill separates review into two axes:

1. **Standards:** Does the code follow repository conventions and avoid known code smells?
2. **Spec:** Does it implement the requested behavior without missing requirements or scope creep?

#### Review procedure

1. Pin the fixed point, such as `main` or a commit.
2. Inspect the three-dot diff and commit list.
3. Locate project standards and the originating request.
4. Review standards and spec independently.
5. Report findings by file and hunk.
6. Distinguish hard standard violations from judgment-call smells.

#### Smells worth checking in RTC

- Primitive obsession in route and preference strings.
- Repeated switches on the same enum.
- Middle-man coordinators that only delegate.
- Shotgun surgery across UI, ViewModel, repository, and SQL.
- Duplicated assistant, theme, or settings behavior.
- Placeholder callbacks that make a feature appear functional.

---

### 3.7 `review-animations`

**Source:** `/home/ubuntu/skills/review-animations`

#### Purpose

This skill reviews motion as engineering, not decoration.

#### Review dimensions

- **Easing:** Does the curve match the physical interaction?
- **Duration/response:** Is it quick enough to feel direct?
- **Interruptibility:** Can the user reverse or interrupt it?
- **Transform origin:** Does motion originate from the correct source?
- **Performance:** Are compositor-friendly properties used?
- **Lifecycle:** Are animations cancelled and cleaned up?
- **Accessibility:** Is reduced motion supported?

#### RTC application

Use it for the RTC Assistant’s expanding message area, modal sheets, profile menus, media retry states, and any new Home transitions. Avoid adding bounce to passive appearance changes; reserve overshoot for interactions that carry momentum.

---

### 3.8 `ci-cd-and-automation`

**Source:** `/home/ubuntu/skills/ci-cd-and-automation`

#### Purpose

This skill makes validation repeatable in CI rather than dependent on one developer’s machine.

#### Recommended gates for RTC

1. Python source-contract suite.
2. Kotlin unit tests.
3. Android lint.
4. Debug APK build.
5. Migration and SQL contract checks.
6. Optional emulator/instrumentation tests.
7. Artifact and branch-state reporting.

#### Environment rule

A local failure caused by a missing Android SDK should be reported as an environment limitation. It must not be silently treated as a passing Android build. CI should provide the configured SDK and remain the authoritative build gate.

---

### 3.9 `validate-data`

**Source:** `/home/ubuntu/skills/validate-data`

#### Purpose

This skill checks whether data-driven behavior is accurate, complete, and methodologically sound rather than merely visually plausible.

#### Checks

- Single source of truth.
- Correct aggregation and filtering.
- No stale cache presented as live state.
- No duplicate records from retries.
- Correct pagination and cursor boundaries.
- Appropriate null and failure handling.
- Reproducible verification queries.

#### RTC application

It is relevant to dashboard/report synchronization, notification preference persistence, signed media URLs, community post idempotency, and Supabase migration verification.

---

### 3.10 `sql-queries`

**Source:** `/home/ubuntu/skills/sql-queries`

#### Purpose

This skill provides safe patterns for writing and reviewing SQL across dialects, with emphasis on bounded reads, CTEs, joins, pagination, and analytical correctness.

#### RTC application

Use it for:

- Supabase RPC definitions.
- Migration verification queries.
- Cursor pagination.
- User-scoped uniqueness checks.
- RLS-aware read paths.
- Production checks that select only required columns and use explicit limits.

#### Safety rules

- Use explicit `LIMIT` and pagination for reads.
- Select only the required columns.
- Avoid unbounded administrative queries.
- Make user and tenant scope explicit.
- Verify the intended index supports the query shape.

---

### 3.11 `sql-optimization-patterns`

**Source:** `/home/ubuntu/skills/sql-optimization-patterns`

#### Purpose

This skill diagnoses slow SQL, N+1 patterns, missing indexes, poor pagination, and inefficient execution plans.

#### RTC application

The client post idempotency index is a representative case: the uniqueness boundary should be explicit, user-scoped, and backed by an index so duplicate detection is efficient and authoritative.

#### Review checklist

- Is the uniqueness key correct for the ownership boundary?
- Does the index match the predicate and ordering?
- Are RPCs bounded and selective?
- Is pagination stable under concurrent inserts?
- Does `EXPLAIN` confirm the intended access path?

---

### 3.12 `manus-config` and Supabase connector workflows

**Sources:** `/home/ubuntu/skills/manus-config` and the configured Supabase MCP server

#### Purpose

These workflows manage connectors, inspect environment configuration, identify projects, apply migrations, and verify remote state.

#### Safe deployment workflow

1. Inspect the configured connector.
2. Identify the exact target project.
3. Read the local migration and confirm its SQL payload.
4. Check whether the migration already exists remotely.
5. Apply a forward-only migration.
6. Verify migration history.
7. Verify columns, indexes, RPC signatures, and policies.
8. Run security and performance advisors.
9. Record the project identity and result.

#### RTC application

The Community post idempotency migration was applied to RTC Community Production, the obsolete RPC overload was removed, and the resulting schema and migration state were verified.

---

### 3.13 `dispatching-parallel-agents`

**Source:** `/home/ubuntu/skills/dispatching-parallel-agents`

#### Purpose

This skill is for independent tasks that can be investigated concurrently without shared mutable state.

#### Good parallel splits

- Home UI and profile UI inspection.
- Supabase migration review and client call-site review.
- Focused test discovery and design-system review.
- Navigation inventory and media pipeline inventory.

#### Bad parallel splits

- Two agents editing the same source file.
- Sequential tasks where the second depends on an uninspected result from the first.
- Broad, unstructured exploration with no output schema.

#### Coordination rule

Each parallel task needs a bounded prompt, clear files or entities, and a specific report format. Aggregate findings before editing shared code.

---

### 3.14 `workflow-composer`

**Source:** `/home/ubuntu/skills/workflow-composer`

#### Purpose

This skill composes multi-stage research or implementation workflows through the workflow MCP server, especially for fan-out, map/reduce, conditions, and repeated structured subtasks.

#### Use it when

- Three or more independent entities require the same research fields.
- A large migration needs staged discovery, implementation, and verification.
- Results must be structured and aggregated.
- A loop or conditional workflow is more appropriate than manual sequential work.

#### Do not use it for

A deterministic local script over a list of files or inputs. Use one bounded script instead of spawning many agents for identical mechanical work.

---

### 3.15 `technical-writing`

**Source:** `/home/ubuntu/skills/technical-writing`

#### Purpose

This skill produces precise, structured, professional technical documents with explicit assumptions, evidence, limitations, and next steps.

#### RTC application

Use it for:

- Defect analysis.
- Architecture plans.
- Release-readiness reports.
- Migration notes.
- Skills catalogs.
- Test and validation summaries.

#### Good document structure

1. Scope and objective.
2. Context and current architecture.
3. Findings.
4. Design or implementation decisions.
5. Verification evidence.
6. Limitations.
7. Follow-up work.

---

### 3.16 `knowledge-synthesis`

**Source:** `/home/ubuntu/skills/knowledge-synthesis`

#### Purpose

This skill aggregates, deduplicates, ranks, and attributes information from multiple sources.

#### RTC application

Use it when combining:

- User reports and screen recordings.
- Repository code and test evidence.
- Supabase schema state and client expectations.
- Multiple skill instructions.
- External API documentation and observed behavior.

#### Output discipline

Separate verified facts, strong inferences, unresolved questions, and recommendations. Do not merge conflicting evidence silently.

---

## 4. Additional applicable skills in the environment

The following available skills may be valuable for future RTC work even though they were not central to today’s changes.

| Skill | Best use in RTC |
| --- | --- |
| `builtin-llm-models` | Calling an LLM in bulk for classification, extraction, evaluation, or structured analysis. |
| `data-analysis` | Analyzing resident reports, operational metrics, usage exports, or test result datasets. |
| `data-context-extractor` | Building a company-specific vocabulary and metric reference for RTC data analysis. |
| `deep-research` | Investigating Android, Supabase, civic-tech, accessibility, or policy questions using multiple sources. |
| `gws-best-practices` | Automating Google Drive, Docs, Sheets, or Slides workflows if RTC operations use Workspace. |
| `image-processing` | Inspecting, resizing, compressing, or validating uploaded evidence and profile media. |
| `imagegen` | Creating original visual assets, diagrams, mockups, or UI references. |
| `kpi-dashboard-design` | Designing admin dashboards for reports, support cases, moderation, operations, MRR, churn, or service metrics. |
| `manus-api` | Managing Manus projects, tasks, OAuth applications, and agentic integrations. |
| `memory-recall` | Recovering prior decisions, debugging notes, and historical context before changing established architecture. |
| `n8n-workflow-patterns` | Designing webhook, HTTP, database, and AI automation workflows outside the Android client. |
| `persistent-computing` | Running long-lived services, Docker workloads, queues, or reusable environments that cannot live safely in a temporary sandbox. |
| `read-special-images` | OCR and inspection of dense screen recordings, screenshots, or long UI captures. |
| `stitch-extract-design-md` | Extracting the existing design system from source into a maintainable `DESIGN.md`. |
| `validate-data` | Checking data correctness, aggregation, survivorship, and reproducibility. |
| `vue-router-best-practices` | Maintaining a Vue-based companion dashboard or frontend route structure. |
| `vue-pinia-best-practices` | Managing shared state in a Vue frontend. |
| `vue-testing-best-practices` | Testing Vue components, composables, routes, and browser interactions. |
| `typst-pdf-maker` | Producing a polished PDF release report, architecture paper, or operational manual. |
| `slides` | Creating a presentation for stakeholder review; use the configured Slides skill rather than hand-authoring a deck. |

---

## 5. RTC-specific playbooks

### Playbook A: Fix a broken Android feature

1. Read the relevant module documentation and ADRs.
2. Find or create a red-capable test.
3. Trace UI → ViewModel → coordinator → repository → backend.
4. Rank hypotheses.
5. Fix the first broken state transition.
6. Add failure, retry, and lifecycle handling.
7. Run the focused contract.
8. Run `python3 tools/tests/run_contract_tests.py`.
9. Run `./gradlew test --no-daemon` when the Android SDK is available.
10. Review the final diff and document limitations.

### Playbook B: Add a persistent Account Preference

1. Define the user-visible behavior and current value.
2. Confirm the domain model has a typed value rather than a free-form string.
3. Add a ViewModel/coordinator method.
4. Persist locally for immediate offline behavior.
5. Persist remotely for authenticated users.
6. Update the session `StateFlow` immediately after successful persistence.
7. Connect the root UI to the session value.
8. Add an error path that does not silently claim success.
9. Test selection, restoration, failure, and fallback.

### Playbook C: Remove a feature entirely

1. Search labels, symbols, routes, tutorial steps, feature flags, tests, and docs.
2. Identify whether similarly named features are actually separate.
3. Remove visible entry points first.
4. Remove route and onboarding references.
5. Delete dead source files where safe.
6. Update configuration defaults and tests.
7. Add a negative contract proving the obsolete feature stays absent.
8. Re-scan the repository after the change.

### Playbook D: Add Apple-style polish safely

1. Keep the existing design tokens and component system.
2. Improve response and hierarchy before adding motion.
3. Use small, reversible, interruptible animations.
4. Prefer critical damping and no overshoot for passive content.
5. Use material/elevation to clarify hierarchy.
6. Test reduced motion and content expansion.
7. Avoid decorative animation that competes with the task.

### Playbook E: Prepare a repository merge

1. Inspect current branch and remote state.
2. Confirm the worktree and changed-file scope.
3. Run focused tests and the full suite.
4. Run build/lint gates available in the environment.
5. Write or update documentation.
6. Inspect the staged diff.
7. Commit with a scoped message.
8. Push or merge only within the user’s authorization.
9. Confirm `HEAD`, the target branch, and remote are identical where expected.
10. Report the commit, validation, and limitations.

---

## 6. Definition of done

A feature or fix is complete when:

- The requested behavior is observable and not merely represented by labels.
- Every visible option has a real callback, route, or persistence path.
- Descriptions match the behavior that actually exists.
- State has one clear owner and rehydrates correctly.
- Error and retry behavior are explicit.
- The interaction is accessible and has reasonable hit targets.
- Motion is purposeful, interruptible, and reduced-motion aware.
- Focused tests pass.
- The full repository suite passes.
- Android build/device validation is run when the environment supports it.
- Migrations are verified remotely when applicable.
- No secrets appear in source, logs, or documentation.
- The diff is reviewed for stale code, scope creep, and architectural drift.
- The final Git branch and artifacts are verified.
- Known limitations are stated plainly.
