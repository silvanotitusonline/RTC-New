# CODER A REPORT — refactor/split-main-activity

## Final recovery and verification report — 2026-08-28

This report records the completed recovery and decomposition of the RTC Community Android `MainActivity.kt` monolith. The work was reconstructed from the supplied interrupted AI-coder archives, reconciled against live `main`, and then completed in verified increments on `refactor/split-main-activity`.

## 1. Baseline and recovery boundary

- Baseline `main`: `029cbf32b785d9fa8b805786f4f0cbcd27895304` (`chore: remove Google sign-in patch helper`).
- Restored baseline CI run **#94** passed source contracts, `testDebugUnitTest`, `lintDebug`, `assembleDebug`, and debug artifact upload.
- The supplied archive was not a safe continuation point: its reduced `MainActivity.kt` was incomplete, its `RtcCommunityNavGraph.kt` was only 79 lines and truncated immediately after state collection, and several referenced extracted feature files were absent.
- Archived files were therefore treated only as forensic/salvage material. Missing feature packages and the navigation graph were reconstructed from the live baseline source instead of trusting the truncated archive.
- Marketplace runtime/backend behavior was outside this structural refactor. No Supabase schema or data mutation was required or performed.

## 2. Original section map and final destinations

| Original responsibility | Final destination |
|---|---|
| Activity bootstrap, Supabase/deep-link intent handling | `MainActivity.kt` |
| Root application/session/chrome orchestration | `ui/navigation/RtcCommunityApp.kt` |
| Complete resident, Marketplace and staff NavHost | `ui/navigation/RtcCommunityNavGraph.kt` |
| Resident/staff navigation chrome | `ui/navigation/RtcNavigationChrome.kt` |
| Navigation metadata and route helpers | `ui/navigation/RtcNavigationMetadata.kt` |
| Protected destination guard | `ui/navigation/ProtectedRoute.kt` |
| Shared brand/content/pull-refresh components | `ui/components/` |
| Home dashboard | `feature/home/` |
| Community feed, detail, composer and media | `feature/community/` |
| Explore/directory/search/notice submission | `feature/explore/` |
| Support/help centre | `feature/support/` |
| Account/profile/public welcome/notifications | `feature/account/` |
| Resident and staff community alerts | `feature/alerts/` |
| Administration/access/moderation/operations/work/AI | `feature/administration/` |

The final `MainActivity.kt` is a bootstrap-only single Activity containing lifecycle setup, Supabase/deep-link handling, the non-production debug resident entry, theme setup, and delegation to `RtcCommunityApp`.

## 3. Completed commit sequence

1. `25515ec2` — `refactor(ui): recover verified refactor checkpoint (#A0)`
2. `8bfa8e1b` — `refactor(ui): restore pull-request verification gate (#A0b)`
3. `63b5e5d1` — `refactor(ui): restore contract test dependency in CI (#A0c)`
4. `034b1782` — `refactor(ui): recover shared leaf components (#A3)`
5. `35ec6d62` — `refactor(ui): record Step 3 recovery checkpoint (#A3b)`
6. `935f8fdf` — `refactor(ui): recover Home and Community feature packages (#A4)`
7. `00bf6f90` — `refactor(ui): reconstruct resident feature packages (#A5)`
8. `20168116` — `refactor(ui): recover Alerts and Administration feature packages (#A6)`
9. `1c9f0a8c` — `refactor(ui): reconstruct complete navigation graph (#A7)`
10. `1e192998` — `refactor(ui): fix navigation root scope import (#A7b)`
11. `aece3379` — `refactor(ui): restore Kotlin compiler headroom (#A7c)`
12. `6f715360` — `refactor(ui): restore debug packaging heap (#A7d)`
13. `6f80bf27` — `refactor(ui): switch to extracted navigation bootstrap (#A8)`
14. `2a340474` — `refactor(ui): retarget Google auth contract after extraction (#A8b)`
15. `46445d7b` — `refactor(ui): restore Compose delegate import after cutover (#A8c)`
16. `9d2d8352` — `refactor(ui): add refactor structure contracts (#A9)`
17. `8ac8792b` — `refactor(ui): add resident Compose smoke coverage (#A10)`
18. This report-only closeout — `refactor(ui): finalize refactor verification report (#A11)`

## 4. Structural contracts added

`tools/tests/test_refactor_structure.py` now makes the refactor budgets executable requirements:

- `MainActivity.kt` must remain **≤ 400 lines** and remain the only Activity declaration in the bootstrap source.
- Every extracted UI source file covered by the refactor must remain **≤ 600 lines**.
- Extracted UI files may not introduce raw numeric `.dp` or `.sp` dimensions; shared design tokens remain the dimension source.
- Resident primary navigation must remain exactly **Home / Community / Explore / Support**.
- Protected-route access remains centralized through the route access policy and app-root enforcement.

The final source-contract suite also verifies that the resident Compose smoke suite exists, covers all four primary surfaces, remains network-independent, and is compiled by CI.

## 5. Compose smoke coverage

Added `app/src/androidTest/java/za/org/rtc/community/feature/ResidentSurfaceSmokeTest.kt` with deterministic Compose smoke coverage for:

- Home
- Community
- Explore
- Support

CI now runs `assembleDebugAndroidTest`, proving the instrumentation source and the four resident Compose surfaces compile together. The instrumentation APK is included in the debug verification artifact alongside the normal debug APK, lint report and JVM test report.

## 6. Final verified results

Final implementation verification was performed by GitHub Actions run **#107** (`33145126996`) at A10 head `8ac8792bdd4e71413eeea3e38e5ac706777dc22f`.

- Source regression contracts: **92/92 passed**.
- `testDebugUnitTest`: **passed**.
- `lintDebug`: **passed**.
- `assembleDebug`: **passed**.
- `assembleDebugAndroidTest`: **passed**.
- Debug verification artifact upload: **passed**.
- Artifact: `rtc-community-debug-verification`, ID `9675579719`, size `39,458,110` bytes.
- Artifact includes the debug application APK, debug Android-test APK, lint HTML report, and JVM test report.
- `MainActivity.kt`: **93 lines**, against the required **≤400** budget.
- Extracted UI files: enforced by source contract at **≤600 lines each**.
- No new Android runtime third-party dependency was introduced.
- Single-Activity architecture is preserved.

The optional release-candidate APK/AAB step was skipped in PR CI because `GOOGLE_SERVICES_JSON_BASE64` is not configured for that run. The workflow intentionally treats that as a non-failure; debug application and instrumentation builds are fully compiled and verified. Protected-branch/runtime credential requirements remain unchanged.

## 7. Recovery deviations and repairs

The implementation required several recovery-specific repairs rather than blindly replaying the interrupted archive:

- Reconstructed missing Explore, Support, Account, Alerts and Administration code from current `main`.
- Rebuilt the complete navigation graph from current `main`; the archived 79-line graph was discarded as truncated WIP.
- Preserved centralized route protection and the exact four resident primary destinations.
- Adjusted Gradle compiler/package heap only as needed to compile the larger extracted source tree in CI.
- Retargeted source contracts that incorrectly assumed screen/auth code still lived inside `MainActivity.kt`; assertions were preserved, not weakened.
- Restored the Compose `getValue` operator import required by the final Activity cutover.
- Added structural budget contracts only after the extracted tree was complete and green.
- Added resident Compose smoke coverage without introducing backend/network dependencies.

## 8. Final invariants

- No intentional user-visible behavior change.
- No Supabase/backend mutation as part of this refactor.
- No new Android runtime third-party dependency.
- One Android Activity remains.
- `MainActivity.kt` is below the 400-line budget.
- Extracted UI files are contractually bounded to 600 lines.
- Resident primary navigation remains Home / Community / Explore / Support, with Account secondary.
- Protected destinations remain fail-closed through centralized route policy enforcement.
- Raw physical dimensions are prohibited in the extracted UI by source contract.
- Home, Community, Explore and Support Compose smoke tests are source-controlled and compiled in CI.

## 9. Remaining review notes

The refactor implementation is complete. Remaining items are review/merge concerns rather than unfinished decomposition work:

- Existing Kotlin/Compose deprecation warnings remain non-blocking and were not introduced as functional changes by this structural task.
- A production/release-candidate build still requires the repository's real Firebase Android configuration; PR verification intentionally does not synthesize that credentialed production artifact.
- Pull request #6 remains a draft for human review and merge decision.
