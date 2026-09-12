# Phase 39 — Home Dashboard Reference Metrics Review

**Date:** 2026-08-26
**Scope:** Comparison of the current native Compose Home dashboard with the supplied Concept 4 Home reference
**Status:** Review only; no UI code or backend contract was changed in this phase

## Measurement Basis

The supplied screenshot is a raster visual reference rather than a design file or a rendered build of the current APK. The implementation values below are therefore **exact Compose token/code values**. The reference-side values are **screenshot-relative layout estimates**, intended to guide implementation rather than claim pixel-perfect measurement. A true pixel comparison requires a screenshot from the redesigned APK on a declared device width and density.

## Exact Current Implementation Metrics

| Property | Exact current value | Implementation source | Assessment against reference direction |
|---|---:|---|---|
| Phone horizontal gutter | 21 dp each side | `RtcSpacing.standard` | Wider than the reference’s visually compact 16–18 dp margin. |
| Usable content width at 360 / 393 / 412 dp | 318 / 351 / 370 dp | Screen width minus two gutters | Adequate for a single-column mobile dashboard. |
| Vertical list gap | 13 dp | `RtcSpacing.small` | Close to the reference’s compact card rhythm. |
| Default Home card padding | 21 dp | `RtcSpacing.cardPadding` | More generous than the dense reference dashboard. |
| Community snapshot ring | 68 dp diameter | `RtcSize.heroIcon * 2` | Slightly smaller than the screenshot’s dominant 72–80 dp visual anchor. |
| Snapshot ring stroke | 2 dp | `RtcStroke.emphasis` | Too light for the strong multi-segment reference ring. |
| Snapshot status chip | 34 dp minimum height | `RtcSize.statusChipHeight` | Taller than the compact “Live” badge shown in the reference. |
| Bottom navigation | 89 dp minimum height | `RtcSize.bottomNavigationHeight` | Visually heavier than the reference navigation rail. |
| Greeting | 23 sp headline / 17 sp subtext | `headlineSmall` / `bodyLarge` | More prominent and vertically expansive than the reference. |
| Snapshot title | 21 sp | `titleLarge` | Larger than the screenshot’s compact dashboard-card heading. |
| Snapshot metrics | 18 sp figures / 13 sp labels | `titleMedium` / `bodySmall` | Suitable for legibility, but the three stacked rows are less infographic-like than the reference’s two-value summary. |

## Home Layout Comparison

| Reference element | Current implementation | Gap | Recommended target |
|---|---|---|---|
| Top utility row with notification and compact identity | Greeting only; global application chrome owns any other actions | **Missing Home-specific utility hierarchy.** | Add a compact Home top bar with notification route and Account avatar only if those routes are real and already available. |
| Community snapshot card | Present as one full-width `RtcCard` with live badge, ring, three values, and disclosure | **Partially matched.** The semantic structure is correct, but density and visual hierarchy differ. | Use 16 dp outer/card padding, 72–76 dp ring, 12–13 sp heading, 11–12 sp badge, and a 192–208 dp fixed visual envelope. |
| Multi-colour ring with status legend | One emerald determinate `CircularProgressIndicator` | **Missing.** A multicolour ring and legend would imply project-status segment data that the current model does not expose. | Do not fabricate a legend. Add it only after the backend provides authoritative `on_track`, `at_risk`, and `completed` aggregates. |
| Two-number active/open summary | Three stacked metrics: active projects, centres, opportunities | **Partially matched.** Centres increases density and makes the action ambiguous. | Use two direct action blocks, each deep-linking to its matching directory; place Centres in Quick actions or a separate services card. |
| Next steps cards | Two real Support case cards | **Matched structurally.** | Restyle as denser cards with stage badge, brief update timestamp, and progress rail only where a real stage model supports it. |
| Activity timeline | Not present | **Missing.** | Add only when a server-backed resident activity feed exists; do not turn notifications or local UI history into a fake timeline. |
| Four quick-action tiles | Four functional `AssistChip` controls | **Functional but visually mismatched.** Chips are lighter and less scannable than the reference tile grid. | Use a two-by-two grid of 48 dp minimum-touch tiles connected to the same Support, Explore, Centres, and Help callbacks. |
| Official update surface | Two real notice cards | **Matched structurally.** | Put one compact official update after Next steps; preserve the existing notice route. |

## Analytics and Infographic Truthfulness

The current progress ring correctly consumes `metrics.overallProjectProgress`, and the accompanying text explicitly says it is a **status-based estimate rather than financial expenditure**. This is an appropriate honesty boundary. The reference’s line chart, status legend, and “on track / at risk / completed” totals should **not** be copied as decoration because the current `DashboardMetrics` input does not include verified time-series or status-segment aggregates.

> The current dashboard is **semantically safer than the reference mockup**: it displays only values already available to the Android client. Its main shortcoming is visual density and navigation specificity, not fabricated data.

## Critical Interaction Finding

The entire current Community snapshot card opens the **Projects** directory. This makes the `Centres available` and `Open opportunities` rows visible but not independently actionable. The reference implies discrete dashboard controls. The redesign should split these into separate direct actions: Project progress → Projects, Centres available → Centres, and Open opportunities → Opportunities.

## Recommended Metric Specification for the Next Home Iteration

| Component | Target metric | Rationale |
|---|---:|---|
| Page gutter | 16 dp | Matches the denser reference framing while retaining comfortable mobile touch layout. |
| Dashboard card padding | 16 dp | Reduces the current 21 dp visual bulk. |
| Dashboard card visual height | 192–208 dp | Keeps the snapshot as a dominant first card without pushing Next steps below the fold. |
| Ring diameter | 72–76 dp | Establishes the progress figure as the focal analytical element. |
| Ring stroke | 5–6 dp | Gives the ring reference-like visual weight. |
| Dashboard heading | 14–16 sp | Restores the compact dashboard-card hierarchy. |
| Greeting | 20–23 sp name and 13–15 sp context | Brings Home closer to the reference’s compact top hierarchy. |
| Quick actions | 2 columns, 2 rows; each at least 48 dp high | Preserves real routes while matching the reference’s scan-friendly tile layout. |
| Bottom navigation | 72–80 dp visual target, subject to Android insets | Reduces the current 89 dp dominance without compromising touch targets. |

## Validation Limitation

This review does **not** claim visual parity. The current code compiles, but no screenshot has been captured from a physical device or emulator after the makeover. The next objective evidence should be a 360 dp or 393 dp-wide device screenshot captured from the rebuilt APK and compared to the reference using the target specification above.

## References

| Reference | Use |
|---|---|
| User-supplied Concept 4 — Insight Analytics Hub screenshot | Visual hierarchy, dashboard-card, quick-action, and activity-timeline reference. |
| `RtcDesignTokens.kt` | Exact current native spacing, sizing, radius, and density metrics. |
| `Theme.kt` | Exact current native typography scale. |
| `MainActivity.kt` | Exact current Home dashboard layout and live metric wiring. |
