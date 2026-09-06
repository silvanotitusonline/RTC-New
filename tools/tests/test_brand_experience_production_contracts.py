from pathlib import Path
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
SRC = ROOT / "app/src/main/java/za/org/rtc/community"
MANIFEST = ROOT / "app/src/main/AndroidManifest.xml"
ANDROID_NS = "{http://schemas.android.com/apk/res/android}"


def read(relative: str) -> str:
    path = ROOT / relative
    assert path.exists(), f"Required branding production file is missing: {relative}"
    return path.read_text(encoding="utf-8")


def test_branding_route_is_centralized_and_mfa_protected():
    routes = read("app/src/main/java/za/org/rtc/community/navigation/RtcNavigation.kt")
    policy = read("app/src/main/java/za/org/rtc/community/navigation/RouteAccessPolicy.kt")
    app = read("app/src/main/java/za/org/rtc/community/ui/navigation/RtcCommunityApp.kt")
    assert 'const val ADMIN_BRANDING = "admin/branding"' in routes
    assert "RtcRoute.ADMIN_BRANDING" in policy
    assert "RtcRoute.ADMIN_BRANDING" in app


def test_branding_editor_lives_inside_administration_feature_without_second_activity():
    screen = ROOT / "app/src/main/java/za/org/rtc/community/feature/administration/branding/BrandExperienceScreen.kt"
    assert screen.exists(), "BrandExperienceScreen must live under feature/administration/branding"
    activities = []
    for path in SRC.rglob("*.kt"):
        content = path.read_text(encoding="utf-8")
        if re.search(r"class\s+\w+\s*:\s*ComponentActivity", content):
            activities.append(path.relative_to(SRC).as_posix())
        assert "class BrandExperienceActivity" not in content
    assert activities == ["MainActivity.kt"], f"Single-Activity architecture violated: {activities}"


def test_main_activity_remains_thin_and_uses_configured_root():
    main = read("app/src/main/java/za/org/rtc/community/MainActivity.kt")
    assert len(main.splitlines()) <= 400
    assert "RtcConfiguredAppRoot(rtcViewModel)" in main
    assert "BrandExperience" not in main


def test_ui_configuration_schema_v2_and_runtime_boundary_exist():
    model = read("app/src/main/java/za/org/rtc/community/core/UiConfiguration.kt")
    validator = ROOT / "app/src/main/java/za/org/rtc/community/core/UiConfigurationValidation.kt"
    runtime = ROOT / "app/src/main/java/za/org/rtc/community/ui/config/UiConfigurationRuntime.kt"
    assert "val schemaVersion: Int = 2" in model
    assert "LegacyUiConfigurationV1" in model
    assert validator.exists()
    assert runtime.exists()


def test_brand_schema_v2_migrations_match_deployed_nonproduction_history():
    migration_directory = ROOT / "supabase/migrations"
    expected = {
        "20260827223815_broaden_ui_configuration_v2.sql": [
            "create table if not exists public.ui_configuration_assets",
            "create or replace function public.ui_configuration_effective_global()",
            "private.access_assert_system_admin()",
            "grant execute on function public.ui_configuration_effective_global() to anon,authenticated",
        ],
        "20260827231359_harden_ui_configuration_asset_cleanup.sql": [
            "create policy rtc_ui_assets_delete",
            "coalesce(auth.jwt() ->> 'aal', 'aal1') = 'aal2'",
            "private.has_role('SYSTEM_ADMIN'::public.app_role)",
        ],
        "20260827231722_resume_ui_configuration_drafts.sql": [
            "create function public.ui_configuration_admin_history",
            "configuration jsonb",
            "private.ui_configuration_upgrade_to_v2(v.configuration)",
        ],
    }
    for filename, required_fragments in expected.items():
        migration = read(f"supabase/migrations/{filename}")
        for fragment in required_fragments:
            assert fragment in migration, f"{filename} is missing {fragment}"

    stale_versions = {
        "20260827222000_broaden_ui_configuration_v2.sql",
        "20260827223500_harden_ui_configuration_asset_cleanup.sql",
        "20260827224000_resume_ui_configuration_drafts.sql",
    }
    assert not stale_versions.intersection(path.name for path in migration_directory.glob("*.sql"))


def test_brand_storage_policies_keep_rpc_only_tables_private():
    migrations = sorted(
        (ROOT / "supabase/migrations").glob("*_repair_ui_configuration_storage_policy_boundary.sql")
    )
    assert len(migrations) == 1, "The Brand Storage policy repair must have one canonical migration"
    migration = migrations[0].read_text(encoding="utf-8")
    for helper in [
        "private.ui_configuration_can_insert_asset_object",
        "private.ui_configuration_can_select_asset_object",
        "private.ui_configuration_can_delete_asset_object",
    ]:
        assert f"create or replace function {helper}" in migration
        assert f"grant execute on function {helper}(text) to authenticated" in migration
    assert "security definer" in migration
    assert "set search_path = auth, public, pg_temp" in migration
    assert "with check (private.ui_configuration_can_insert_asset_object(name))" in migration
    assert "using (private.ui_configuration_can_select_asset_object(name))" in migration
    assert "using (private.ui_configuration_can_delete_asset_object(name))" in migration
    assert "create policy feedback_media_select_owner_or_triage" in migration
    assert "private.has_any_role(array['CONTENT_EDITOR', 'SYSTEM_ADMIN']::public.app_role[])" in migration
    assert "private.is_content_authority()" not in migration
    assert "grant select on public.ui_configuration_" not in migration


def test_brand_storage_policies_are_scoped_to_rtc_ui_assets_bucket():
    migrations = sorted(
        (ROOT / "supabase/migrations").glob("*_scope_ui_configuration_storage_policies_to_bucket.sql")
    )
    assert len(migrations) == 1, "Brand Storage bucket scope needs one forward-only migration"
    migration = migrations[0].read_text(encoding="utf-8")
    for policy in ("rtc_ui_assets_insert", "rtc_ui_assets_select", "rtc_ui_assets_delete"):
        block = migration.split(f"create policy {policy}", 1)[1].split(";", 1)[0]
        assert "bucket_id = 'rtc-ui-assets'" in block, policy
    assert "grant select on public.ui_configuration_" not in migration


def test_brand_editor_state_tracks_snapshot_resume_and_safe_preview_actions():
    state = read("app/src/main/java/za/org/rtc/community/feature/administration/branding/BrandExperienceState.kt")
    assert "val savedDraftConfiguration: GlobalUiConfiguration? = null" in state
    assert "data class DraftSaved(val id: String, val configuration: GlobalUiConfiguration)" in state
    assert "data class ResumeDraft(val id: String, val configuration: GlobalUiConfiguration)" in state
    assert "data class PreviewRejected(val message: String)" in state
    assert "data class Published(val versionId: String, val refreshPending: Boolean)" in state


def test_brand_publish_and_revert_preserve_committed_outcome_when_refresh_fails():
    repository = read("app/src/main/java/za/org/rtc/community/data/ui_config/UiConfigurationRepository.kt")
    assert "data class UiConfigurationCommit" in repository
    assert "Result<UiConfigurationCommit>" in repository
    assert "refreshEffectiveConfiguration().getOrThrow()" not in repository
    assert "effectiveConfigurationRefreshed = refreshEffectiveConfiguration().isSuccess" in repository


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


def test_publish_completion_keeps_its_snapshot_and_blocks_history_mutations():
    view_model = read("app/src/main/java/za/org/rtc/community/feature/administration/branding/BrandExperienceViewModel.kt")
    sections = read("app/src/main/java/za/org/rtc/community/feature/administration/branding/BrandExperienceSections.kt")
    state = read("app/src/main/java/za/org/rtc/community/feature/administration/branding/BrandExperienceViewModel.kt")
    publish = view_model.split("    fun publish()", 1)[1].split("    fun restore()", 1)[0] if "    fun restore()" in view_model else view_model.split("    fun publish()", 1)[1].split("    fun restore(versionId", 1)[0]
    assert "val snapshot = state.editor.savedDraftConfiguration ?: return" in publish
    assert "} else {\n                        snapshot" in publish
    assert "val mutationInFlight: Boolean = false" in state
    assert "private fun beginMutation(): Boolean" in view_model
    assert "if (state.mutationInFlight) return" in view_model
    assert "if (state.mutationInFlight) return@update state" in view_model
    assert "enabled = !state.mutationInFlight" in sections


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


def test_image_picker_handles_content_provider_failures_without_crashing_compose():
    screen = read("app/src/main/java/za/org/rtc/community/feature/administration/branding/BrandExperienceScreen.kt")
    assert "catch (error: Throwable)" in screen
    assert "CancellationException" in screen
    assert "reportImageSelectionFailure" in screen


def test_draft_mutations_do_not_overlap_an_image_upload():
    view_model = read("app/src/main/java/za/org/rtc/community/feature/administration/branding/BrandExperienceViewModel.kt")
    assert "!imageUploadWorking && !mutationInFlight" in view_model


def test_editor_controls_are_visibly_disabled_during_protected_mutations():
    sections = read("app/src/main/java/za/org/rtc/community/feature/administration/branding/BrandExperienceSections.kt")
    assert "enabled: Boolean = true" in sections
    assert "enabled = enabled" in sections
    assert sections.count("enabled = !state.mutationInFlight") >= 6


def test_launcher_switch_does_not_disable_existing_alias_when_target_enable_fails():
    runtime = read("app/src/main/java/za/org/rtc/community/ui/config/UiConfigurationRuntime.kt")
    assert "if (!setAliasState(manager, selected" in runtime
    assert "return" in runtime.split("if (!setAliasState(manager, selected", 1)[1].split("aliases.values", 1)[0]
    assert "private fun setAliasState" in runtime
    assert "): Boolean" in runtime


def test_published_welcome_configuration_drives_public_welcome_surface():
    welcome = read("app/src/main/java/za/org/rtc/community/feature/account/PublicWelcomeScreen.kt")
    assert "val welcome = LocalRtcUiConfiguration.current.welcome" in welcome
    assert "welcome.headline" in welcome
    assert "welcome.supportingText" in welcome
    assert "welcome.primaryActionLabel" in welcome
    assert "welcome.secondaryActionLabel" in welcome
    assert "LaunchTreatment.IMMERSIVE" in welcome
    assert "LaunchTreatment.MINIMAL" in welcome


def test_home_asset_runtime_renews_expiring_signed_urls():
    runtime = read("app/src/main/java/za/org/rtc/community/ui/config/UiConfigurationRuntime.kt")
    repository = read("app/src/main/java/za/org/rtc/community/data/ui_config/UiConfigurationRepository.kt")
    assert "configuration.collectLatest" in runtime
    assert "ASSET_REFRESH_INTERVAL" in runtime
    assert "ASSET_RETRY_INTERVAL" in runtime
    assert "refreshAssetUrl(assetId)" in runtime
    assert "createSignedUrl(path, 10.minutes)" in repository


def test_launcher_is_a_finite_prepackaged_alias_set():
    root = ET.fromstring(MANIFEST.read_text(encoding="utf-8"))
    app = root.find("application")
    assert app is not None
    main = next(
        node for node in app.findall("activity")
        if node.attrib.get(ANDROID_NS + "name") == ".MainActivity"
    )
    main_actions = [
        action.attrib.get(ANDROID_NS + "name")
        for intent in main.findall("intent-filter")
        for action in intent.findall("action")
    ]
    assert "android.intent.action.MAIN" not in main_actions

    aliases = {
        alias.attrib.get(ANDROID_NS + "name"): alias
        for alias in app.findall("activity-alias")
    }
    expected = {".LauncherDefault", ".LauncherGold", ".LauncherEmerald", ".LauncherMonochrome"}
    assert set(aliases) == expected
    assert all(alias.attrib.get(ANDROID_NS + "targetActivity") == ".MainActivity" for alias in aliases.values())
    enabled = [name for name, alias in aliases.items() if alias.attrib.get(ANDROID_NS + "enabled") == "true"]
    assert enabled == [".LauncherDefault"], enabled


def test_branding_feature_does_not_own_marketplace_or_community_implementation_files():
    branding = ROOT / "app/src/main/java/za/org/rtc/community/feature/administration/branding"
    if not branding.exists():
        raise AssertionError("Branding feature package has not been created")
    forbidden = [
        path.relative_to(ROOT).as_posix()
        for path in branding.rglob("*.kt")
        if "Marketplace" in path.name or "CommunityFeed" in path.name or "CommunityPost" in path.name
    ]
    assert not forbidden, forbidden
