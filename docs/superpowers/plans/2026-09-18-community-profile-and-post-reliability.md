# Community Profile and Post Reliability Implementation Plan

> **For agentic workers:** This plan is implemented inline in the current session.

**Goal:** Make the resident profile/settings surface reachable and reliable, prevent duplicate Community posts, dismiss the composer after successful upload, improve media retry behavior, and stop routine sync activity from creating global status jitter.

**Architecture:** Preserve the existing Jetpack Compose + repository/RPC architecture. Use explicit typed callbacks and semantics for account navigation, a client-generated idempotency UUID carried into the draft RPC, retry-safe server finalization, and local UI state that treats a successful post as terminal before dismissing the sheet. Routine background sync remains available but is not presented as a global warning state.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Supabase PostgREST/Storage, PostgreSQL migrations, Python source-contract tests.

**Spec:** `/home/ubuntu/upload/pasted_content.txt`

## Global Constraints

- Keep the existing Android package and navigation contracts intact.
- Do not introduce Vue files into this Android repository; `vue-best-practices` is documented as not applicable to this codebase.
- Preserve existing source-level regression contracts and safe error handling.
- Do not expose credentials or make destructive data changes.

---

### Task 1: Make resident profile/settings access explicit and testable

**Files:**
- Modify: `app/src/main/java/za/org/rtc/community/feature/account/AccountScreen.kt`
- Modify: `app/src/main/java/za/org/rtc/community/feature/account/AccountHubScreen.kt`
- Modify: `app/src/main/java/za/org/rtc/community/feature/account/AccountSettingsMenu.kt`
- Modify: `app/src/main/java/za/org/rtc/community/ui/navigation/RtcCommunityNavGraph.kt`
- Test: `tools/tests/test_profile_settings_access.py`

**Behavior:** Profile identity card and Settings action expose explicit button semantics and navigate to the existing profile route. Notification settings navigate to the existing notification screen rather than using a no-op lambda. Empty preference actions show an explicit non-blocking message instead of appearing dead.

### Task 2: Add client idempotency to Community draft creation

**Files:**
- Create: `supabase/migrations/20260918030000_community_post_client_idempotency.sql`
- Modify: `app/src/main/java/za/org/rtc/community/data/RtcRepository.kt`
- Modify: `app/src/main/java/za/org/rtc/community/supabase/ProductionUxRepository.kt`
- Modify: `app/src/main/java/za/org/rtc/community/app/RtcResidentCoordinator.kt`
- Modify: `app/src/main/java/za/org/rtc/community/feature/community/PostComposer.kt`
- Test: `tools/tests/test_community_post_reliability.py`

**Behavior:** Generate one UUID when the composer opens, disable submission while working, pass the UUID to the draft RPC, and return the existing draft for a repeated client key owned by the same user. Finalization remains idempotent.

### Task 3: Make upload success terminal and media reads resilient

**Files:**
- Modify: `app/src/main/java/za/org/rtc/community/feature/community/CommunityFeedScreen.kt`
- Modify: `app/src/main/java/za/org/rtc/community/feature/community/CommunityPostCard.kt`
- Modify: `app/src/main/java/za/org/rtc/community/RtcCommunityApplication.kt`
- Test: `tools/tests/test_community_post_reliability.py`

**Behavior:** Success clears the draft before sheet dismissal, the sheet cannot reopen the unsaved dialog after success, and media uses bounded retry/fallback behavior with a retry action where the existing component supports it.

### Task 4: Suppress routine sync status jitter

**Files:**
- Modify: `app/src/main/java/za/org/rtc/community/ui/navigation/RtcCommunityApp.kt`
- Modify: `app/src/main/java/za/org/rtc/community/core/sync/SystemUpdateSyncEngine.kt`
- Test: `tools/tests/test_community_post_reliability.py`

**Behavior:** Background polling does not continuously render a warning/update banner. Explicit failures and user-triggered refreshes remain visible through the existing error/status channels.

### Task 5: Validate

- Run: `python3 tools/tests/test_community_post_reliability.py`
- Run: `python3 tools/tests/run_contract_tests.py`
- Run: `./gradlew test` if the available JDK/Android toolchain permits.
- Run: `git diff --check` and inspect changed files.
