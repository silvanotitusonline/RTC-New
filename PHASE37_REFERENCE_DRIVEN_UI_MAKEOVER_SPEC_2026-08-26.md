# Phase 37 — Reference-Driven RTC Community UI Makeover Specification

**Date:** 2026-08-26
**Scope:** Native Android visual makeover only; existing server security and truthfulness controls remain unchanged.

## Visual Direction

The supplied reference screens specify a **dark civic insight** interface, with a near-black graphite background, layered charcoal surfaces, a vivid emerald as the primary action and active-state color, and restrained gold for priority, progress, and selected status. The implementation uses the reference palette directly:

| Token | Value | Intended use |
|---|---:|---|
| RTC emerald | `#2EC27E` | Primary buttons, active tabs, selected bottom navigation, progress, positive status |
| RTC gold | `#D4AF37` | Priority, emphasis, important figures, active Home navigation |
| Graphite | `#0C1013` | Screen background and splash field |
| Raised surface | `#171D23` | Cards, inputs, dashboard panels |
| Surface outline | `#2B343D` | Borders and separators |
| Primary text | `#F5F7F8` | Headings and key values |
| Secondary text | `#AAB3BA` | Supporting text, timestamps, labels |

> The supplied images are a design reference, not evidence of new backend behavior. Every visual affordance must map to an existing secure callback or be presented as non-interactive status content.

## Screen-by-Screen Layout Requirements

| Surface | Required reference-driven composition | Functional rule |
|---|---|---|
| Splash | Full graphite field; centered transparent RTC mark; small emerald loading indicator; no generic Android icon. | Startup behavior and the Android splash contract remain unchanged. |
| Welcome / onboarding | Centered transparent RTC mark, short civic-value message, emerald primary action, small page indicators, dark image texture only as a decorative synthetic asset. | Sign-in and sign-up actions retain existing authentication flows. |
| Sign in / sign up | Compact branded header, raised dark input fields, high-contrast emerald submit action, visible password action, concise verification state. | Current email/password validation and confirmation workflow remain authoritative; no fake social-login buttons. |
| Home | Greeting, notification/account actions, community-snapshot card, next-steps stack, activity timeline, and four quick actions. Visual progress values must use live data or neutral labels, never invented analytics. | Existing case/notices/directory callbacks remain the only actions. Unavailable metrics are described honestly. |
| Explore | Search treatment, small filter affordance, structured progress panel, active-project list, category grid, and clear bottom navigation. | Existing directory and search routes remain the only actions. No fabricated trend data. |
| Community | Header actions, Latest/Trending chips, composer prompt with media affordances, category chips, avatar-led feed cards, media preview, Like/Comment/Share row, official-notice treatment, floating compose button. | Like, comment, share, media and profile-avatar controls use the guarded, already implemented flows. Guidelines acceptance is persisted once at onboarding/server level, not repeatedly shown after confirmed acceptance. |
| Support | Emergency boundary, active-case cards with honest status stages, service-request shortcuts, activity list. | Existing support routes and case state remain authoritative. |

## Community Interaction Requirements

The Community screen must retain the reference hierarchy but never present a false interaction. Feed cards show only server-projected author avatars, Like state, comment count, and media. The share action sends only the registered app post URI and neutral text. Existing guidelines persistence remains the authority: the UI asks only when the server says the resident has not accepted; a confirmed resident can create posts and comments without a repeated gate.

## Asset Plan

| Asset | Format and placement | Constraint |
|---|---|---|
| RTC transparent logo | Transparent PNG, splash and welcome surfaces | Simple icon-first mark; no generated long-form typography required for correctness. |
| Synthetic member portraits | Square transparent or dark-neutral PNG/WebP assets for mocked development profiles | Clearly synthetic adults; diverse, non-identifiable; no real people or user likenesses. |
| Optional welcome texture | Low-contrast dark civic-neighbourhood illustration | Decorative only; contains no text, analytics, user data, or live map claim. |

## Acceptance Criteria

The makeover is acceptable only when the splash uses RTC branding rather than the generic Android presentation, primary tabs/buttons follow the emerald-and-gold hierarchy, Home/Explore/Community visually reflect the supplied layout direction, and all new visual controls preserve working routes or communicate their unavailable state. The app remains **NO-GO** until full packaging and synthetic device validation are completed.
