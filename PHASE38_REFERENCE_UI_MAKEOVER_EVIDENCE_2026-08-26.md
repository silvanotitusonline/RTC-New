# Phase 38 — Reference-Driven UI Makeover Evidence

**Date:** 2026-08-26
**Scope:** Local Android source and generated synthetic assets only
**Backend / production status:** No production query, deployment, data change, or security-control change
**Release status:** **NO-GO** pending authenticated device evidence and stable APK packaging

## Objective

This checkpoint implements the supplied visual references as a native Android makeover while retaining the existing secure Community, profile, media, Like, Share, comment, authorization, and one-time guideline flows. The supplied designs are treated as the visual specification; they do not create new backend claims or permissions.

## Delivered Visual System

| Area | Implemented result | Truthfulness boundary |
|---|---|---|
| Global visual language | The shared Compose theme now uses graphite `#0C1013`, emerald `#2EC27E`, and gold `#D4AF37` as the reference-driven system. | Colors change presentation only; role, route, MFA, RLS, and RPC behavior are unchanged. |
| Splash | Android 12+ and pre-Android-12 splash resources use the transparent RTC mark over the graphite field. | Startup behavior is unchanged; the generic visual treatment is replaced. |
| Welcome and authentication | The real public welcome flow now has an RTC brand lockup, civic onboarding copy, primary Create account action, sign-in action, and separated public browse paths. The existing auth dialog uses the same branding and an emerald full-width submit control. | Existing email/password verification, password recovery, message handling, and server authority remain the only account actions. No unimplemented social-login control was added. |
| Home | Existing live dashboard values are presented in a community snapshot panel with a progress indicator, gold figures, and action-linked service counts. | The panel uses `DashboardMetrics`; it explicitly states that progress is status-based and not financial expenditure. |
| Explore | Existing live directory counts are presented in a progress/overview panel; categories use more vibrant avatar-like icon containers. | No project performance, trend, or availability data is invented. Existing directories remain the only navigation routes. |
| Community | The feed now follows the supplied hierarchy: strong header, Latest/Trending controls, prominent media composer, avatar-led feed cards, local-feed status, floating post action, and functional Like, Comment, and Share affordances. | Like invokes the existing guarded RPC; Comment opens the guarded post-detail flow; Share uses the existing safe `rtc://community/post/{id}` path. |
| Guidelines | Composer/comment access still checks the server-derived `guidelinesAccepted` state. | The dialog is shown only while the current guideline version is not confirmed. After server-confirmed acceptance, it does not reappear for ordinary posting or commenting. |
| Synthetic portraits | Five clearly synthetic, non-identifiable adult community portraits are bundled as drawable assets. They are selected only when the build-only `DEVELOPMENT_ADAPTER` is active and a post has no server avatar. | In real authenticated sessions, the server-projected avatar URL remains first priority; the app otherwise shows initials. Synthetic images cannot replace a real profile photo. |

## Asset Inventory

| Asset | Path | Purpose |
|---|---|---|
| Transparent RTC mark | `app/src/main/res/drawable-nodpi/rtc_logo_mark_transparent.png` | Splash and brand lockup |
| Synthetic member portraits | `app/src/main/res/drawable-nodpi/synthetic_member_01.png` through `synthetic_member_05.png` | Debug-only Community preview fallback |

## Validation Results

| Validation | Result | Scope limitation |
|---|---|---|
| `:app:compileDebugKotlin` | **Passed** | Confirms native Kotlin/Compose compilation. |
| `:app:processDebugResources` | **Passed** | Confirms splash XML and generated drawable resources are valid Android resources. |
| Lightweight source contracts | **65/65 passed** | Source-level coverage only. |
| Full debug APK packaging | Not rerun | The reset-era dex packaging stage previously stalled; a stable complete package remains required. |
| Device visual review | Not run | No emulator/physical-device evidence yet for splash, dark palette, layout, playback sound, or share chooser. |
| Authenticated real-session verification | Not run | The owner-only synthetic runtime credential/TOTP files still require safe recovery. |

## Functional Controls Retained

The makeover did not introduce decorative fake actions. Home snapshot selection opens the existing Projects directory. Explore category cards retain their existing directory actions. Community Like calls the guarded server mutation; comment opens the authenticated Community detail flow; share sends the existing limited app URI; photo/video labels open the existing composer rather than a new local-only uploader. The video mute control and profile-photo propagation fixes from Phase 36 remain intact.

## Remaining Release Gates

> This is an implemented and compiled interface makeover, not a production release claim.

A release remains blocked until the app is packaged successfully in a stable environment, the new splash/welcome/Home/Explore/Community layouts are reviewed on an Android device, real authenticated synthetic residents verify image/video upload, profile-photo propagation, Like/comment/share, sound muting, and one-time guideline acceptance, and outstanding server security-definer warnings receive individual review.
