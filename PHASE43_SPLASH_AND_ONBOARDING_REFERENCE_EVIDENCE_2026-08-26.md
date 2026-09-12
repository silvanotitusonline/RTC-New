# Phase 43 — Splash and Onboarding Reference Evidence

**Date:** 2026-08-26  
**Branch:** `recovery/nonproduction-baseline`  
**Scope:** Native Android splash, Welcome, Sign in, and Create account UI  
**Production status:** Unchanged. No production Supabase project, user data, authentication configuration, credential, policy, or release channel was accessed or modified.

## Reference interpretation

The supplied mobile references establish a dark graphite onboarding flow with an emerald primary action, a compact RTC mark on Welcome and authentication screens, high-contrast display headings, generous vertical spacing, outlined input fields, and no simulated handset chrome inside the app content. The supplied **Rise Tsantsabane Communities / RTC** logo is intended specifically as the splash mark; it is not substituted into the compact Welcome and authentication lockup shown by the references.

No supplied attachment was reopened during implementation. The layout and labels were applied from the user-provided visual references and task requirements.

## Implemented behavior

| Surface | Reference-aligned implementation | Functional behavior |
|---|---|---|
| Android splash | Uses `rtc_community_logo_transparent.png` over the existing graphite splash background for Android 12+ and legacy splash resources. | The supplied RTC Community mark is a true RGBA asset with transparent pixels. No `09:41`, `5G`, network, battery, or other simulated status-bar artwork is added. |
| Welcome | Adds centered RTC lockup, **Welcome to RTC Community**, the official-updates description, **Your community. Your voice.**, **Get started**, **Sign in**, and Create-account link. | Get started and Create account open the real account-creation state; Sign in opens the real sign-in state. |
| Sign in | Adds full-page **Sign in to RTC Community** layout with email, password, password recovery, primary sign-in action, account-creation link, and Back action. | Sign in calls the existing `onSignIn(email, password)` callback. Forgotten-password requests call the existing recovery callback for the entered email. |
| Create account | Adds full-page **Create your RTC account** layout with name, email, password, password confirmation, verified-email disclosure, primary registration action, sign-in link, and Back action. | Create account calls the existing `onSignUp(email, password, displayName)` callback. Client-side confirmation prevents submission until both passwords match; server-side account/password policy remains authoritative. |
| Unsupported visual control avoidance | The reference’s “Continue with Google” button was not included because Google OAuth is not backed by the recovered authentication implementation. A Terms/Privacy acceptance checkbox was not added because the app has no connected legal-document workflow or server-recorded acceptance path. | No decorative OAuth or legal-consent control is presented as functional. |

## Asset integrity

The first generative transparency attempt was rejected because it retained a visible checkerboard rather than an alpha channel. The final source asset is a true RGBA PNG produced from the supplied image by removing only exact black background pixels; all non-black logo elements were preserved. The source regression suite now checks both `RGBA` mode and the existence of transparent alpha pixels to prevent regression to a black or checkerboard splash background.

## Validation

| Check | Result |
|---|---|
| Onboarding source contracts | Passed. Covers splash-only supplied logo usage, compact Welcome logo separation, Welcome labels, full-page Sign in/Create account states, password confirmation, real auth/recovery callbacks, Back action, and absence of unsupported Google/Terms controls. |
| Complete source-contract suite | **69/69 passed** via `uv run --no-project tools/tests/run_contract_tests.py`. |
| Kotlin compile and resource processing | **Passed** via `:app:compileDebugKotlin :app:processDebugResources`. |
| Final debug packaging | **Passed** via `:app:assembleDebug`. |
| APK structural integrity | **Passed** ZIP integrity check. |
| APK identity | `za.org.rtc.community` version `1.0.5` (`versionCode 30`), min SDK 26, target SDK 36. |
| APK SHA-256 | `9880e727a1744874ebf81f745d4a42ef06033e0ff38e6f21b97911683d01ea37` |

## Remaining release limitations

This remains a locally built **debug APK**, not a production release approval. No connected Android device, emulator binary, Android Virtual Device, system image, instrumentation suite, or fresh authenticated isolated runtime is available in the restored environment. Consequently, visual fit on an actual handset, Android’s platform splash rendering, verified-email completion, sign-in, password recovery, and sign-out must still be validated in a safe isolated device session before release approval.
