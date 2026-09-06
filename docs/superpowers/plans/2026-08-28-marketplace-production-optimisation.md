# Marketplace Production Optimisation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make RTC Community Marketplace production-ready across discovery, bounded search, opening-hours truthfulness, owner resume/mutations/media, reviews, and privileged administration without changing shared security architecture or production Supabase.

**Architecture:** Preserve the existing `feature/marketplace` boundary and current navigation graph while moving reusable behavior into focused domain/data units. Add only backward-compatible Marketplace RPC/migration changes for paginated search and special-date hours, and keep Supabase as the source of truth for persisted drafts and authorization.

**Tech Stack:** Kotlin 2.x, Jetpack Compose Material 3, Android Lifecycle/ViewModel, Kotlin Coroutines/Flow, Hilt, SavedStateHandle, DataStore Preferences, Coil, Supabase Kotlin/PostgREST/Storage, PostgreSQL/PLpgSQL, JUnit4, kotlinx-coroutines-test.

**Spec:** `docs/superpowers/specs/2026-08-28-marketplace-production-optimisation-design.md`

## Global Constraints

- Branch: `optimize/marketplace-production` from `ff083d90496632796104f98ebeae65b9c3b97eb2`.
- Never commit directly to `main`.
- Never mutate RTC Community Production Supabase.
- `feature/marketplace-build` is donor/reference only; never merge it wholesale.
- Do not replace centralized `RouteAccessPolicy`/`ProtectedRoute` with local Boolean authorization.
- Preserve server-side owner/content-admin/moderator assertions as the authoritative trust boundary.
- Preserve intentionally RPC-only Marketplace table access; do not add table policies merely to clear advisor INFO findings.
- New SQL must be forward-only, Marketplace-scoped, backward compatible, and keep explicit hardened `search_path` semantics.
- New behavior follows test-first RED → GREEN → REFACTOR when executable tests are available.

---

### Task 1: Domain opening-hours model and evaluator

**Files:**
- Create: `app/src/main/java/za/org/rtc/community/feature/marketplace/domain/MarketplaceHours.kt`
- Modify: `app/src/main/java/za/org/rtc/community/feature/marketplace/domain/MarketplaceModels.kt`
- Test: `app/src/test/java/za/org/rtc/community/feature/marketplace/domain/MarketplaceHoursTest.kt`

**Interfaces:**
- Produces: `MarketplaceHoursInterval`, `MarketplaceHoursException`, `MarketplaceOpeningStatus`, `MarketplaceHoursEvaluator.evaluate(location, instant)`.
- Consumes: `MarketplaceLocation.timezone` plus new `hours` and `hourExceptions` lists on `MarketplaceLocation`.

- [ ] **Step 1: Write the failing domain tests**

```kotlin
package za.org.rtc.community.feature.marketplace.domain

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class MarketplaceHoursTest {
    private val evaluator = MarketplaceHoursEvaluator()

    @Test fun specialDateClosedOverridesWeeklyHours() {
        val location = MarketplaceLocation(
            id = "loc", label = "Main", locality = "Postmasburg", municipality = null,
            province = "Northern Cape", address = null, visibility = "AREA",
            latitude = null, longitude = null, timezone = "Africa/Johannesburg",
            accessibilityFeatures = emptyList(), parkingNote = null,
            hours = listOf(MarketplaceHoursInterval(5, 1, "OPEN", "08:00", "17:00")),
            hourExceptions = listOf(MarketplaceHoursException("2026-08-28", "CLOSED", emptyList(), null)),
        )
        assertEquals(MarketplaceOpeningStatus.Closed, evaluator.evaluate(location, Instant.parse("2026-08-28T10:00:00Z")))
    }

    @Test fun overnightIntervalCarriesIntoNextDay() {
        val location = MarketplaceLocation(
            id = "loc", label = "Main", locality = "Postmasburg", municipality = null,
            province = null, address = null, visibility = "AREA", latitude = null, longitude = null,
            timezone = "Africa/Johannesburg", accessibilityFeatures = emptyList(), parkingNote = null,
            hours = listOf(MarketplaceHoursInterval(5, 1, "OPEN", "20:00", "02:00")),
            hourExceptions = emptyList(),
        )
        assertEquals(MarketplaceOpeningStatus.OpenNow, evaluator.evaluate(location, Instant.parse("2026-08-28T23:30:00Z")))
    }
}
```

- [ ] **Step 2: Run the focused test and verify RED**

Run: `gradle --no-daemon testDebugUnitTest --tests '*MarketplaceHoursTest'`

Expected: compilation/test failure because the hours domain types/evaluator do not exist.

- [ ] **Step 3: Implement minimal domain types and evaluator**

```kotlin
sealed interface MarketplaceOpeningStatus {
    data object OpenNow : MarketplaceOpeningStatus
    data object Closed : MarketplaceOpeningStatus
    data object Open24Hours : MarketplaceOpeningStatus
    data object ByAppointment : MarketplaceOpeningStatus
    data object Unavailable : MarketplaceOpeningStatus
    data class OpensAt(val localTime: String) : MarketplaceOpeningStatus
    data class ClosesAt(val localTime: String) : MarketplaceOpeningStatus
}
```

Implement exception precedence, weekly state handling, and previous-day overnight carry using `ZoneId.of(location.timezone)` and `Instant.atZone(...)`. Never return open when the data is incomplete.

- [ ] **Step 4: Run the focused test and verify GREEN**

Run: `gradle --no-daemon testDebugUnitTest --tests '*MarketplaceHoursTest'`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/za/org/rtc/community/feature/marketplace/domain app/src/test/java/za/org/rtc/community/feature/marketplace/domain/MarketplaceHoursTest.kt
git commit -m "feat(marketplace): model truthful opening hours"
```

---

### Task 2: Additive detail-hours contract and JSON mapping

**Files:**
- Modify: `app/src/main/java/za/org/rtc/community/feature/marketplace/data/remote/SupabaseMarketplaceRepository.kt`
- Create: `supabase/migrations/20260828090000_marketplace_detail_hour_exceptions.sql`
- Test: `app/src/test/java/za/org/rtc/community/feature/marketplace/domain/MarketplaceHoursTest.kt`

**Interfaces:**
- Consumes: Task 1 `MarketplaceHoursInterval` and `MarketplaceHoursException`.
- Produces: populated `MarketplaceLocation.hours` and `.hourExceptions` from `marketplace_business_detail`.

- [ ] **Step 1: Add a failing mapper-facing test case**

Extend the hours/domain fixture expectations so a location with weekly hours and one date exception can be represented and evaluated without client-side fabrication.

- [ ] **Step 2: Verify RED**

Run the focused Marketplace test command; expect failure until mapping/contract support exists.

- [ ] **Step 3: Add the forward migration**

Create a `CREATE OR REPLACE FUNCTION public.marketplace_business_detail(...)` body based on the currently deployed non-production definition and add this key inside each location JSON object:

```sql
'hourExceptions', coalesce((
  select jsonb_agg(
    jsonb_build_object(
      'date', e.exception_date,
      'state', e.state,
      'intervals', e.intervals,
      'note', e.note
    ) order by e.exception_date
  )
  from public.marketplace_location_hour_exceptions e
  where e.location_id = l.id
), '[]'::jsonb)
```

Preserve the existing weekly `hours` array, public-address privacy behavior, `SECURITY DEFINER`, hardened `search_path`, and internal actor assertion behavior from the deployed function.

- [ ] **Step 4: Map both arrays in Android**

In `toBusinessDetail()`, parse location `hours` into `MarketplaceHoursInterval` and `hourExceptions` into `MarketplaceHoursException`. Parsing must tolerate absent additive fields as empty lists so older environments remain readable.

- [ ] **Step 5: Verify GREEN and commit**

Run the focused test, then commit with:

```bash
git commit -am "feat(marketplace): expose location hour exceptions"
```

---

### Task 3: Bounded paginated search contract and latest-request ViewModel

**Files:**
- Modify: `app/src/main/java/za/org/rtc/community/feature/marketplace/domain/MarketplaceModels.kt`
- Modify: `app/src/main/java/za/org/rtc/community/feature/marketplace/data/remote/SupabaseMarketplaceRepository.kt`
- Modify: `app/src/main/java/za/org/rtc/community/feature/marketplace/presentation/MarketplaceViewModels.kt`
- Modify: `app/src/main/java/za/org/rtc/community/feature/marketplace/presentation/MarketplaceScreens.kt`
- Create: `supabase/migrations/20260828091000_marketplace_search_pagination.sql`
- Test: `app/src/test/java/za/org/rtc/community/feature/marketplace/domain/MarketplaceSearchPageTest.kt`

**Interfaces:**
- Add: `data class MarketplaceSearchPage(val items: List<MarketplaceBusinessCard>, val nextOffset: Int?, val hasMore: Boolean)`.
- Change repository search contract to `suspend fun searchPage(filters: MarketplaceSearchFilters, origin: MarketplaceCoordinates?, offset: Int, limit: Int = 20): Result<MarketplaceSearchPage>` while retaining any legacy helper needed by existing call sites during migration.
- Add ViewModel criteria setters and `loadMore()`.

- [ ] **Step 1: Write failing page-envelope tests**

```kotlin
@Test fun pageWithExtraRowReportsHasMore() {
    val visible = (1..20).map { "id-$it" }
    val page = MarketplaceSearchPage.fromIdsForTest(visible + "id-21", offset = 0, pageSize = 20)
    assertEquals(20, page.items.size)
    assertEquals(20, page.nextOffset)
    assertTrue(page.hasMore)
}
```

If production-only construction makes this helper inappropriate, test an extracted pure `MarketplacePaging` helper instead; keep the helper package-internal and behavior-focused.

- [ ] **Step 2: Verify RED**

Run: `gradle --no-daemon testDebugUnitTest --tests '*MarketplaceSearchPageTest'`.

- [ ] **Step 3: Add `marketplace_search_businesses_page` migration**

The RPC accepts the same filters as the existing search plus:

```sql
p_offset integer default 0,
p_limit integer default 20
```

Bound `p_offset` to non-negative and `p_limit` to 1..50. Use the same published-business filters and ordering as the corrected non-production search function. Query `v_limit + 1`, return only the first `v_limit` cards, and return:

```sql
jsonb_build_object(
  'items', coalesce(v_items, '[]'::jsonb),
  'nextOffset', case when v_has_more then v_offset + v_limit else null end,
  'hasMore', v_has_more
)
```

Do not modify/remove the legacy search RPC.

- [ ] **Step 4: Implement repository page decoding**

Call `marketplace_search_businesses_page`, pass all filters, coordinate/radius values, `p_offset`, and bounded `p_limit`, then decode `items`, `nextOffset`, and `hasMore`.

- [ ] **Step 5: Implement latest-request search state**

Create a `MutableStateFlow<MarketplaceSearchFilters>` plus refresh/load-more state. Process criteria with `debounce(300)`, `distinctUntilChanged()`, and `mapLatest`/`flatMapLatest` semantics inside `viewModelScope`. Filter changes reset accumulated items and offset. `loadMore()` exits early when already loading or `hasMore == false`.

- [ ] **Step 6: Wire Search UI**

Move query/sort/verified/filter state ownership to the ViewModel. Add category, minimum-rating, locality and radius controls only where the corresponding data/origin exists. Trigger load-more near list end and keep retry scoped to the failed page.

- [ ] **Step 7: Verify and commit**

Run focused unit tests plus contract tests when available, then commit:

```bash
git commit -am "feat(marketplace): add bounded cancellable search paging"
```

---

### Task 4: Restore owner checkpoints and make mutations operation-idempotent

**Files:**
- Modify: `app/src/main/java/za/org/rtc/community/feature/marketplace/data/local/MarketplaceDraftCheckpointStore.kt`
- Create: `app/src/main/java/za/org/rtc/community/feature/marketplace/domain/MarketplaceMutation.kt`
- Modify: `app/src/main/java/za/org/rtc/community/feature/marketplace/domain/MarketplaceModels.kt`
- Modify: `app/src/main/java/za/org/rtc/community/feature/marketplace/data/remote/SupabaseMarketplaceRepository.kt`
- Modify: `app/src/main/java/za/org/rtc/community/feature/marketplace/presentation/MarketplaceViewModels.kt`
- Test: `app/src/test/java/za/org/rtc/community/feature/marketplace/domain/MarketplaceMutationTest.kt`

**Interfaces:**
- Produce: `MarketplaceMutationCommand(key: String, action: String, subjectId: String?)` and command-state helper that reuses `key` across retries of one command.
- Add checkpoint method: `suspend fun read(): MarketplaceDraftCheckpoint?` using `checkpoint.first()`.

- [ ] **Step 1: Write failing idempotency tests**

```kotlin
@Test fun retryReusesSameOperationKey() {
    val tracker = MarketplaceMutationTracker()
    val first = tracker.begin("submit", "business-1")
    val retry = tracker.retry(first)
    assertEquals(first.key, retry.key)
}

@Test fun newIntentGetsNewOperationKey() {
    val tracker = MarketplaceMutationTracker()
    val first = tracker.begin("submit", "business-1")
    tracker.complete(first)
    val second = tracker.begin("submit", "business-1")
    assertNotEquals(first.key, second.key)
}
```

- [ ] **Step 2: Verify RED**

Run the focused mutation test and confirm missing types fail.

- [ ] **Step 3: Implement mutation tracker and repository signatures**

Pass a command key into RPC methods with `p_idempotency_key` instead of creating `UUID.randomUUID()` inside each repository method. Continue using a fresh upload key for a genuinely new media upload begin command, but preserve it for a retry of that same item.

- [ ] **Step 4: Restore checkpoint in owner ViewModel**

On `loadEditor(businessId)`, read the checkpoint. Expose `currentStep: StateFlow<Int>` initialized from `SavedStateHandle` and set it to the stored checkpoint step when `checkpoint.businessId == businessId`. `checkpoint()` updates both DataStore and `currentStep`.

- [ ] **Step 5: Prevent duplicate in-flight owner/admin/review mutations**

Expose mutation state and disable/ignore an identical action while running. On a retry after failure, reuse the retained command key. On success, complete the command and allow a later intentional command to get a new key.

- [ ] **Step 6: Verify and commit**

Run mutation tests and commit:

```bash
git commit -am "fix(marketplace): restore drafts and stabilize mutation identity"
```

---

### Task 5: Media IO boundary, item state, authenticated rendering, and deletion

**Files:**
- Modify: `app/src/main/java/za/org/rtc/community/feature/marketplace/data/local/MarketplaceMediaPreparation.kt`
- Modify: `app/src/main/java/za/org/rtc/community/feature/marketplace/domain/MarketplaceModels.kt`
- Modify: `app/src/main/java/za/org/rtc/community/feature/marketplace/data/remote/SupabaseMarketplaceRepository.kt`
- Modify: `app/src/main/java/za/org/rtc/community/feature/marketplace/presentation/MarketplaceScreens.kt`

**Interfaces:**
- `MarketplaceMediaPreparation.prepareImage` remains synchronous internally but is invoked from `withContext(Dispatchers.IO)` in repository/data flow.
- Add `deleteDraftMedia(businessId, assetId, commandKey)` to owner repository.

- [ ] **Step 1: Establish the observable behavior in a focused test where pure code is available**

Test media action-state transitions or extracted path/cache policy rather than Android `BitmapFactory` itself in a JVM test. Ensure a failed item remains retryable and a successful item is terminal.

- [ ] **Step 2: Move preparation off Main**

Wrap `mediaPreparation.prepareImage(Uri.parse(sourceUri))` in `withContext(Dispatchers.IO)` and preserve existing temporary-file cleanup.

- [ ] **Step 3: Add draft-media deletion**

Call existing `marketplace_delete_draft_media` with operation key, then refresh editor state. Require confirmation in the UI.

- [ ] **Step 4: Render Marketplace media using Coil and authenticated Storage resolution**

Add a small repository resolver for private object paths using the configured Supabase Storage client and cache time-bounded resolved URLs in memory. Show logo/cover/gallery in Home/Detail/Owner preview where paths exist. Never display raw storage object paths as URLs.

- [ ] **Step 5: Expose per-item media state**

Represent preparing/uploading/finalizing/succeeded/failed states so the owner sees progress and can retry only failed items.

- [ ] **Step 6: Verify and commit**

Run the available unit/build gates and commit:

```bash
git commit -am "feat(marketplace): harden media upload and rendering"
```

---

### Task 6: Complete owner/review/admin surfaces and centralized route gating

**Files:**
- Modify: `app/src/main/java/za/org/rtc/community/feature/marketplace/presentation/MarketplaceScreens.kt`
- Modify: `app/src/main/java/za/org/rtc/community/feature/marketplace/presentation/MarketplaceViewModels.kt`
- Modify: `app/src/main/java/za/org/rtc/community/feature/marketplace/domain/MarketplaceModels.kt`
- Modify: `app/src/main/java/za/org/rtc/community/feature/marketplace/data/remote/SupabaseMarketplaceRepository.kt`
- Modify: `app/src/main/java/za/org/rtc/community/ui/navigation/RtcCommunityNavGraph.kt`
- Test: `app/src/test/java/za/org/rtc/community/navigation/MarketplaceRouteAccessPolicyTest.kt`

**Interfaces:**
- Reuse existing `RouteAccessPolicy` Marketplace route sets.
- Expose existing backend operations only through repository interfaces matching their current RPC contracts.

- [ ] **Step 1: Write failing route-policy integration test**

Verify Marketplace content routes are denied to resident/moderator roles and review moderation routes are denied to resident/content-editor roles, while authorized roles are allowed according to existing policy.

- [ ] **Step 2: Wrap admin Marketplace routes in `ProtectedRoute`**

Use:

```kotlin
ProtectedRoute(
    route = RtcRoute.ADMIN_MARKETPLACE,
    session = session,
    onDenied = { navController.returnToSafeWorkspace(session.role.isStaff) },
) {
    MarketplaceAdminRoute(onNavigate = { navController.navigateOverlay(it) })
}
```

Apply equivalent centralized gating to privileged Marketplace child routes as they are surfaced. Do not duplicate role checks inside screens.

- [ ] **Step 3: Complete owner steps**

Replace placeholder-only stages with editors backed by existing RPCs: category selection, location hours/exceptions, public contact preferences, richer offering fields, and truthful preview. Verification UI must expose only backend-supported evidence/status data.

- [ ] **Step 4: Complete review behavior**

Expose structured report reason/details, helpful voting, own-review deletion confirmation, and owner response only where the backend contract authorizes it.

- [ ] **Step 5: Complete admin publication behavior**

Consume `marketplace_admin_submission_detail` before approve/request-changes/reject. Surface suspend/reinstate when a business state supports it. Require explicit confirmation for publish/reject/suspend/reinstate.

- [ ] **Step 6: Verify route tests and commit**

```bash
git commit -am "feat(marketplace): complete privileged workflows"
```

---

### Task 7: Decompose presentation files and add security handoff

**Files:**
- Create: `app/src/main/java/za/org/rtc/community/feature/marketplace/presentation/MarketplaceComponents.kt`
- Create: `app/src/main/java/za/org/rtc/community/feature/marketplace/presentation/MarketplaceHomeScreen.kt`
- Create: `app/src/main/java/za/org/rtc/community/feature/marketplace/presentation/MarketplaceSearchScreen.kt`
- Create: `app/src/main/java/za/org/rtc/community/feature/marketplace/presentation/MarketplaceBusinessScreen.kt`
- Create: `app/src/main/java/za/org/rtc/community/feature/marketplace/presentation/MarketplaceOwnerScreen.kt`
- Create: `app/src/main/java/za/org/rtc/community/feature/marketplace/presentation/MarketplaceReviewScreen.kt`
- Create: `app/src/main/java/za/org/rtc/community/feature/marketplace/presentation/MarketplaceAdminScreen.kt`
- Modify/Delete after extraction: `app/src/main/java/za/org/rtc/community/feature/marketplace/presentation/MarketplaceScreens.kt`
- Create: `docs/MARKETPLACE_SECURITY_HANDOFF.md`

**Interfaces:**
- Preserve all public route composable names currently imported by `RtcCommunityNavGraph` so navigation call sites do not churn.

- [ ] **Step 1: Extract shared load/card/detail primitives**

Move `MarketplaceLoadContainer`, business card/metadata rendering, shared confirmation/dialog primitives, and common section components into `MarketplaceComponents.kt` without changing behavior.

- [ ] **Step 2: Extract each route family**

Move Home/Search/Business/Owner/Review/Admin composables into the listed files, preserving route function signatures.

- [ ] **Step 3: Run focused tests/build after each extraction**

Run `testDebugUnitTest` after each route-family move so package/import mistakes are isolated.

- [ ] **Step 4: Write security handoff**

Document Android-called RPCs, `SECURITY DEFINER` exposure, internal authorization assertions, private Storage assumptions, intentional RPC-only table access, and advisor items owned by the security workstream.

- [ ] **Step 5: Commit**

```bash
git commit -am "refactor(marketplace): split production surfaces by responsibility"
git add docs/MARKETPLACE_SECURITY_HANDOFF.md
git commit -m "docs: hand off marketplace security boundaries"
```

---

### Task 8: Final verification and review evidence

**Files:**
- Create: `MARKETPLACE_PRODUCTION_OPTIMISATION_EVIDENCE_2026-08-28.md`

**Interfaces:**
- Consumes all prior tasks.
- Produces review-ready verification evidence; does not merge the branch.

- [ ] **Step 1: Run source contract tests**

```bash
python3 tools/tests/run_contract_tests.py
```

Record exact pass/fail counts.

- [ ] **Step 2: Run unit tests**

```bash
gradle --no-daemon testDebugUnitTest
```

Record the exact result.

- [ ] **Step 3: Run lint and debug builds**

```bash
gradle --no-daemon lintDebug
gradle --no-daemon assembleDebug
gradle --no-daemon assembleDebugAndroidTest
```

Record exact results and artifact locations if produced.

- [ ] **Step 4: Inspect branch diff against main**

Verify no unrelated Community, global auth, production configuration, or secret-bearing files changed.

- [ ] **Step 5: Document backend status**

State explicitly that forward Marketplace migrations are committed for review/non-production application only and RTC Community Production was not mutated.

- [ ] **Step 6: Commit evidence**

```bash
git add MARKETPLACE_PRODUCTION_OPTIMISATION_EVIDENCE_2026-08-28.md
git commit -m "docs: record marketplace production verification"
```
