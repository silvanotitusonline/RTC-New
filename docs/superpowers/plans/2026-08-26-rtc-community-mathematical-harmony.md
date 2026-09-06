# RTC Community Mathematical Harmony Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Refine RTC Community Concept 6 into a centralized, accessible and responsive proportional design system without changing product, navigation, security or backend behavior.

**Architecture:** Add pure mathematical helpers and static semantic Compose tokens under `ui/theme`, then make shared Concept 6 components enforce the system. Migrate screen-level measurements in `MainActivity.kt` by semantic category, retaining explicit platform/accessibility exceptions and using GitHub Actions for the Android compiler, lint and artifact gates.

**Tech Stack:** Kotlin 2.3, Jetpack Compose Material 3, Navigation Compose, JUnit 4, Python source-contract tests, Gradle 8.13, Android Gradle Plugin 8.13.2, JDK 21, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-08-26-rtc-community-mathematical-harmony-design.md`

## Global Constraints

- Resident primary navigation remains Home / Community / Explore / Support only.
- Account remains a secondary route.
- Concept 6 branding, semantic palette and information architecture remain unchanged.
- Protected routes remain fail-closed; System Administrator routes retain MFA/AAL2 enforcement.
- Supabase migrations, RPCs, RLS, Edge Functions and production data are unchanged by this UI refinement.
- RTC AI retains the existing Gemini-backed Proposal → Review → Confirm → Transaction → Audit contract.
- Interactive targets remain at least 48dp.
- QR codes remain square and user media is not destructively cropped to satisfy φ.
- No privileged credentials, signing material or `google-services.json` enter source control.

---

### Task 1: Add Failing Mathematical-Harmony Contracts

**Files:**

- Create: `tools/tests/test_mathematical_harmony.py`
- Create: `app/src/test/java/za/org/rtc/community/ui/theme/RtcMathTest.kt`

**Interfaces:**

- Consumes: existing Python contract runner and JUnit 4 dependency.
- Produces: executable requirements for `RtcMath`, semantic tokens, responsive classes, Golden media framing, circular shapes and removal of raw screen-level `dp` literals.

- [ ] **Step 1: Add source-contract tests**

```python
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[2]
THEME = ROOT / "app/src/main/java/za/org/rtc/community/ui/theme"
MAIN = (ROOT / "app/src/main/java/za/org/rtc/community/MainActivity.kt").read_text()
COMPONENTS = (ROOT / "app/src/main/java/za/org/rtc/community/ui/components/Concept6Components.kt").read_text()


def test_mathematical_foundation_is_centralized():
    math = (THEME / "RtcMath.kt").read_text()
    tokens = (THEME / "RtcDesignTokens.kt").read_text()
    for symbol in ["Phi", "GoldenMajor", "GoldenMinor", "Pi", "goldenLandscapeHeight", "circleCircumference"]:
        assert symbol in math
    for value in ["3.dp", "5.dp", "8.dp", "13.dp", "21.dp", "34.dp", "55.dp", "89.dp"]:
        assert value in tokens
    assert "minimumTouchTarget = 48.dp" in tokens


def test_density_and_responsive_contracts_exist():
    tokens = (THEME / "RtcDesignTokens.kt").read_text()
    math = (THEME / "RtcMath.kt").read_text()
    for density in ["RESIDENT_COMFORTABLE", "FEED_CONTENT", "ADMIN_COMPACT", "ANALYTICAL_DENSE"]:
        assert density in tokens
    for width in ["COMPACT", "MEDIUM", "EXPANDED"]:
        assert width in math
    assert "600" in math and "840" in math


def test_shared_geometry_uses_tokens_and_true_circles():
    assert "RoundedCornerShape(999.dp)" not in COMPONENTS
    assert "CircleShape" in COMPONENTS
    assert "RtcSpacing." in COMPONENTS
    assert "RtcRadius." in COMPONENTS
    assert "RtcSize.minimumTouchTarget" in COMPONENTS


def test_screen_measurements_are_semantic_and_media_is_golden():
    assert re.search(r"\b\d+(?:\.\d+)?\.dp\b", MAIN) is None
    preview = re.search(r"private fun CommunityMediaPreview\(.*?(?=\n@Composable|\Z)", MAIN, re.S).group(0)
    assert "aspectRatio(RtcMath.Phi)" in preview
    assert ".height(184.dp)" not in preview
    assert "RtcWindowWidth" in MAIN


def test_motion_vocabulary_and_documentation_exist():
    tokens = (THEME / "RtcDesignTokens.kt").read_text()
    for duration in ["89", "144", "233", "377", "610"]:
        assert duration in tokens
    assert (ROOT / "docs/MATHEMATICAL_DESIGN_SYSTEM.md").exists()
    assert (ROOT / "docs/MATHEMATICAL_HARMONY_AUDIT.md").exists()
```

- [ ] **Step 2: Add pure JVM mathematical tests**

```kotlin
package za.org.rtc.community.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class RtcMathTest {
    @Test fun goldenSubdivisionsFormAWhole() {
        assertEquals(1f, RtcMath.GoldenMajor + RtcMath.GoldenMinor, 0.00001f)
    }

    @Test fun goldenLandscapeHeightUsesPhi() {
        assertEquals(200f, RtcMath.goldenLandscapeHeight(323.6068f), 0.001f)
    }

    @Test fun circularGeometryUsesPi() {
        assertEquals(62.83185f, RtcMath.circleCircumference(10f), 0.001f)
        assertEquals(314.15927f, RtcMath.circleArea(10f), 0.001f)
    }

    @Test fun widthClassesRespectAndroidBreakpoints() {
        assertEquals(RtcWindowWidth.COMPACT, classifyRtcWindowWidth(599))
        assertEquals(RtcWindowWidth.MEDIUM, classifyRtcWindowWidth(600))
        assertEquals(RtcWindowWidth.MEDIUM, classifyRtcWindowWidth(839))
        assertEquals(RtcWindowWidth.EXPANDED, classifyRtcWindowWidth(840))
    }
}
```

- [ ] **Step 3: Run the source contracts and verify the new suite fails for missing implementation files**

Run: `python3 tools/tests/run_contract_tests.py`

Expected: the existing 29 contracts pass; the new mathematical-harmony tests fail because `RtcMath.kt`, `RtcDesignTokens.kt` and the two documentation files do not yet exist.

- [ ] **Step 4: Commit the failing contracts**

```bash
git add tools/tests/test_mathematical_harmony.py app/src/test/java/za/org/rtc/community/ui/theme/RtcMathTest.kt
git commit -m "test(ui): define mathematical harmony contracts"
```

---

### Task 2: Implement Pure Mathematics and Semantic Tokens

**Files:**

- Create: `app/src/main/java/za/org/rtc/community/ui/theme/RtcMath.kt`
- Create: `app/src/main/java/za/org/rtc/community/ui/theme/RtcDesignTokens.kt`
- Modify: `app/src/main/java/za/org/rtc/community/ui/theme/Theme.kt`

**Interfaces:**

- Produces: `RtcMath`, `RtcWindowWidth`, `classifyRtcWindowWidth`, `RtcSpacing`, `RtcRadius`, `RtcSize`, `RtcStroke`, `RtcAspectRatio`, `RtcMotion`, `RtcContentDensity` and `RtcDensityMetrics`.
- Consumers: shared components, `MainActivity.kt`, JVM tests and source contracts.

- [ ] **Step 1: Add the pure mathematical foundation**

```kotlin
object RtcMath {
    const val Phi = 1.61803398875f
    const val GoldenMajor = 0.61803398875f
    const val GoldenMinor = 0.38196601125f
    const val Pi = 3.141592653589793

    fun goldenLandscapeHeight(width: Float): Float = width / Phi
    fun circleCircumference(radius: Float): Float = (2.0 * Pi * radius).toFloat()
    fun circleArea(radius: Float): Float = (Pi * radius * radius).toFloat()
}

enum class RtcWindowWidth { COMPACT, MEDIUM, EXPANDED }

fun classifyRtcWindowWidth(widthDp: Int): RtcWindowWidth = when {
    widthDp < 600 -> RtcWindowWidth.COMPACT
    widthDp < 840 -> RtcWindowWidth.MEDIUM
    else -> RtcWindowWidth.EXPANDED
}
```

- [ ] **Step 2: Add the semantic token families**

Implement the exact Fibonacci spacing values, 8/13/21/34dp radius progression, 48dp accessibility target, component-specific size classes, 1/2dp stroke classes, Golden aspect values and 89/144/233/377/610ms motion vocabulary. Add four density modes whose metrics are derived solely from these tokens.

- [ ] **Step 3: Complete the tempered Material typography scale**

Update `Theme.kt` so Material roles cover `labelSmall` through `displaySmall`, preserve 13/21/34sp anchors, and use line heights from the approved specification. Remove the obsolete five-field `RtcDimensions` model and its composition local after all component consumers move to the new static semantic tokens.

- [ ] **Step 4: Run pure and source tests**

Run: `python3 tools/tests/run_contract_tests.py`

Run in Android CI: `gradle --no-daemon --stacktrace testDebugUnitTest`

Expected locally: mathematical foundation and responsive tests advance; screen/component migration tests remain red.

- [ ] **Step 5: Commit the foundation**

```bash
git add app/src/main/java/za/org/rtc/community/ui/theme app/src/test/java/za/org/rtc/community/ui/theme/RtcMathTest.kt
git commit -m "refactor(ui): introduce proportional Concept 6 tokens"
```

---

### Task 3: Migrate Shared Concept 6 Components

**Files:**

- Modify: `app/src/main/java/za/org/rtc/community/ui/components/Concept6Components.kt`

**Interfaces:**

- Consumes: all token families from Task 2.
- Produces: token-enforced scaffolds, cards, status chips, navigation, fields, rows, banners, empty states and FABs with existing call signatures preserved through default parameters.

- [ ] **Step 1: Make `RtcScreenScaffold` density-aware**

Add `density: RtcContentDensity = RtcContentDensity.RESIDENT_COMFORTABLE`. Resolve its `RtcDensityMetrics`, use semantic top/bottom gutters and list gap, and center a bounded `LazyColumn` on medium/expanded widths without changing content callbacks.

- [ ] **Step 2: Normalize component geometry**

Use `RtcRadius.large` for cards, `RtcStroke.hairline` for borders, density-derived card padding and `RtcSpacing.small` for internal grouping. Preserve content-driven card height.

- [ ] **Step 3: Correct circular controls**

Replace the `RoundedCornerShape(999.dp)` status chip with `CircleShape`; enforce semantic chip height/padding. Apply `CircleShape` and the semantic 55dp diameter to `RtcComposeFab`.

- [ ] **Step 4: Normalize fields, rows, banners and empty states**

Use the 21dp field radius, 55dp settings-row minimum, 48dp action minimum, semantic padding/gaps and existing semantic colors. Preserve button roles, content descriptions and non-color status text.

- [ ] **Step 5: Normalize resident bottom navigation**

Retain exactly four supplied items. Use semantic action-icon size, a bounded navigation height, circular active indicator and minimum touch target without introducing Account.

- [ ] **Step 6: Run contracts**

Run: `python3 tools/tests/run_contract_tests.py`

Expected: shared geometry tests pass; `MainActivity.kt` migration and documentation tests remain red.

- [ ] **Step 7: Commit shared components**

```bash
git add app/src/main/java/za/org/rtc/community/ui/components/Concept6Components.kt
git commit -m "refactor(ui): harmonize shared Concept 6 geometry"
```

---

### Task 4: Refine App Shell, Navigation and Responsive Structure

**Files:**

- Modify: `app/src/main/java/za/org/rtc/community/MainActivity.kt`

**Interfaces:**

- Consumes: `RtcWindowWidth`, `classifyRtcWindowWidth`, proportional size/spacing tokens and existing route APIs.
- Preserves: `NavHost`, `RouteAccessPolicy`, `navigatePrimary`, `navigateOverlay`, notification routing and all callbacks.

- [ ] **Step 1: Replace the boolean 840dp check with the centralized window class**

Use `classifyRtcWindowWidth(LocalConfiguration.current.screenWidthDp)` and derive expanded navigation from `RtcWindowWidth.EXPANDED`. Medium layouts remain stacked unless a screen explicitly supports a safe dual-pane relationship.

- [ ] **Step 2: Normalize expanded navigation geometry**

Move the 248dp staff pane to the semantic 233dp Fibonacci pane width and use semantic padding/gaps. Retain the resident rail, staff tool visibility and skip-to-content control.

- [ ] **Step 3: Normalize top-bar and offline-banner rhythm**

Use semantic padding, icon gaps and touch targets while preserving title, workspace selector, search, reading mode, notifications and profile behavior.

- [ ] **Step 4: Run the existing navigation/security contracts**

Run: `python3 tools/tests/run_contract_tests.py`

Expected: four-destination navigation and protected-route tests remain green.

- [ ] **Step 5: Commit shell refinement**

```bash
git add app/src/main/java/za/org/rtc/community/MainActivity.kt
git commit -m "refine(ui): normalize navigation and responsive shell"
```

---

### Task 5: Migrate Resident, Media, Form and Dialog Surfaces

**Files:**

- Modify: `app/src/main/java/za/org/rtc/community/MainActivity.kt`

**Interfaces:**

- Preserves: all resident screen parameters, ViewModel actions, support RPC behavior, community media callbacks and account/privacy actions.
- Produces: semantic spacing across Home, Community, Explore, Support, Account, Search, Notifications, Help and Public Welcome.

- [ ] **Step 1: Migrate Home and Explore hierarchy**

Replace 164dp metric/category widths with the semantic 144dp adaptive-card minimum, migrate layout gaps to 8/13/21dp tokens and retain content-driven heights and directory behavior.

- [ ] **Step 2: Migrate Community feed and detail rhythm**

Use feed density, semantic avatar/icon classes and related-text gaps. Keep post/comment cards content-driven and retain all moderation/report/edit behavior.

- [ ] **Step 3: Apply intentional media proportions**

Replace the fixed 184dp Community preview height with `Modifier.aspectRatio(RtcMath.Phi)`. Keep full-screen media, poster extraction, native content scale, signed-URL refresh and playback recovery unchanged.

- [ ] **Step 4: Migrate Support and Account**

Use resident density, normalized case progress, 89dp profile avatar, semantic settings rows and consistent section rhythm. Preserve real support persistence and Account’s secondary-route status.

- [ ] **Step 5: Migrate sheets, forms and dialogs**

Use 21dp dialog/sheet padding, 13dp field/group gaps, 8dp action gaps and minimum touch targets. Preserve error/helper visibility, destructive confirmation wording, photo/video bounds and QR square geometry.

- [ ] **Step 6: Migrate search, notifications, help and public welcome**

Use semantic list/card rhythm, bounded readable text widths and the 144dp hero minimum. Preserve all routes and sign-in/recovery behavior.

- [ ] **Step 7: Run source contracts**

Run: `python3 tools/tests/run_contract_tests.py`

Expected: resident, media and existing product contracts pass; protected screen raw-measurement migration may remain red until Task 6.

- [ ] **Step 8: Commit resident migration**

```bash
git add app/src/main/java/za/org/rtc/community/MainActivity.kt
git commit -m "refine(ui): harmonize resident and media surfaces"
```

---

### Task 6: Migrate Staff and Administrator Density

**Files:**

- Modify: `app/src/main/java/za/org/rtc/community/MainActivity.kt`

**Interfaces:**

- Preserves: all role/MFA checks, work-item lifecycle actions, content/moderation operations, RTC AI proposal confirmation, operational controls, alerts, analytics and audit behavior.

- [ ] **Step 1: Apply `ADMIN_COMPACT` density**

Use the shared density on Operations Hub, Work Queue, My Work, Staff Alerts, Content Management, Moderation, Access Management, Operational Controls and RTC AI.

- [ ] **Step 2: Apply `ANALYTICAL_DENSE` density**

Use the analytical density on Privacy Analytics, System Health and Administrative Activity. Replace metric-card fixed widths with the semantic adaptive-card minimum and retain label/value readability.

- [ ] **Step 3: Normalize protected warnings and consequential actions**

Use common protected banners, semantic status chips, 13dp card padding and 8dp control groups. Do not reduce confirmation prominence or touch targets.

- [ ] **Step 4: Remove remaining screen-level raw `dp` literals**

Classify every remaining occurrence as spacing, size, stroke or platform exception and reference the corresponding semantic token. The final `MainActivity.kt` must not match `\b\d+(?:\.\d+)?\.dp\b`.

- [ ] **Step 5: Run all source contracts**

Run: `python3 tools/tests/run_contract_tests.py`

Expected: all existing and mathematical-harmony source contracts pass except documentation existence if Task 7 has not run.

- [ ] **Step 6: Commit protected-surface migration**

```bash
git add app/src/main/java/za/org/rtc/community/MainActivity.kt
git commit -m "refine(ui): unify protected workspace density"
```

---

### Task 7: Document and Score the Final System

**Files:**

- Create: `docs/MATHEMATICAL_DESIGN_SYSTEM.md`
- Create: `docs/MATHEMATICAL_HARMONY_AUDIT.md`
- Modify: `README.md`
- Modify: `docs/PRODUCTION_READINESS_REPORT.md`

**Interfaces:**

- Produces: maintainable token guidance, exception register, before/after literal inventory, evidence-based coherence score and honest release classification.

- [ ] **Step 1: Document the design system**

Document φ usage, Fibonacci spacing, typography, radii, size classes, density modes, responsive breakpoints, motion, circular geometry, media rules and examples of correct/incorrect token use.

- [ ] **Step 2: Record accessibility/platform exceptions**

Explicitly record 48dp touch targets, 24dp Material action icons, 600/840dp breakpoints, square QR codes, native user-media ratios and dynamic content-driven card heights.

- [ ] **Step 3: Produce the final audit**

Score all 15 requested categories with evidence from source and CI. Do not award 100 where rendered/device evidence is unavailable. Include remaining visual/device verification gates.

- [ ] **Step 4: Update project documentation**

Add the mathematical-design-system verification commands to README and update the production-readiness report without claiming a binary GO before CI succeeds.

- [ ] **Step 5: Run all local verification**

Run: `python3 tools/tests/run_contract_tests.py`

Run: `rg -n '\b[0-9]+(?:\.[0-9]+)?\.dp\b' app/src/main/java/za/org/rtc/community/MainActivity.kt`

Expected: all contracts pass and the raw-literal search returns no matches.

- [ ] **Step 6: Commit documentation**

```bash
git add README.md docs/MATHEMATICAL_DESIGN_SYSTEM.md docs/MATHEMATICAL_HARMONY_AUDIT.md docs/PRODUCTION_READINESS_REPORT.md
git commit -m "docs(ui): publish mathematical harmony system and audit"
```

---

### Task 8: Publish, Compile, Fix and Package

**Files:**

- Modify only when CI evidence identifies a causal compile/lint/test defect.
- Produce through CI: debug APK, unit-test report and lint report.
- Produce conditionally: release APK and AAB when Firebase/signing configuration permits.

**Interfaces:**

- Consumes: all local commits and GitHub Android workflow.
- Produces: feature branch, pull request, CI evidence, final GitHub commit SHA and downloadable artifacts.

- [ ] **Step 1: Create the remote feature branch**

Branch from baseline commit `c40c47ea4ddfa8442905fa56203ccec83457f23f` as `feature/mathematical-harmony-system`.

- [ ] **Step 2: Publish logical commits**

Mirror the local task commits to the feature branch using atomic Git tree commits. Do not include generated caches, local properties, secrets or build output.

- [ ] **Step 3: Create a pull request**

Use a PR title such as `refactor(ui): mathematically harmonize Concept 6` and include constraints, test evidence, accessibility exceptions and artifact expectations.

- [ ] **Step 4: Run Android gates**

Required workflow commands:

```text
python3 tools/tests/run_contract_tests.py
gradle --no-daemon --stacktrace testDebugUnitTest
gradle --no-daemon --stacktrace lintDebug
gradle --no-daemon --stacktrace assembleDebug
```

Conditional commands:

```text
gradle --no-daemon --stacktrace assembleRelease bundleRelease
```

- [ ] **Step 5: Diagnose any failure from the actual logs**

Fetch the failing job/step logs, identify the first causal compiler/lint/test error, add a focused regression where possible, fix the source and rerun the complete workflow. Do not disable lint or remove functionality.

- [ ] **Step 6: Review the final diff and security boundary**

Confirm the four resident destinations, protected routes, MFA contract, Supabase/Edge Function hashes, no privileged credentials and no backend/data changes.

- [ ] **Step 7: Merge only after required gates pass**

Merge the verified PR into `main`; otherwise leave it open and report the precise blocker. Record the final commit SHA and merge status.

- [ ] **Step 8: Retrieve available artifacts**

Download the debug APK, lint report and unit-test report. Retrieve release APK/AAB only when the workflow produced them. Report missing conditional artifacts honestly.
