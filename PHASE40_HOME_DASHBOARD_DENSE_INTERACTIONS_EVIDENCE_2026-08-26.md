# Phase 40 — Dense Home Dashboard Interactions Evidence

**Date:** 2026-08-26  
**Branch:** `recovery/nonproduction-baseline`  
**Scope:** Native Android Home dashboard only  
**Production status:** Unchanged; no production Supabase project, data, credentials, policies, or functions were queried or modified.

## Objective

Apply the reference-directed Home dashboard refinement while preserving truthful analytics and existing authorization boundaries. The requested result was a denser `16dp` layout, a visibly weighted `5–6dp` project-progress ring, and independently actionable snapshot items for **Projects**, **Centres**, and **Opportunities**.

## Implemented behavior

| Area | Reviewed implementation | Outcome |
|---|---|---|
| Home page gutter | `RtcHomeDashboard.pagePadding = 16.dp` | Home content now uses the requested dense outer padding. |
| Snapshot card padding | `RtcHomeDashboard.cardPadding = 16.dp` | Snapshot internals now use the requested dense padding without changing application-wide card defaults. |
| Progress ring | `ringDiameter = 76.dp`; `ringStroke = 6.dp` | The status-based progress indicator is larger and materially heavier, satisfying the requested 5–6dp stroke. |
| Snapshot ownership | The outer snapshot is a non-clickable Material card | Eliminates the prior misleading behavior where every snapshot tap opened Projects. |
| Projects action | Active-project metric calls `onOpenDirectory("projects")` | Opens the existing Projects directory route. |
| Centres action | Centre metric calls `onOpenDirectory("centres")` | Opens the existing Centres directory route. |
| Opportunities action | Opportunity metric calls `onOpenDirectory("opportunities")` | Opens the existing Opportunities directory route. |
| Accessibility | Each metric action has `RtcSize.minimumTouchTarget` minimum height and button role | Compact presentation does not reduce the supported touch-target baseline. |
| Data integrity | Existing server-derived project, centre, opportunity, and status-progress values remain in use | No fabricated charts, financial values, activity timelines, or analytics were introduced. |

The previous disclosure remains visible: progress is a **status-based estimate**, not a measurement of financial expenditure.

## Design-system containment

The refinement uses the new `RtcHomeDashboard` token object in `RtcDesignTokens.kt`. It intentionally does **not** change global `RtcSpacing.standard`, `RtcStroke.emphasis`, or shared card defaults, avoiding unintended density and ring changes across unrelated screens.

## Validation

| Check | Result |
|---|---|
| Source-contract suite | **66/66 passed** via `uv run --no-project tools/tests/run_contract_tests.py`. |
| New regression contract | Passed: verifies central `16.dp` Home geometry tokens, `76.dp` ring diameter, `6.dp` ring stroke, absence of the prior outer Projects click handler, the three exact directory identifiers, and minimum metric touch target. |
| Kotlin compile | **Passed**: `:app:compileDebugKotlin` with the restored Gradle 8.13 / JDK 21 toolchain and bounded resources. |
| Resource processing | Not rerun; this source-only Compose change introduced no Android resource edits. |
| Full APK packaging | Not attempted. Previous full dex packaging was not accepted as reliable evidence after environment reset. |
| Authenticated device validation | Not available after runtime reset; no real-session or physical-device claim is made. |

## Release posture

This slice is **not a production release approval**. Remaining gates include fresh isolated authenticated-session evidence, synthetic/device interaction validation, and reliable full package validation. The completed work is local, source-controlled Android UI and navigation refinement only.
