from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
MAIN_ACTIVITY = (ROOT / "app/src/main/java/za/org/rtc/community/MainActivity.kt").read_text()
APP_ROOT = (ROOT / "app/src/main/java/za/org/rtc/community/ui/navigation/RtcCommunityApp.kt").read_text()
NAV_GRAPH = (ROOT / "app/src/main/java/za/org/rtc/community/ui/navigation/RtcCommunityNavGraph.kt").read_text()
MAIN = "\n".join([MAIN_ACTIVITY, APP_ROOT, NAV_GRAPH])
PRODUCTION_REPOSITORY = (
    ROOT
    / "app/src/main/java/za/org/rtc/community/supabase/ProductionUxRepository.kt"
).read_text()
RTC_REPOSITORY = (
    ROOT / "app/src/main/java/za/org/rtc/community/data/RtcRepository.kt"
).read_text()
MEDIA_PREPARATION = (
    ROOT / "app/src/main/java/za/org/rtc/community/core/media/MediaPreparation.kt"
).read_text()
MEDIA_PREPARATION_COMPAT = (
    ROOT / "app/src/main/java/za/org/rtc/community/data/local/MediaPreparation.kt"
).read_text()
IMAGE_COMPRESSION = (
    ROOT / "app/src/main/java/za/org/rtc/community/core/media/ImageCompressionUtility.kt"
).read_text()
BASE_THEME = (ROOT / "app/src/main/res/values/themes.xml").read_text()
API_27_THEME = (ROOT / "app/src/main/res/values-v27/themes.xml").read_text()
COMMUNITY_FEED = (ROOT / "app/src/main/java/za/org/rtc/community/feature/community/CommunityFeedScreen.kt").read_text()
COMMUNITY_MEDIA = (ROOT / "app/src/main/java/za/org/rtc/community/feature/community/CommunityMedia.kt").read_text()
EXPLORE_SCREEN = (ROOT / "app/src/main/java/za/org/rtc/community/feature/explore/ExploreScreen.kt").read_text()
EXPLORE_DIRECTORY = (ROOT / "app/src/main/java/za/org/rtc/community/feature/explore/ExploreDirectoryScreen.kt").read_text()
NOTICE_SUBMISSION = (ROOT / "app/src/main/java/za/org/rtc/community/feature/explore/NoticeSubmissionSheet.kt").read_text()
SUPPORT_SCREENS = (ROOT / "app/src/main/java/za/org/rtc/community/feature/support/SupportScreen.kt").read_text()
ACCOUNT_PROFILE = (ROOT / "app/src/main/java/za/org/rtc/community/feature/account/AccountProfileNotifications.kt").read_text()
MY_WORK_PROFILE = (ROOT / "app/src/main/java/za/org/rtc/community/feature/administration/AdministrationWorkAi.kt").read_text()


def test_coil_2_video_frame_api_is_imported_from_real_packages():
    assert "import coil.decode.VideoFrameDecoder" in COMMUNITY_MEDIA
    assert "import coil.request.videoFrameMillis" in COMMUNITY_MEDIA
    assert "import coil.video." not in COMMUNITY_MEDIA


def test_community_report_dialog_is_a_typed_real_control():
    assert "internal fun CommunityReportDialog(" in COMMUNITY_FEED
    assert "onSubmit: (ModerationReason, String) -> Unit" in COMMUNITY_FEED
    assert "ModerationReason.entries.forEach" in COMMUNITY_FEED
    assert "import za.org.rtc.community.feature.community.CommunityScreen" in MAIN


def test_staged_media_uses_supabase_3_streaming_upload_data():
    assert "import io.github.jan.supabase.storage.UploadData" in PRODUCTION_REPOSITORY
    assert "import io.ktor.utils.io.jvm.javaio.toByteReadChannel" in PRODUCTION_REPOSITORY
    assert "UploadData(staged.inputStream().toByteReadChannel(), staged.length())" in PRODUCTION_REPOSITORY
    assert "UploadData(file.inputStream().toByteReadChannel(), file.length())" in PRODUCTION_REPOSITORY


def test_notice_submission_sheet_explicitly_opts_into_material_api():
    marker = (
        "@OptIn(ExperimentalMaterial3Api::class)\n"
        "@Composable\n"
        "internal fun NoticeSubmissionSheet("
    )
    assert marker in NOTICE_SUBMISSION
    assert "NoticeSubmissionSheet(" in EXPLORE_DIRECTORY


def test_api_27_navigation_bar_theme_attribute_is_version_qualified():
    attribute = '<item name="android:windowLightNavigationBar">false</item>'
    assert attribute not in BASE_THEME
    assert attribute in API_27_THEME


def test_media3_transformer_uses_androidx_recognized_opt_in():
    assert "import androidx.annotation.OptIn" in MEDIA_PREPARATION
    assert MEDIA_PREPARATION.count("@OptIn(markerClass = [UnstableApi::class])") == 3
    assert "typealias MediaPreparation = za.org.rtc.community.core.media.MediaPreparation" in MEDIA_PREPARATION_COMPAT


def test_support_case_composable_observes_session_reactively():
    assert "val session by viewModel.session.collectAsStateWithLifecycle()" in SUPPORT_SCREENS
    assert "item.authorId == session.id" in SUPPORT_SCREENS
    assert "item.authorId == viewModel.session.value.id" not in SUPPORT_SCREENS
    assert "import za.org.rtc.community.feature.support.SupportCaseDetailScreen" in MAIN


def test_profile_updates_are_server_confirmed_and_handle_is_read_only():
    assert "updateCommunityProfile" not in PRODUCTION_REPOSITORY
    assert "suspend fun ownPersistedProfile" in PRODUCTION_REPOSITORY
    assert "suspend fun saveOwnProfile" in PRODUCTION_REPOSITORY
    assert "The profile changes could not be confirmed." in PRODUCTION_REPOSITORY
    assert "persistedProfile = productionUxRepository.ownPersistedProfile().getOrNull()" in RTC_REPOSITORY
    assert "productionUxRepository.saveOwnProfile(cleanName, cleanBio, cleanInterests).getOrThrow()" in RTC_REPOSITORY
    assert "Derived from your verified email and cannot be changed here." in ACCOUNT_PROFILE
    assert "onSave = { name, handle, bio, interests" not in ACCOUNT_PROFILE
    assert "import za.org.rtc.community.feature.account.AccountScreen" in MAIN


def test_experience_and_notifications_are_server_confirmed_and_hydrated():
    assert "saveAccountPreferences" not in PRODUCTION_REPOSITORY
    for method in [
        "ownExperiencePreferences",
        "saveOwnExperiencePreferences",
        "ownSupportNotificationPreference",
        "saveOwnSupportNotificationPreference",
        "ownOrdinaryAlertPreference",
        "saveOrdinaryAlertPreference",
    ]:
        assert f"suspend fun {method}" in PRODUCTION_REPOSITORY
    assert "persistedExperience = productionUxRepository.ownExperiencePreferences().getOrNull()" in RTC_REPOSITORY
    assert "supportNotifications = productionUxRepository.ownSupportNotificationPreference().getOrNull() ?: true" in RTC_REPOSITORY
    assert "communityNotifications = productionUxRepository.ownOrdinaryAlertPreference().getOrNull() ?: true" in RTC_REPOSITORY
    assert "productionUxRepository.saveOwnExperiencePreferences(readingMode, themePreference).getOrThrow()" in RTC_REPOSITORY
    assert "productionUxRepository.saveOwnSupportNotificationPreference(enabled).getOrThrow()" in RTC_REPOSITORY
    assert "productionUxRepository.saveOrdinaryAlertPreference(enabled).getOrThrow()" in RTC_REPOSITORY


def test_community_upload_recovery_reuses_existing_owner_draft_object():
    assert "val bucket = supabase.storage.from(COMMUNITY_MEDIA_BUCKET)" in PRODUCTION_REPOSITORY
    assert "if (!bucket.exists(path))" in PRODUCTION_REPOSITORY
    assert "bucket.upload(" in PRODUCTION_REPOSITORY
    assert "upsert = false" in PRODUCTION_REPOSITORY
    assert "UploadData(file.inputStream().toByteReadChannel(), file.length())" in PRODUCTION_REPOSITORY


SUPPORT_CASE_MIGRATION = (
    ROOT
    / "tools/supabase-local/recovery/20260825124753_recover_hardened_support_case_persistence.sql"
).read_text()


def test_assigned_case_staff_workflow_uses_only_guarded_rpcs():
    assert 'rpc("list_assigned_support_cases")' in PRODUCTION_REPOSITORY
    assert '"update_assigned_support_case_state"' in PRODUCTION_REPOSITORY
    assert 'from("community_cases")' not in PRODUCTION_REPOSITORY
    assert 'from("case_messages")' not in PRODUCTION_REPOSITORY
    for state in ["IN_REVIEW", "IN_PROGRESS", "RESOLVED", "CLOSED"]:
        assert f'"{state}"' in PRODUCTION_REPOSITORY
    assert "cleanNote.length in 3..1000" in PRODUCTION_REPOSITORY
    assert "p_case_id" in PRODUCTION_REPOSITORY
    assert "p_state" in PRODUCTION_REPOSITORY
    assert "p_note" in PRODUCTION_REPOSITORY


def test_assigned_case_state_is_live_case_staff_only_and_never_optimistic():
    assert "val assignedSupportCases: StateFlow<List<AssignedSupportCase>>" in RTC_REPOSITORY
    assert "_session.value.role != UserRole.CASE_STAFF" in RTC_REPOSITORY
    assert "_assignedSupportCases.value = emptyList()" in RTC_REPOSITORY
    assert "_session.value.role == UserRole.CASE_STAFF" in RTC_REPOSITORY
    assert "refreshAssignedSupportCases().getOrThrow()" in RTC_REPOSITORY
    assert "Do not mutate local case state optimistically" in RTC_REPOSITORY
    assert "session.role == UserRole.CASE_STAFF && session.authority == SessionAuthority.SUPABASE_AUTH" in MY_WORK_PROFILE
    assert "Resident identity, attachments, message history, and assignment controls are not shown here." in MY_WORK_PROFILE
    assert "import za.org.rtc.community.feature.administration.MyWorkProfileScreen" in MAIN
    assert "MyWorkProfileScreen(viewModel = viewModel" in NAV_GRAPH or "MyWorkProfileScreen(\n                            viewModel = viewModel," in NAV_GRAPH


def test_reconstructed_support_case_migration_closes_direct_access_and_grants_only_rpcs():
    assert "create type public.case_state as enum" in SUPPORT_CASE_MIGRATION
    assert "community_cases_no_direct_client_access" in SUPPORT_CASE_MIGRATION
    assert "case_messages_no_direct_client_access" in SUPPORT_CASE_MIGRATION
    assert "using (false)" in SUPPORT_CASE_MIGRATION
    assert "with check (false)" in SUPPORT_CASE_MIGRATION
    assert "create or replace function private.can_access_case" in SUPPORT_CASE_MIGRATION
    assert "private.has_verified_system_admin()" in SUPPORT_CASE_MIGRATION
    assert "grant execute on function public.list_assigned_support_cases() to authenticated;" in SUPPORT_CASE_MIGRATION
    assert "grant execute on function public.update_assigned_support_case_state(uuid, public.case_state, text) to authenticated;" in SUPPORT_CASE_MIGRATION
    assert "revoke all on function private.can_access_case(uuid) from public, anon, authenticated;" in SUPPORT_CASE_MIGRATION
    assert "from anon" not in SUPPORT_CASE_MIGRATION


def test_comment_avatar_uses_profile_revision_for_cache_busting():
    comment_view_migration = (
        ROOT / "supabase/migrations/20260825240000_reconcile_comment_avatar_revision.sql"
    ).read_text()
    assert '@SerialName("avatar_updated_at") val avatarUpdatedAt: String? = null' in PRODUCTION_REPOSITORY
    assert 'authorAvatarUrl = row.avatarPath?.let { signedAvatarUrl(it, row.avatarUpdatedAt) }' in PRODUCTION_REPOSITORY
    assert 'cp.updated_at as avatar_updated_at' in comment_view_migration
    assert 'with (security_invoker = true)' in comment_view_migration
    assert 'grant select' not in comment_view_migration
    assert 'create policy' not in comment_view_migration


def test_community_upload_recovery_is_scoped_to_the_authenticated_owner():
    database = (
        ROOT / "app/src/main/java/za/org/rtc/community/data/local/RtcDatabase.kt"
    ).read_text()
    worker = (
        ROOT / "app/src/main/java/za/org/rtc/community/data/local/CommunityUploadWorker.kt"
    ).read_text()
    app_module = (
        ROOT / "app/src/main/java/za/org/rtc/community/di/AppModule.kt"
    ).read_text()

    assert '@ColumnInfo(name = "owner_user_id") val ownerUserId: String? = null' in database
    assert "LocalDraftEntity::class" in database and "UploadOutboxEntity::class" in database
    # Schema advanced beyond the original v3/v6 recovery surface; require a current version and forward migrations.
    assert any(f"version = {version}" in database for version in (11, 10, 9, 8, 7, 6))
    assert "RTC_DATABASE_MIGRATION_1_2" in database
    assert "RTC_DATABASE_MIGRATION_2_3" in database
    assert "ALTER TABLE community_upload_outbox ADD COLUMN owner_user_id TEXT" in database
    assert "suspend fun pendingForOwner" in database
    assert "suspend fun forDraftForOwner" in database
    assert "suspend fun deleteDraftForOwner" in database
    assert "RTC_DATABASE_MIGRATION_1_2" in app_module and "RTC_DATABASE_MIGRATION_2_3" in app_module
    assert "currentAuthenticatedUserIdOrNull() ?: return Result.success()" in worker
    assert "pendingForOwner(ownerUserId)" in worker
    assert "ownerUserId = authorId" in PRODUCTION_REPOSITORY
    assert "forDraftForOwner(draftId, ownerUserId)" in PRODUCTION_REPOSITORY
    assert "deleteDraftForOwner(draftId, ownerUserId)" in PRODUCTION_REPOSITORY
    assert "No resumable media belongs to the signed-in account." in PRODUCTION_REPOSITORY
    assert "resumeCommunityUpload(draftId, allowEmptyMedia = mediaUris.isEmpty())" in PRODUCTION_REPOSITORY
    assert "resumeCommunityUpload(draftId: String, allowEmptyMedia: Boolean = false)" in PRODUCTION_REPOSITORY
    assert "rows.isNotEmpty() || allowEmptyMedia" in PRODUCTION_REPOSITORY
    assert "observePendingCountForOwner(activeSession.id)" in RTC_REPOSITORY
    assert "enqueueUploadRecovery()" in RTC_REPOSITORY


def test_media_preparation_uses_managed_androidx_exifinterface():
    app_gradle = (ROOT / "app/build.gradle.kts").read_text()
    versions = (ROOT / "gradle/libs.versions.toml").read_text()

    # Orientation correction lives in ImageCompressionUtility; MediaPreparation re-exports the type.
    assert "import androidx.exifinterface.media.ExifInterface" in IMAGE_COMPRESSION or "import androidx.exifinterface.media.ExifInterface" in MEDIA_PREPARATION
    assert "import android.media.ExifInterface" not in MEDIA_PREPARATION
    assert "import android.media.ExifInterface" not in IMAGE_COMPRESSION
    assert "ExifInterface(descriptor.fileDescriptor)" in IMAGE_COMPRESSION or "ExifInterface(descriptor.fileDescriptor)" in MEDIA_PREPARATION
    assert "normalizedForExif" in IMAGE_COMPRESSION or "normalizedForExif" in MEDIA_PREPARATION
    assert "typealias MediaPreparation = za.org.rtc.community.core.media.MediaPreparation" in MEDIA_PREPARATION_COMPAT
    assert "implementation(libs.androidx.exifinterface)" in app_gradle
    assert 'androidx-exifinterface = { module = "androidx.exifinterface:exifinterface"' in versions


def test_explore_uses_approved_two_card_information_architecture():
    assert 'Text("Community Notices"' in EXPLORE_SCREEN
    assert 'Text("Projects and Opportunities"' in EXPLORE_SCREEN
    assert "private fun ExploreActionRow(" in EXPLORE_SCREEN
    assert 'onOpenDirectory("notices")' in EXPLORE_SCREEN
    assert 'onOpenDirectory("projects")' in EXPLORE_SCREEN
    assert 'onOpenDirectory("opportunities")' in EXPLORE_SCREEN
    assert 'notices.count { it.status == NoticeStatus.PUBLISHED }' in EXPLORE_SCREEN
    assert 'count = "${projects.size} available"' in EXPLORE_SCREEN
    assert 'count = "${opportunities.size} open"' in EXPLORE_SCREEN
    assert "Explore overview" not in EXPLORE_SCREEN
    assert "ExploreCategoryTile" not in EXPLORE_SCREEN
    assert 'onOpenDirectory("centres")' not in EXPLORE_SCREEN
    assert 'onOpenDirectory("services")' not in EXPLORE_SCREEN
    assert "import za.org.rtc.community.feature.explore.ExploreScreen" in MAIN


ADMIN_DASHBOARD = (
    ROOT / "app/src/main/java/za/org/rtc/community/feature/administration/AdminDashboardViewModel.kt"
).read_text()
ADMIN_SUMMARY = (
    ROOT / "app/src/main/java/za/org/rtc/community/feature/administration/AdminWorkspaceComponents.kt"
).read_text()
ADMIN_DASHBOARD_MIGRATION = (
    ROOT / "supabase/migrations/20260917220000_production_admin_dashboard_summary.sql"
).read_text()
ACCOUNT_DELETION_FUNCTION = (
    ROOT / "supabase/functions/process-account-deletions/index.ts"
).read_text()
ACCOUNT_DELETION_MIGRATION = (
    ROOT / "supabase/migrations/20260917220500_account_deletion_fk_cascade.sql"
).read_text()


def test_administration_dashboard_uses_production_rpc_and_real_frontend_surfaces():
    assert 'SUMMARY_RPC = "admin_get_moderation_dashboard_summary_v1"' in ADMIN_DASHBOARD
    assert 'supabase.postgrest.rpc(SUMMARY_RPC)' in ADMIN_DASHBOARD
    assert 'decodeList<AdminDashboardSummary>()' in ADMIN_DASHBOARD
    assert 'from(TABLE_REPORTS)' not in ADMIN_DASHBOARD
    assert 'delay(' not in ADMIN_DASHBOARD
    assert 'label = "Notices"' in ADMIN_SUMMARY
    assert 'label = "Events"' in ADMIN_SUMMARY
    assert 'label = "Work queue"' in ADMIN_SUMMARY
    assert 'private.ops_assert_staff()' in ADMIN_DASHBOARD_MIGRATION
    assert 'revoke all on function public.admin_get_moderation_dashboard_summary_v1() from public, anon;' in ADMIN_DASHBOARD_MIGRATION


def test_account_deletion_is_bounded_idempotent_and_deletes_auth_user():
    assert ".select('id, requester_id')" in ACCOUNT_DELETION_FUNCTION
    assert ".limit(MAX_BATCH)" in ACCOUNT_DELETION_FUNCTION
    assert ".eq('state', 'submitted')" in ACCOUNT_DELETION_FUNCTION
    assert ".in('state', ['processing', 'submitted'])" in ACCOUNT_DELETION_FUNCTION
    assert "supabase.auth.admin.deleteUser(request.requester_id)" in ACCOUNT_DELETION_FUNCTION
    assert "user_id" not in ACCOUNT_DELETION_FUNCTION
    assert "on delete cascade" in ACCOUNT_DELETION_MIGRATION


MARKETPLACE_CARDS = (
    ROOT / "app/src/main/java/za/org/rtc/community/feature/marketplace/presentation/MarketplaceBusinessCards.kt"
).read_text()


def test_media_and_ui_final_readiness_boundaries_are_explicit():
    assert 'else -> error("Unsupported media type: $mime")' in MEDIA_PREPARATION
    assert "while (pending.length > 0)" in ACCOUNT_DELETION_FUNCTION
    assert "object.id === null || object.metadata === null" in ACCOUNT_DELETION_FUNCTION
    assert "IconButton(onClick = onToggleSave, modifier = Modifier.size(48.dp))" in MARKETPLACE_CARDS


ADMIN_REALTIME_MIGRATION = (
    ROOT / "supabase/migrations/20260918010000_admin_dashboard_realtime_invalidation.sql"
).read_text()


def test_admin_dashboard_realtime_is_staff_scoped_payload_free_and_forward_only():
    assert "admin_dashboard_invalidations" in ADMIN_REALTIME_MIGRATION
    assert "private.is_any_staff()" in ADMIN_REALTIME_MIGRATION
    assert "revoke all on public.admin_dashboard_invalidations from public, anon;" in ADMIN_REALTIME_MIGRATION
    assert "alter publication supabase_realtime add table public.admin_dashboard_invalidations" in ADMIN_REALTIME_MIGRATION
    assert "emit_admin_dashboard_invalidation" in ADMIN_REALTIME_MIGRATION
    for table in ["civic_reports", "official_notices", "community_events", "operational_work_items"]:
        assert f"on public.{table}" in ADMIN_REALTIME_MIGRATION


def test_admin_dashboard_realtime_rehydrates_authoritative_rpc_and_cleans_up():
    assert 'INVALIDATION_TABLE = "admin_dashboard_invalidations"' in ADMIN_DASHBOARD
    assert "postgresChangeFlow<PostgresAction>" in ADMIN_DASHBOARD
    assert "debounce(INVALIDATION_DEBOUNCE_MILLIS)" in ADMIN_DASHBOARD
    assert "fetchPendingCountsInternal()" in ADMIN_DASHBOARD
    assert "channel.unsubscribe()" in ADMIN_DASHBOARD
    assert "override fun onCleared()" in ADMIN_DASHBOARD
    assert "AdminRealtimeStatus.DISCONNECTED" in ADMIN_DASHBOARD
    assert "Live updates" in ADMIN_SUMMARY
    assert "Refresh required" in ADMIN_SUMMARY
    assert "lastUpdatedLabel()" in ADMIN_SUMMARY
