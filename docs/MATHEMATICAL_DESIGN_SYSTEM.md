# RTC Community Concept 6 Mathematical Design System

**System:** Concept 6 proportional refinement
**Application:** RTC Community 1.0.5
**Application ID:** `za.org.rtc.community`
**Effective date:** 26 August 2026

## 1. Purpose

This document defines the mathematical relationships, semantic tokens and deliberate exceptions that govern RTC Community's Compose interface. It refines Concept 6; it does not create a new visual identity or alter the product's navigation, trust model, backend workflows or civic purpose.

The system uses three mathematical ideas only where they improve a real relationship:

- the Golden Ratio for selected proportional relationships;
- the Fibonacci sequence for a coherent spatial and temporal vocabulary;
- π for genuine circular calculations.

Accessibility, Android ergonomics, content integrity and functional correctness take precedence over numerical purity.

## 2. Source of truth

| Concern | Source |
|---|---|
| Proportional constants and breakpoints | `ui/theme/RtcMath.kt` |
| Spacing, radius, size, stroke, aspect, motion and density | `ui/theme/RtcDesignTokens.kt` |
| Color and typography | `ui/theme/Theme.kt` |
| Shared component enforcement | `ui/components/Concept6Components.kt` |
| Screen-level composition | `MainActivity.kt` |

Screen and shared-component code must use semantic tokens. Raw measurements belong only in the centralized token definitions or in a documented platform/data exception.

## 3. Mathematical foundation

`RtcMath` centralizes the following constants:

| Symbol | Value | Permitted use |
|---|---:|---|
| `Phi` | 1.61803398875 | Golden rectangles and genuine proportional relationships |
| `GoldenMajor` | 0.61803398875 | Dominant region weighting |
| `GoldenMinor` | 0.38196601125 | Supporting region weighting |
| `Pi` | 3.141592653589793 | Circumference and area of custom circles |

The pure helpers are:

- `goldenLandscapeHeight(width) = width / Phi`;
- `circleCircumference(radius) = 2 × Pi × radius`;
- `circleArea(radius) = Pi × radius²`.

Do not scatter `0.618f`, `1.618f` or local π approximations through Composables. Do not use proportional mathematics for ordinary padding.

## 4. Fibonacci spatial rhythm

| Token | Value | Intended use |
|---|---:|---|
| `micro` | 3dp | Optical correction only |
| `tiny` | 5dp | Closely related labels and indicators |
| `compact` | 8dp | Icon-label and compact control gaps |
| `small` | 13dp | Internal groups and dense card padding |
| `standard` | 21dp | Page gutters and comfortable content padding |
| `section` | 34dp | Major section separation |
| `largeSection` | 55dp | Empty-state or major structural separation |
| `hero` | 89dp | Rare hero-level separation |

Semantic aliases such as `pageGutter`, `cardPadding`, `dialogPadding`, `relatedText`, `iconLabel` and `sectionGap` are preferred at call sites. The numeric family is implementation detail; the semantic role is the design contract.

## 5. Radius, stroke and elevation

### Radius family

| Token | Value | Intended use |
|---|---:|---|
| `small` | 8dp | Compact containers |
| `medium` | 13dp | Controls and actions |
| `large` | 21dp | Standard cards, fields and banners |
| `hero` | 34dp | Rare high-emphasis containers |

True pills, avatars, chips and FABs use `CircleShape`. A fabricated large corner value such as `999.dp` is prohibited.

### Surface treatment

| Token | Value | Intended use |
|---|---:|---|
| `RtcStroke.hairline` | 1dp | Standard borders and dividers |
| `RtcStroke.emphasis` | 2dp | Loading or high-emphasis stroke |
| `RtcElevation.flat` | 0dp | Concept 6 calm, contrast-led card hierarchy |

Concept 6 communicates hierarchy primarily through surface contrast, spacing and outline restraint rather than unrelated shadows.

## 6. Size classes and accessibility exceptions

| Token | Value | Intended use |
|---|---:|---|
| `minimumTouchTarget` | 48dp | Minimum interactive target; accessibility exception |
| `settingsRowHeight` | 55dp | Settings and disclosure rows |
| `fabDiameter` | 55dp | True circular floating action |
| `inlineIcon` | 16dp | Chip and inline metadata icons |
| `actionIcon` | 24dp | Material action/navigation icon exception |
| `largeIcon` / `heroIcon` | 34dp | Directory and purposeful-state emphasis |
| `avatarCompact` | 34dp | Dense comment identity |
| `avatarStandard` | 42dp | Feed/detail identity; ergonomic exception |
| `avatarLarge` | 55dp | Profile editing |
| `avatarProfile` | 89dp | Account identity hero |
| `adaptiveCardMinWidth` | 144dp | Wrapping metric and directory cards |
| `welcomeHeroHeight` | 144dp | Public-welcome hero minimum |
| `staffPaneWidth` | 233dp | Expanded staff navigation pane |
| `qrCode` | 233dp | Maximum displayed square TOTP QR |
| `bottomNavigationHeight` | 89dp | Resident navigation with safe vertical rhythm |

The 48dp touch target, 24dp Material action icon and 42dp standard avatar are intentional Android/optical exceptions. They must not be changed merely to make every value a Fibonacci number.

## 7. Typography

The scale is tempered for Android readability and font scaling. It uses Fibonacci anchors without the oversized jumps of a literal φ type scale.

| Material role | Size | Line height | Default weight |
|---|---:|---:|---|
| `labelSmall` | 11sp | 16sp | Medium |
| `labelMedium` | 12sp | 17sp | Medium |
| `labelLarge` | 14sp | 20sp | Semi-bold |
| `bodySmall` | 13sp | 19sp | Regular |
| `bodyMedium` | 15sp | 22sp | Regular |
| `bodyLarge` | 17sp | 25sp | Regular |
| `titleSmall` | 16sp | 21sp | Semi-bold |
| `titleMedium` | 18sp | 24sp | Semi-bold |
| `titleLarge` | 21sp | 27sp | Semi-bold |
| `headlineSmall` | 23sp | 29sp | Bold |
| `headlineMedium` | 26sp | 32sp | Bold |
| `headlineLarge` | 29sp | 36sp | Bold |
| `displaySmall` | 34sp | 42sp | Bold |

No screen-level Composable should define a local `sp` value. Long text remains content-driven and uses bounded content width on expanded displays.

## 8. Aspect-ratio family

| Token | Ratio | Use |
|---|---:|---|
| `square` | 1:1 | QR codes and true square assets |
| `goldenLandscape` | 1.618:1 | Community feed preview framing |
| `landscape` | 16:9 | Conventional landscape media where required |
| `portrait` | 4:5 | Conventional portrait media where required |

Community preview cards use `aspectRatio(RtcMath.Phi)`. Full-screen and source media retain their intended/native behavior. User media must never be destructively transformed merely to satisfy φ.

The TOTP QR remains square, centered and backed by a 512×512 encoded bitmap. Golden-ratio treatment is explicitly prohibited for QR geometry.

## 9. Density modes

| Mode | Outer padding | Card padding | List gap | Group gap | Audience |
|---|---:|---:|---:|---:|---|
| `RESIDENT_COMFORTABLE` | 21dp | 21dp | 13dp | 13dp | Home, Explore, Support, Account |
| `FEED_CONTENT` | 21dp | 13dp | 13dp | 8dp | Community feed |
| `ADMIN_COMPACT` | 13dp | 13dp | 8dp | 8dp | Operational workspaces |
| `ANALYTICAL_DENSE` | 13dp | 13dp | 8dp | 5dp | Analytics, health and activity |

`RtcScreenScaffold` provides the selected density through `LocalRtcContentDensity`. Nested `RtcCard` instances inherit it automatically unless a component has an explicit local reason to override it. This keeps resident and protected areas in one design system while permitting different information densities.

## 10. Responsive model and grid

| Window class | Width | Behavior |
|---|---:|---|
| `COMPACT` | below 600dp | Stacked content and four-item bottom navigation |
| `MEDIUM` | 600–839dp | Stacked/selectively adaptive content with bounded readable width |
| `EXPANDED` | 840dp and above | Navigation rail or 233dp staff pane with centered bounded content |

The 600dp and 840dp thresholds are Android adaptive-layout exceptions, not spatial tokens. `classifyRtcWindowWidth` is the only screen-classification function.

Shared scaffolds align screen edges through density-derived gutters and center content within a 987dp resident or 1597dp protected maximum. Dynamic cards remain content-driven. Expanded navigation never introduces a fifth resident destination.

## 11. Motion vocabulary

| Token | Duration | Intended use |
|---|---:|---|
| `microFeedback` | 89ms | Immediate micro feedback |
| `stateChange` | 144ms | Compact state transition |
| `standardTransition` | 233ms | Standard screen/control transition |
| `contextualTransition` | 377ms | Context or sheet transition |
| `deliberateEmphasis` | 610ms | Rare deliberate emphasis |

The audited implementation contained no arbitrary animation durations requiring migration. No decorative animation was added solely to exercise these tokens. New motion must select the shortest suitable duration, use a small professional easing vocabulary and respect Android's animator/reduced-motion behavior.

## 12. Shared component rules

- `RtcScreenScaffold` owns bounded width, page gutters, list rhythm and density propagation.
- `RtcCard` owns card radius, outline, flat elevation, inherited padding and clickable minimum height.
- `RtcStatusChip` owns true pill geometry, semantic text and non-color state communication.
- `RtcResidentBottomNavigation` accepts exactly the supplied four resident items and uses semantic icons/bar height.
- `RtcComposeFab` owns a 55dp true circle and centered 24dp action icon.
- `RtcSearchField`, settings rows, protected banners, emergency banners and empty-state actions use semantic dimensions and accessible targets.
- Dynamic feed, support and operational records retain content-driven heights.

## 13. Concept 6 color discipline

The proportional refinement preserves the established palette:

- deep ink and graphite for calm structural surfaces;
- emerald/mint for resident action and success;
- civic gold for protected administration and consequential context;
- danger red only for actual danger/error semantics.

Accent dominance remains restrained. Geometry and spacing do not redefine semantic color meaning.

## 14. Correct implementation patterns

Preferred:

```kotlin
Column(verticalArrangement = Arrangement.spacedBy(RtcSpacing.compact))

RtcScreenScaffold(density = RtcContentDensity.ADMIN_COMPACT) { /* content */ }

Modifier.fillMaxWidth().aspectRatio(RtcMath.Phi)

Modifier.sizeIn(minHeight = RtcSize.minimumTouchTarget)
```

Prohibited:

```kotlin
Modifier.padding(14.dp)

RoundedCornerShape(999.dp)

Modifier.height(184.dp)

Modifier.width(screenWidth * 0.618f)
```

The prohibited examples show the category of problem; centralized token declarations remain the legitimate location for physical values.

## 15. Exception register

| Exception | Reason | Required behavior |
|---|---|---|
| 48dp touch targets | Android accessibility | Never reduce to a nearby Fibonacci value |
| 24dp action icons | Material ergonomics | Use the named action-icon token |
| 600/840dp breakpoints | Android adaptive guidance | Classify centrally |
| 42dp standard avatar | Optical density | Use only through the named token |
| 512×512 QR payload | Scanner reliability | Keep encoded pixels square and undistorted |
| Native/full-screen media | Content integrity | Preserve appropriate content behavior |
| Dynamic card height | Text/media variability | Never force feed/support records into φ height |
| System insets | Platform ownership | Consume platform-calculated values |
| Font scaling | Accessibility | Allow reflow; avoid fixed text container heights |

## 16. Verification

Source contracts:

```bash
python3 tools/tests/run_contract_tests.py
```

Screen-level raw-measurement check:

```bash
rg -n '\b[0-9]+(?:\.[0-9]+)?\.dp\b' \
  app/src/main/java/za/org/rtc/community/MainActivity.kt \
  app/src/main/java/za/org/rtc/community/ui/components/Concept6Components.kt
```

Required Android gates:

```bash
gradle --no-daemon --stacktrace testDebugUnitTest
gradle --no-daemon --stacktrace lintDebug
gradle --no-daemon --stacktrace assembleDebug
```

The final device gate must cover compact/expanded layouts, light/dark/system themes, Simplified Reading Mode, 200% font scale, TalkBack traversal, media playback, TOTP QR scanning and the four-destination resident navigation.
