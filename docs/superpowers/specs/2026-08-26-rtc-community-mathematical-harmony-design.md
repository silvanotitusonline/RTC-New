# RTC Community Concept 6 Mathematical Harmony Design

## Status

Approved implementation specification derived from the owner-supplied **RTC Community — Mathematical Harmony & Proportional Refinement Implementation Brief** and the verified RTC Community 1.0.5 Concept 6 source snapshot dated 26 August 2026.

## Goal

Refine the existing RTC Community Concept 6 Android interface into a coherent proportional system using the Golden Ratio, Fibonacci progression and mathematically correct circular geometry while preserving every functional, navigational, security and backend contract.

The result remains **RTC Community Concept 6**. It is a systematic refinement, not a new design concept.

## Non-Negotiable Product Contracts

- Resident primary navigation remains **Home / Community / Explore / Support**.
- Account remains a secondary route and is never added as a fifth resident destination.
- The trust hierarchy remains Resident/Public → Staff → System Administrator.
- Protected routes remain fail-closed and System Administrator surfaces retain MFA/AAL2 enforcement.
- Supabase RPCs, RLS, Edge Functions, media flows, notifications, offline drafts, WorkManager, RTC AI, moderation and administrative operations retain their existing behavior.
- RTC AI retains Proposal → Review → Confirm → Transaction → Audit semantics and continues using the existing guarded Gemini-backed Edge Function.
- Accessibility, platform ergonomics, content integrity and functional correctness override exact mathematical ratios.
- No service-role credential, signing key, service-account secret or Firebase runtime configuration enters source control.

## Verified Baseline

- Archive SHA-256: `94900bad536e46f1598fdc1755141e2f15839100240218a8fa2fb591be48f8ae`.
- Source regression baseline: 29/29 contracts pass.
- Existing visual system: Concept 6 deep ink, graphite, emerald, civic gold and danger semantics.
- Current dimensional inventory: 28 distinct raw `dp` values across the Compose source, with repeated arbitrary clusters around 10/12/14/16/18/20/22dp.
- Current typography: ten styles spanning 12–34sp, with inconsistent tempered-ratio relationships and incomplete style coverage.
- Current responsive model: compact phone layout plus a single 840dp large-screen threshold.
- Current local limitation: JDK 17 only, with no local Gradle or Android SDK; Android compilation, lint and APK/AAB generation therefore run in GitHub Actions.

## Considered Approaches

### 1. Semantic proportional token system — selected

Introduce a small, explicit mathematical foundation and expose semantic spacing, radius, sizing, motion, aspect-ratio, density and breakpoint tokens through Compose composition locals. Shared components and screen code consume semantic names; raw calculations remain centralized.

This approach gives the highest consistency without making ordinary UI code mathematical or difficult to maintain.

### 2. Mechanical Fibonacci replacement — rejected

Replace every literal with the nearest Fibonacci value. This would incorrectly shrink or enlarge touch targets, QR geometry, platform controls, dynamic media and dense administrative surfaces. It would optimize numerical purity at the expense of usability.

### 3. Shared-component-only refinement — rejected

Update only `Theme.kt` and `Concept6Components.kt`. This would leave more than 150 screen-level literal measurements in `MainActivity.kt`, so resident and protected surfaces would continue to diverge.

## Mathematical Foundation

`RtcMath` is the only source of proportional constants:

```kotlin
object RtcMath {
    const val Phi = 1.61803398875f
    const val GoldenMajor = 0.61803398875f
    const val GoldenMinor = 0.38196601125f
    const val Pi = 3.141592653589793
}
```

The constants are used only for relationships that are genuinely proportional: Golden media/card ratios, expanded master-detail weighting and custom circular calculations. Ordinary spacing consumes semantic tokens and never scatters `0.618f` calculations through screen code.

## Spatial System

The base Fibonacci family is:

| Token | Value | Primary use |
|---|---:|---|
| `micro` | 3dp | Optical correction only |
| `tiny` | 5dp | Closely related text or indicators |
| `compact` | 8dp | Icon-label and compact control gaps |
| `small` | 13dp | Internal groups and dense cards |
| `standard` | 21dp | Page gutters and comfortable card padding |
| `section` | 34dp | Major section separation |
| `largeSection` | 55dp | Empty/hero separation |
| `hero` | 89dp | Rare structural emphasis |

Semantic aliases define page gutter, list gap, card gap, content padding, dialog padding, form gap and administrative density. The aliases permit a future value adjustment without changing every consumer.

Platform/accessibility exceptions remain explicit tokens rather than magic numbers. The minimum interactive target remains 48dp.

## Radius and Circular Geometry

The non-circular radius family is 8dp / 13dp / 21dp / 34dp. Pills and avatars use `CircleShape`, never a fabricated `999.dp` radius. Circular custom drawing uses `2πr` or `πr²` through centralized helpers when circumference or area is required.

QR codes remain square and pixel-accurate. User media retains native aspect ratio where required.

## Typography

Use a tempered proportional scale compatible with Android font scaling:

| Material role | Size / line height |
|---|---:|
| `labelSmall` | 11sp / 16sp |
| `labelMedium` | 12sp / 17sp |
| `labelLarge` | 14sp / 20sp |
| `bodySmall` | 13sp / 19sp |
| `bodyMedium` | 15sp / 22sp |
| `bodyLarge` | 17sp / 25sp |
| `titleSmall` | 16sp / 21sp |
| `titleMedium` | 18sp / 24sp |
| `titleLarge` | 21sp / 27sp |
| `headlineSmall` | 23sp / 29sp |
| `headlineMedium` | 26sp / 32sp |
| `headlineLarge` | 29sp / 36sp |
| `displaySmall` | 34sp / 42sp |

The scale preserves 13, 21 and 34 Fibonacci anchors while tempering intermediate steps for mobile readability. No component hard-codes an `sp` value outside the theme.

## Density Classes

All surfaces share the same base tokens with four explicit density modes:

- `RESIDENT_COMFORTABLE`: standard 21dp padding and 13dp list rhythm.
- `FEED_CONTENT`: 21dp outer padding with compact 8dp/13dp internal rhythm.
- `ADMIN_COMPACT`: 13dp card padding and 8dp control rhythm.
- `ANALYTICAL_DENSE`: 13dp outer/card padding and 5dp/8dp metric rhythm.

This retains civic readability on resident screens and operational density in protected workspaces without creating a separate design language.

## Responsive System

Use Android-compatible breakpoints:

- Compact: under 600dp, stacked content.
- Medium: 600–839dp, selective dual-pane or wider card grids.
- Expanded: 840dp and above, navigation rail and proportional primary/secondary regions.

The 600/840 values are deliberate platform exceptions. Expanded master-detail content may use `GoldenMajor` and `GoldenMinor` weights, with practical minimum/maximum widths. Long-form content receives a bounded readable width rather than stretching edge to edge.

## Motion

The central duration vocabulary is 89ms / 144ms / 233ms / 377ms / 610ms. Existing screens contain no arbitrary animation duration to migrate, so the implementation does not add decorative motion merely to exercise the tokens. Any new state feedback uses the shortest suitable duration and must respect Android animator/reduced-motion behavior.

## Shared Component Refinement

`Concept6Components.kt` becomes the enforcement boundary for:

- adaptive screen gutters and density;
- card padding, radius, outline and elevation;
- status-chip height, padding and circular shape;
- icon-label spacing and size classes;
- search-field geometry;
- settings-row touch targets;
- emergency/protected banners;
- case progress;
- empty states;
- resident bottom navigation;
- true circular FAB geometry;
- Golden-ratio media framing where appropriate.

Dynamic content cards remain content-driven. Feed posts, messages and case records never receive forced fixed Golden heights.

## Screen Migration

Every raw screen-level spacing, padding, radius, icon, avatar, panel width and fixed media measurement is classified before replacement:

- layout spacing migrates to semantic Fibonacci-backed tokens;
- touch targets remain at least 48dp;
- platform icons retain 24dp where Material ergonomics require it;
- current 164dp metric/action widths migrate to the 144dp Fibonacci grid minimum with adaptive wrapping;
- fixed 184dp Community preview height migrates to a Golden landscape aspect ratio;
- the 248dp staff pane migrates to a bounded 233dp Fibonacci minimum and proportional expanded behavior;
- circular elements use centralized size classes and `CircleShape`;
- content-specific media and QR geometry remain functionally correct.

## Accessibility and Optical Corrections

- Touch targets remain at least 48dp.
- Text must reflow at 200% font scale without fixed-height clipping.
- State is communicated by text/icon as well as color.
- TalkBack labels and existing semantics are preserved or improved.
- Light, dark and system themes remain supported.
- Simplified Reading Mode remains legible and unaffected by density compaction.
- Optical centering is allowed through the 3dp correction token when numerical centering appears visually wrong.

## Verification Strategy

1. Add source contracts that fail until mathematical constants, semantic tokens, circular shapes, density classes, responsive breakpoints, Golden media framing and documentation exist.
2. Add pure JVM unit tests for φ subdivisions, Golden height, circumference and responsive classification.
3. Preserve all existing 29 regression contracts.
4. Run `testDebugUnitTest`, `lintDebug` and `assembleDebug` in GitHub Actions.
5. Run release APK/AAB tasks only when Firebase runtime configuration and signing constraints permit.
6. Inspect rendered representative screens when an Android-capable runner or emulator is available; never claim visual/device verification from source alone.

## Non-Goals

- No new navigation destination or information architecture.
- No database schema or production data mutation.
- No Supabase Edge Function change.
- No migration from Gemini to OpenAI.
- No new branding, palette or Concept 7 visual direction.
- No arbitrary decomposition of the existing repository or backend layers.

## Acceptance Criteria

- Existing functional and security source contracts remain green.
- New mathematical-harmony source and JVM contracts pass.
- Shared and screen-level Compose code consumes centralized semantic tokens for ordinary layout values.
- Pills and circular surfaces use mathematically correct shapes.
- Community media uses intentional, non-destructive aspect handling.
- Resident, staff and administrator surfaces use one proportional system with declared density differences.
- Compact, medium and expanded behavior is centralized and documented.
- Android CI passes unit tests, lint and debug assembly, or every remaining failure is reported with its exact evidence and no false production-ready claim.
