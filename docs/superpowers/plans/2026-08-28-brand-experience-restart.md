# Brand & Experience Restart Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make PR #17’s Brand & Experience integration safe to review by closing the Storage scope, draft lifecycle, state-concurrency, preview, image-intake, and launcher-switching defects.

**Architecture:** Keep global configuration as a bounded client model and keep data access server-authorized through existing RPCs. Add a forward-only Non-Production Storage policy migration; model draft identity as a saved configuration snapshot; treat server mutation completion separately from local effective-configuration refresh; and move raw image I/O off the Compose callback thread.

**Tech Stack:** Kotlin, Jetpack Compose, Hilt, kotlinx.serialization, Supabase PostgREST/Storage, PostgreSQL RLS, Python source contracts, JUnit, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-08-28-brand-experience-restart-design.md`

## Global Constraints

- Work from the current PR #17 head and deliver only through `integration/brand-experience-v2`; never write to `main` directly.
- Preserve Marketplace, Community, the single `MainActivity`, existing route/MFA controls, and bounded runtime configuration.
- Production project `pbzzfzfgwzwdstvnwzqu` is read-only; apply any DDL only to Non-Production project `eqwstpdjoineycrkhpht`.
- Use a new canonical forward migration; do not modify the semantic effect of an already-applied migration.
- Do not add direct `SELECT` grants on `ui_configuration_versions`, `ui_configuration_events`, or `ui_configuration_assets`.
- Add a focused failing test before every production behavior change and record its red result.
- Keep all privileged user-facing errors safe; never surface raw SQL, storage, RPC, or server errors in Compose.

---

### Task 1: Scope Brand Storage object policies to their bucket

**Files:**

- Create: `supabase/migrations/<generated>_scope_ui_configuration_storage_policies_to_bucket.sql`
- Modify: `tools/tests/test_brand_experience_production_contracts.py`
- Test: `tools/tests/test_brand_experience_production_contracts.py`

**Interfaces:**

- Consumes: `private.ui_configuration_can_insert_asset_object(text)`, `private.ui_configuration_can_select_asset_object(text)`, and `private.ui_configuration_can_delete_asset_object(text)`.
- Produces: `storage.objects` policies that authorize Brand paths only when `bucket_id = 'rtc-ui-assets'` and the corresponding helper returns true.

- [ ] **Step 1: Write the failing source contract**

```python
def test_brand_storage_policies_are_scoped_to_rtc_ui_assets_bucket():
    migrations = sorted((ROOT / "supabase/migrations").glob("*_scope_ui_configuration_storage_policies_to_bucket.sql"))
    assert len(migrations) == 1
    migration = migrations[0].read_text(encoding="utf-8")
    for policy in ("rtc_ui_assets_insert", "rtc_ui_assets_select", "rtc_ui_assets_delete"):
        block = migration.split(f"create policy {policy}", 1)[1].split(";", 1)[0]
        assert "bucket_id = 'rtc-ui-assets'" in block
    assert "grant select on public.ui_configuration_" not in migration
```

- [ ] **Step 2: Run the focused test and verify it fails**

Run: `python3 -m pytest tools/tests/test_brand_experience_production_contracts.py -q`

Expected: failure because the generated forward migration is absent.

- [ ] **Step 3: Generate and write the forward-only migration**

Run `supabase migration new scope_ui_configuration_storage_policies_to_bucket`, retain the generated timestamped filename, and replace the three policies with:

```sql
create policy rtc_ui_assets_insert on storage.objects
for insert to authenticated
with check (
  bucket_id = 'rtc-ui-assets'
  and private.ui_configuration_can_insert_asset_object(name)
);

create policy rtc_ui_assets_select on storage.objects
for select to authenticated
using (
  bucket_id = 'rtc-ui-assets'
  and private.ui_configuration_can_select_asset_object(name)
);

create policy rtc_ui_assets_delete on storage.objects
for delete to authenticated
using (
  bucket_id = 'rtc-ui-assets'
  and private.ui_configuration_can_delete_asset_object(name)
);
```

The migration must `drop policy if exists` each named policy before recreating it and must not alter function grants or table privileges.

- [ ] **Step 4: Run the focused test and verify it passes**

Run: `python3 -m pytest tools/tests/test_brand_experience_production_contracts.py -q`

Expected: PASS.

- [ ] **Step 5: Apply and verify in Non-Production only**

Apply the exact source SQL using Supabase `apply_migration` to `eqwstpdjoineycrkhpht`. With an authenticated AAL2 rollback-only session, verify `EXPLAIN` can plan a permitted `rtc-ui-assets` object path and cannot authorize the same path under an unrelated bucket. Confirm no direct authenticated table `SELECT` grant exists.

- [ ] **Step 6: Commit**

```bash
git add supabase/migrations tools/tests/test_brand_experience_production_contracts.py
git commit -m "fix(supabase): scope Brand Storage policies to their bucket"
```

### Task 2: Make reducer state snapshot-aware and preview-safe

**Files:**

- Modify: `app/src/main/java/za/org/rtc/community/feature/administration/branding/BrandExperienceState.kt`
- Modify: `app/src/test/java/za/org/rtc/community/feature/administration/branding/BrandExperienceStateTest.kt`

**Interfaces:**

- Consumes: `GlobalUiConfiguration` equality and `BrandExperienceEditorAction`.
- Produces: `BrandExperienceEditorState.savedDraftConfiguration: GlobalUiConfiguration?`; `DraftSaved(id, configuration)` only binds an unchanged matching snapshot; `ResumeDraft(id, configuration)` installs a decoded draft; `PreviewRejected(message)` preserves the last safe preview; and `Published(versionId, refreshPending)` communicates local-refresh state.

- [ ] **Step 1: Write failing reducer tests**

```kotlin
@Test
fun staleDraftCompletionDoesNotAttachToNewerWorkingConfiguration() {
    val beforeSave = BrandExperienceEditorState.initial(GlobalUiConfiguration.default())
    val saving = reduceBrandExperienceState(beforeSave, BrandExperienceEditorAction.SaveDraftStarted)
    val edited = reduceBrandExperienceState(saving, BrandExperienceEditorAction.SetPrimarySeed("#112233"))
    val completed = reduceBrandExperienceState(edited, BrandExperienceEditorAction.DraftSaved("draft-1", beforeSave.working))
    assertNull(completed.draftId)
    assertEquals("#112233", completed.working.appearance.primarySeed)
}

@Test
fun resumedDraftIsImmediatelyPublishableFromItsSavedSnapshot() {
    val draft = GlobalUiConfiguration.default()
    val resumed = reduceBrandExperienceState(
        BrandExperienceEditorState.initial(GlobalUiConfiguration.default()),
        BrandExperienceEditorAction.ResumeDraft("draft-1", draft),
    )
    assertEquals("draft-1", resumed.draftId)
    assertEquals(draft, resumed.savedDraftConfiguration)
    assertEquals(BrandExperiencePhase.DRAFT_SAVED, resumed.phase)
}

@Test
fun rejectedPreviewPreservesTheLastSafePreview() {
    val initial = BrandExperienceEditorState.initial(GlobalUiConfiguration.default())
    val rejected = reduceBrandExperienceState(initial, BrandExperienceEditorAction.PreviewRejected("Correct colours first."))
    assertEquals(initial.preview, rejected.preview)
    assertEquals(BrandExperiencePhase.EDITING, rejected.phase)
}
```

- [ ] **Step 2: Run the focused JVM test and verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests '*BrandExperienceStateTest'`

Expected: compilation failure because the new state members and actions do not exist.

- [ ] **Step 3: Write minimal reducer implementation**

Add `savedDraftConfiguration` to `BrandExperienceEditorState`. `DraftSaved` accepts the snapshot and sets `draftId` only when `state.working == action.configuration`; otherwise retain the edited state and set a safe save-again message. Every mutating edit clears both `draftId` and `savedDraftConfiguration`. `ResumeDraft` sets baseline, working, preview, saved snapshot, and draft ID to the decoded draft. `PreviewRejected` leaves `preview` unchanged. `Published` clears the saved draft and distinguishes a pending refresh in its message.

- [ ] **Step 4: Run the focused JVM test and verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests '*BrandExperienceStateTest'`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/za/org/rtc/community/feature/administration/branding/BrandExperienceState.kt app/src/test/java/za/org/rtc/community/feature/administration/branding/BrandExperienceStateTest.kt
git commit -m "fix(branding): preserve draft snapshots and safe previews"
```

### Task 3: Separate committed server mutations from local refresh

**Files:**

- Modify: `app/src/main/java/za/org/rtc/community/data/ui_config/UiConfigurationRepository.kt`
- Modify: `app/src/main/java/za/org/rtc/community/feature/administration/branding/BrandExperienceViewModel.kt`
- Modify: `tools/tests/test_brand_experience_production_contracts.py`
- Test: `tools/tests/test_brand_experience_production_contracts.py`

**Interfaces:**

- Produces: `UiConfigurationCommit(versionId: String, effectiveConfigurationRefreshed: Boolean)` returned by both `publishDraft` and `revert`.
- Consumes: existing `refreshEffectiveConfiguration(): Result<GlobalUiConfiguration>`.

- [ ] **Step 1: Write the failing source contract**

```python
def test_brand_publish_and_revert_preserve_committed_outcome_when_refresh_fails():
    repository = read("app/src/main/java/za/org/rtc/community/data/ui_config/UiConfigurationRepository.kt")
    assert "data class UiConfigurationCommit" in repository
    assert "Result<UiConfigurationCommit>" in repository
    assert "refreshEffectiveConfiguration().getOrThrow()" not in repository
    assert "effectiveConfigurationRefreshed = refreshEffectiveConfiguration().isSuccess" in repository
```

- [ ] **Step 2: Run the source contract and verify it fails**

Run: `python3 -m pytest tools/tests/test_brand_experience_production_contracts.py -q`

Expected: failure because repository mutation methods return `Result<String>` and throw after a refresh failure.

- [ ] **Step 3: Implement committed outcome semantics**

Add:

```kotlin
data class UiConfigurationCommit(
    val versionId: String,
    val effectiveConfigurationRefreshed: Boolean,
)
```

After each successful RPC, set `effectiveConfigurationRefreshed = refreshEffectiveConfiguration().isSuccess` and return the commit. Update ViewModel publish and restore handlers to treat the commit as success; only read the cached effective configuration when refresh succeeded, otherwise keep the known working configuration and display the refresh-pending message through the reducer.

- [ ] **Step 4: Run source contract and targeted JVM tests**

Run: `python3 -m pytest tools/tests/test_brand_experience_production_contracts.py -q && ./gradlew :app:testDebugUnitTest --tests '*BrandExperienceStateTest'`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/za/org/rtc/community/data/ui_config/UiConfigurationRepository.kt app/src/main/java/za/org/rtc/community/feature/administration/branding/BrandExperienceViewModel.kt tools/tests/test_brand_experience_production_contracts.py
git commit -m "fix(branding): report committed configuration mutations truthfully"
```

### Task 4: Resume drafts and prevent editor-operation races

**Files:**

- Modify: `app/src/main/java/za/org/rtc/community/feature/administration/branding/BrandExperienceViewModel.kt`
- Modify: `app/src/main/java/za/org/rtc/community/feature/administration/branding/BrandExperienceSections.kt`
- Modify: `tools/tests/test_brand_experience_production_contracts.py`
- Test: `app/src/test/java/za/org/rtc/community/feature/administration/branding/BrandExperienceStateTest.kt`

**Interfaces:**

- Consumes: `UiConfigurationVersionSummary.configuration`, strict `GlobalUiConfiguration.decodeOrNull`, and Task 2 snapshot state.
- Produces: `resumeDraft(version: UiConfigurationVersionSummary)`, DRAFT-only Resume UI, and `canPublish` requiring `working == savedDraftConfiguration`.

- [ ] **Step 1: Write failing contracts**

```python
def test_history_resumes_only_valid_drafts_and_restore_excludes_them():
    sections = read("app/src/main/java/za/org/rtc/community/feature/administration/branding/BrandExperienceSections.kt")
    view_model = read("app/src/main/java/za/org/rtc/community/feature/administration/branding/BrandExperienceViewModel.kt")
    assert 'version.state == "DRAFT"' in sections
    assert "viewModel.resumeDraft(version)" in sections
    assert 'setOf("PUBLISHED", "SUPERSEDED")' in sections
    assert "GlobalUiConfiguration.decodeOrNull(version.configuration.toString())" in view_model

def test_publish_requires_the_exact_saved_draft_snapshot():
    view_model = read("app/src/main/java/za/org/rtc/community/feature/administration/branding/BrandExperienceViewModel.kt")
    assert "editor.savedDraftConfiguration == configuration" in view_model
    assert "val snapshot = state.configuration" in view_model
    assert "BrandExperienceEditorAction.DraftSaved(id, snapshot)" in view_model
```

- [ ] **Step 2: Run source contract and verify it fails**

Run: `python3 -m pytest tools/tests/test_brand_experience_production_contracts.py -q`

Expected: failure because all historical rows expose Restore and Save Draft reads mutable state after launch.

- [ ] **Step 3: Implement strict resume and operation snapshots**

`resumeDraft` returns without state change when the row is not `DRAFT` or strict decode rejects its configuration. For a valid draft, dispatch `ResumeDraft(versionId, decoded)`. In `saveDraft`, capture `snapshot` and `reason` before `SaveDraftStarted`; call `createDraft(snapshot, reason)` and dispatch `DraftSaved(id, snapshot)`. In `publish`, capture the matching draft ID/configuration/reason/confirmation before `PublishStarted`; prevent `edit` from changing configuration while the phase is `PUBLISHING`.

In `BrandHistoryPanel`, render Resume only for `DRAFT`; render Restore only for `PUBLISHED` and `SUPERSEDED`. Keep the confirmation gate only on Restore.

- [ ] **Step 4: Run source and JVM verification**

Run: `python3 -m pytest tools/tests/test_brand_experience_production_contracts.py -q && ./gradlew :app:testDebugUnitTest --tests '*BrandExperienceStateTest'`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/za/org/rtc/community/feature/administration/branding/BrandExperienceViewModel.kt app/src/main/java/za/org/rtc/community/feature/administration/branding/BrandExperienceSections.kt tools/tests/test_brand_experience_production_contracts.py app/src/test/java/za/org/rtc/community/feature/administration/branding/BrandExperienceStateTest.kt
git commit -m "fix(branding): resume drafts without stale editor handles"
```

### Task 5: Validate preview and bound image ingestion before upload

**Files:**

- Modify: `app/src/main/java/za/org/rtc/community/feature/administration/branding/BrandExperienceScreen.kt`
- Modify: `app/src/main/java/za/org/rtc/community/feature/administration/branding/BrandExperienceViewModel.kt`
- Modify: `tools/tests/test_brand_experience_production_contracts.py`
- Test: `tools/tests/test_brand_experience_production_contracts.py`

**Interfaces:**

- Produces: `readUiImageBytesCapped(input: InputStream, maxBytes: Int): ByteArray?`, executed on `Dispatchers.IO`, and `reportImageSelectionFailure(message: String)`.
- Consumes: `UiConfigurationRepository.MAX_UI_IMAGE_BYTES`, `UiConfigurationValidator`, and `BrandPaletteEngine.hasPublishableContrast`.

- [ ] **Step 1: Write the failing source contracts**

```python
def test_preview_is_gated_before_theme_composition_receives_invalid_colours():
    view_model = read("app/src/main/java/za/org/rtc/community/feature/administration/branding/BrandExperienceViewModel.kt")
    assert "if (!state.validatorPasses || !state.contrastPasses)" in view_model
    assert "BrandExperienceEditorAction.PreviewRejected" in view_model

def test_image_picker_reads_a_capped_stream_off_the_compose_thread():
    screen = read("app/src/main/java/za/org/rtc/community/feature/administration/branding/BrandExperienceScreen.kt")
    assert "withContext(Dispatchers.IO)" in screen
    assert "readUiImageBytesCapped" in screen
    assert "UiConfigurationRepository.MAX_UI_IMAGE_BYTES" in screen
    assert ".readBytes()" not in screen
```

- [ ] **Step 2: Run source contract and verify it fails**

Run: `python3 -m pytest tools/tests/test_brand_experience_production_contracts.py -q`

Expected: failure because preview is unconditional and the picker calls `InputStream.readBytes()` on the callback thread.

- [ ] **Step 3: Implement validation and capped asynchronous reading**

In `preview`, inspect the current `BrandExperienceUiState`; dispatch `PreviewRejected("Correct blocking validation or contrast errors before previewing.")` when either gate fails, otherwise dispatch `Preview`.

Use `rememberCoroutineScope().launch` in the picker callback. Inside `withContext(Dispatchers.IO)`, require an allowed MIME type, read at most `MAX_UI_IMAGE_BYTES + 1` through an 8 KiB buffer, return `null` on overflow, and run bounds-only `BitmapFactory` decoding. On the main dispatcher, call `uploadHomeImage` only when bytes and positive dimensions exist; otherwise call `reportImageSelectionFailure` with a safe message.

- [ ] **Step 4: Run source contract and Android compilation**

Run: `python3 -m pytest tools/tests/test_brand_experience_production_contracts.py -q && ./gradlew :app:compileDebugKotlin`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/za/org/rtc/community/feature/administration/branding/BrandExperienceScreen.kt app/src/main/java/za/org/rtc/community/feature/administration/branding/BrandExperienceViewModel.kt tools/tests/test_brand_experience_production_contracts.py
git commit -m "fix(branding): gate preview and bound image ingestion"
```

### Task 6: Preserve the active launcher alias on enable failure

**Files:**

- Modify: `app/src/main/java/za/org/rtc/community/ui/config/UiConfigurationRuntime.kt`
- Modify: `tools/tests/test_brand_experience_production_contracts.py`
- Test: `tools/tests/test_brand_experience_production_contracts.py`

**Interfaces:**

- Produces: `setAliasState(...): Boolean`; `LauncherIconManager.apply(icon)` only disables non-selected aliases when enabling the target succeeded or was already enabled.

- [ ] **Step 1: Write the failing source contract**

```python
def test_launcher_switch_does_not_disable_existing_alias_when_target_enable_fails():
    runtime = read("app/src/main/java/za/org/rtc/community/ui/config/UiConfigurationRuntime.kt")
    assert "if (!setAliasState(manager, selected" in runtime
    assert "return" in runtime.split("if (!setAliasState(manager, selected", 1)[1].split("aliases.values", 1)[0]
    assert "private fun setAliasState" in runtime
    assert "): Boolean" in runtime
```

- [ ] **Step 2: Run source contract and verify it fails**

Run: `python3 -m pytest tools/tests/test_brand_experience_production_contracts.py -q`

Expected: failure because failures are swallowed and non-selected aliases are disabled regardless.

- [ ] **Step 3: Implement the fail-closed switch**

Change `setAliasState` to return `true` when the desired state already exists or the package-manager call succeeds, and `false` when it fails. In `apply`, return immediately if enabling `selected` fails. Only then disable the remaining aliases. Do not change the finite alias map or manifest declarations.

- [ ] **Step 4: Run source contract and targeted compilation**

Run: `python3 -m pytest tools/tests/test_brand_experience_production_contracts.py -q && ./gradlew :app:compileDebugKotlin`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/za/org/rtc/community/ui/config/UiConfigurationRuntime.kt tools/tests/test_brand_experience_production_contracts.py
git commit -m "fix(branding): keep launcher alias on enable failure"
```

### Task 7: Perform integration verification, review, and handoff

**Files:**

- Modify: `docs/BRAND_EXPERIENCE_INTEGRATION_FINAL_REPORT.md`
- Modify: `docs/superpowers/specs/2026-08-28-brand-experience-restart-design.md`
- Modify: `docs/superpowers/plans/2026-08-28-brand-experience-restart.md`

**Interfaces:**

- Consumes: all tasks above and the exact remote PR head.
- Produces: an updated evidence report, current PR #17 description/status, and a production readiness decision.

- [ ] **Step 1: Run all source contracts**

Run: `python3 tools/tests/run_contract_tests.py`

Expected: every contract passes with the updated count.

- [ ] **Step 2: Run Android and authorization gates**

Run:

```bash
deno test supabase/functions/_shared/auth.test.ts
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
```

Expected: all commands exit zero. Treat AndroidTest APK assembly as compilation evidence only, not device execution.

- [ ] **Step 3: Run Non-Production security verification**

Use the Supabase project `eqwstpdjoineycrkhpht` to confirm migration history includes the new version, the three policies contain the `rtc-ui-assets` predicate, cross-bucket authorization fails under the same AAL2 session, direct table SELECT remains denied, and advisors are reviewed. Do not query or mutate Production.

- [ ] **Step 4: Request independent code review**

Provide the reviewer with `main..integration/brand-experience-v2`, refreshed evidence, the requirements in the spec, and explicit focus on RLS policy composition, snapshot races, and Compose I/O. Resolve every important or critical finding with a test-first follow-up before readying the PR.

- [ ] **Step 5: Update report and commit**

Record exact commands, result counts, workflow run IDs, artifact IDs/digests when available, Non-Production evidence, unresolved external release gates, and the explicit NO-GO/GO decision. Do not claim device, signing, protected-main, or Production readiness without fresh evidence.

```bash
git add docs/BRAND_EXPERIENCE_INTEGRATION_FINAL_REPORT.md docs/superpowers/specs/2026-08-28-brand-experience-restart-design.md docs/superpowers/plans/2026-08-28-brand-experience-restart.md
git commit -m "docs(branding): record restart verification evidence"
```

- [ ] **Step 6: Publish to the existing PR and verify exact-head CI**

Update only `integration/brand-experience-v2` through the GitHub integration. Wait for a fresh `Android Production Verification` success whose head SHA exactly matches the final PR head. Update PR #17 with the report and reviewer disposition; retain draft status until all evidence is present.
