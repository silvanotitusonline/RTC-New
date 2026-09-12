# RTC Community Non-Production Phase 1: Constrained UI Configuration Implementation

**Author:** Manus AI
**Date:** 27 August 2026
**Branch context:** Local recovery/non-production baseline working tree.
**Scope:** Source-controlled Kotlin/Jetpack Compose and Supabase migration changes only.
**Excluded:** Production systems, database migration application, real credentials, Firebase Test Lab, emulator/device execution, launcher alias changes, release build, APK distribution, deployment, and Git commit/push.

## Outcome

The approved constrained Phase 1 foundation has been implemented locally. It introduces a **global resident Home configuration contract** with a strict compiled section catalogue and a safe default fallback. A source-controlled Supabase migration now describes the required append-only configuration lifecycle, but it has **not** been applied to the isolated non-production project.

The existing native Home composition has been refactored into a deterministic renderer. The default Home still uses its original reference-driven order and existing connected actions. A future remote payload can only reorder or hide named, precompiled sections; malformed, duplicated, incomplete, unknown, or future-schema configurations resolve to the compiled default instead of changing behavior or breaking Home.

## Implemented local changes

| Area | Local implementation | Safety result |
| --- | --- | --- |
| Supabase migration | Added `20260827190000_add_nonproduction_ui_configuration_catalogue.sql`. It defines `ui_configuration_versions`, `ui_configuration_events`, validation logic, one global `RESIDENT_GLOBAL` audience, lifecycle states, typed-confirmation publish/revert RPCs, and a narrow effective-layout read RPC. | The migration is source-controlled only. Direct client table access is revoked; administrator mutation RPCs assert authenticated `SYSTEM_ADMIN` and `aal2` through the existing server-side assertion helper. |
| Version history | Published rows are superseded instead of overwritten. Reversion creates a new published revision based on a prior validated configuration. Event rows record draft, publish, supersede, and revert actions. | Prior payload history remains intact; neither an Android client nor a content-editor direct write can mutate it. |
| Home catalogue | Added `HomeSection`, `HomeLayout`, and `GlobalUiConfiguration` in `core/UiConfiguration.kt`. The allowlist contains Welcome, Community Snapshot, Quick Access, Continue Draft, Pending Sync, Next Steps, Latest Updates, and Help. | The catalogue has no arbitrary deep links, URLs, scripts, assets, class names, or remote composable definitions. Welcome and Help are mandatory. |
| Native renderer | `HomeScreen` now accepts a layout, resolves it through `HomeLayout.validatedOrDefault`, and renders existing connected sections through a finite `when` branch. The call site continues to supply the same reference default. | The source has only changed structural composition; no Home action was replaced with a no-op or external target. |
| Decode and fallback | The strict JSON decoder rejects malformed JSON, unknown fields, unsupported schema versions, duplicate sections, and layouts without mandatory sections. | The automatic fallback is the hardcoded default Home, not a blank screen or partially interpreted payload. |
| Device cache foundation | `UserPreferencesStore` now exposes a `CachedGlobalUiConfiguration` flow and writes only validation-passed payloads with a bounded opaque version identifier. | DataStore is a last-known-good device cache only. It does not replace the Supabase publication authority or alter the resident’s independent theme/reading preference. |
| Unit coverage | Added four pure JVM tests for default ordering, valid minimum layout, duplicate/incomplete fallback, and malformed/future configuration fallback. | Configuration policy is now executable and regression-protected locally. |
| Source contracts | Added focused contracts for the migration’s global/MFA/direct-write restrictions, compiled-only Home renderer, strict fallback decoder, and cache separation. Repaired one older test whose arbitrary 9,000-character source window was invalidated by the intentionally longer Home function. | The repaired contract now verifies the Home screen’s intentional `ResidentPullToRefresh` shell directly. |

## Verification evidence

| Check | Result | Evidence |
| --- | --- | --- |
| Working-tree patch check | **Passed** | `git diff --check` returned no whitespace or patch-structure errors. |
| Source-level regression contracts | **Passed: 82/82** | `uv run --no-project tools/tests/run_contract_tests.py`. A first run identified an obsolete Home source-window heuristic; it was corrected without relaxing the intended structural guard, then the suite passed. |
| Debug Kotlin + JVM unit tests | **Passed** | `:app:testDebugUnitTest` completed successfully with an in-process, memory-bounded compiler. |
| JVM test count | **Passed: 8 tests** | `UiConfigurationTest`: 4/4; existing `RtcMathTest`: 4/4; no failures/errors. |
| Android instrumentation compilation | **Passed** | `:app:compileDebugAndroidTestKotlin` completed successfully. It compiles test code only and is not device execution. |
| Debug lint | **Passed** | `:app:lintDebug` completed successfully and generated `app/build/reports/lint-results-debug.html`. |

## Observed compiler warnings

Compilation succeeded. The remaining warnings predate or are outside this Phase 1 feature: deprecated non-auto-mirrored Assignment/Article/Logout icons, a deprecated top-app-bar colour helper, and Kotlin’s future annotation-default-target notice on existing data fields. The newly introduced redundant JSON-format allocation warning was removed before final verification. No warning was promoted to an error, and no unrelated UI-wide cleanup was performed in this bounded feature pass.

## Intentional non-actions

The migration was not executed through Supabase. It needs a separate review and explicit authorization before it can alter even the isolated non-production schema. No remote fetch/RPC integration, administrator customizer screen, theme preset editor, Welcome-copy editor, activity aliases, or runtime launcher switcher has been added yet; those are later vertical slices requiring compatible client contracts and explicit review.

No Firebase Test Lab or browser upload was attempted. The attachment’s supplied account credential was not read into a command, stored, printed, or used. No release build was attempted, and this work makes no production-ready or device-tested claim.

## Remaining Phase 1 follow-through

The immediate next development slice is to review the migration contract and, only after an explicit isolated-environment authorization, apply it to the non-production Supabase project. The Android repository can then call the effective-layout RPC, cache only a validated response, and pass it into the already modularized renderer. The System Administrator UI for draft/publish/revert must be built only after that server contract is available and must preserve existing route/MFA gates.
