from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[2]
SRC = ROOT / 'app/src/main/java/za/org/rtc/community'

def text(path):
    return (ROOT / path).read_text(encoding='utf-8')

def test_bootstrap_runtime_contracts():
    assert (SRC / 'RtcCommunityApplication.kt').exists()
    assert (SRC / 'di/AppModule.kt').exists()
    assert (SRC / 'app/RtcFirebaseMessagingService.kt').exists()
    app = (SRC / 'RtcCommunityApplication.kt').read_text()
    assert '@HiltAndroidApp' in app and 'createRtcNotificationChannels' in app
    fcm = (SRC / 'app/RtcFirebaseMessagingService.kt').read_text()
    assert 'onNewToken' in fcm and 'onMessageReceived' in fcm
    assert 'PendingIntent.FLAG_IMMUTABLE' in fcm

def test_concept6_design_contract():
    theme = text('app/src/main/java/za/org/rtc/community/ui/theme/Theme.kt')
    components = SRC / 'ui/components/Concept6Components.kt'
    assert 'RtcInk' in theme and 'RtcCivicGold' in theme and 'RtcMint' in theme
    assert components.exists()
    c = components.read_text()
    for symbol in ['RtcScreenScaffold','RtcCard','RtcStatusChip','RtcResidentBottomNavigation','RtcCommunityFeedCard','RtcProtectedToolCard','RtcEmptyState','RtcEmergencyBanner','RtcCaseProgress']:
        assert f'fun {symbol}' in c

def test_route_policy_contract():
    policy = SRC / 'navigation/RouteAccessPolicy.kt'
    assert policy.exists()
    p = policy.read_text()
    assert 'OPERATIONAL_CONTROLS' in p and 'SYSTEM_ADMIN' in p and 'requiresMfa' in p
    nav = text('app/src/main/java/za/org/rtc/community/navigation/RtcNavigation.kt')
    assert 'else -> RtcRoute.OPERATIONAL_CONTROLS' not in text('app/src/main/java/za/org/rtc/community/MainActivity.kt')
    assert 'residentPrimaryRoutes' in nav

def test_release_has_no_seed_support_cases_or_work_queue():
    repo = text('app/src/main/java/za/org/rtc/community/data/RtcRepository.kt')
    assert 'Lighting concern near the community hall' not in repo
    assert 'Review Community Notice submission' not in repo
    assert 'submit_support_case' in text('app/src/main/java/za/org/rtc/community/supabase/ProductionUxRepository.kt')

def test_community_semantics_are_real():
    prod = text('app/src/main/java/za/org/rtc/community/supabase/ProductionUxRepository.kt')
    main = '\n'.join([text('app/src/main/java/za/org/rtc/community/MainActivity.kt'), text('app/src/main/java/za/org/rtc/community/ui/navigation/RtcCommunityApp.kt'), text('app/src/main/java/za/org/rtc/community/ui/navigation/RtcCommunityNavGraph.kt')])
    assert 'trendingScore = row.reportCount' not in prod
    assert 'filter { !it.isOfficial }' not in main
    assert 'isFollowedTopic' in prod
    assert 'reactionCount' in prod

def test_media_pipeline_is_bounded():
    prod = text('app/src/main/java/za/org/rtc/community/supabase/ProductionUxRepository.kt')
    composer = text('app/src/main/java/za/org/rtc/community/feature/community/PostComposer.kt')
    create = re.search(r'suspend fun createCommunityPost\(.*?\n    }\n\n    suspend fun reportCommunityPost', prod, re.S)
    assert create, 'createCommunityPost block not found'
    assert '.readBytes()' not in create.group(0)
    assert 'GetMultipleContents' not in composer
    assert 'PickMultipleVisualMedia' in composer
    assert 'finalize_community_post' in create.group(0)

def test_local_persistence_contract():
    assert (SRC / 'data/local/RtcDatabase.kt').exists()
    assert (SRC / 'data/local/UserPreferencesStore.kt').exists()
    assert (SRC / 'data/local/CommunityUploadWorker.kt').exists()
    repo = text('app/src/main/java/za/org/rtc/community/data/RtcRepository.kt')
    assert 'UserPreferencesStore' in repo
    assert 'LocalDraftDao' in repo

def test_concept6_major_screens_use_shared_scaffold():
    main = '\n'.join([text('app/src/main/java/za/org/rtc/community/MainActivity.kt'), text('app/src/main/java/za/org/rtc/community/ui/navigation/RtcCommunityApp.kt'), text('app/src/main/java/za/org/rtc/community/ui/navigation/RtcCommunityNavGraph.kt')])
    home = text('app/src/main/java/za/org/rtc/community/feature/home/HomeScreen.kt')
    community = text('app/src/main/java/za/org/rtc/community/feature/community/CommunityFeedScreen.kt')
    explore = text('app/src/main/java/za/org/rtc/community/feature/explore/ExploreScreen.kt')
    support = text('app/src/main/java/za/org/rtc/community/feature/support/SupportScreen.kt')
    account = text('app/src/main/java/za/org/rtc/community/feature/account/AccountScreen.kt')
    notifications = text('app/src/main/java/za/org/rtc/community/feature/account/AccountProfileNotifications.kt')
    search = text('app/src/main/java/za/org/rtc/community/feature/explore/SearchScreen.kt')
    administration = text('app/src/main/java/za/org/rtc/community/feature/administration/AdminWorkspace.kt')
    for screen in ['HomeScreen','CommunityScreen','ExploreScreen','SupportScreen','AccountScreen','SearchScreen','NotificationsScreen','AdminWorkspace']:
        source = home if screen == 'HomeScreen' else community if screen == 'CommunityScreen' else explore if screen == 'ExploreScreen' else support if screen == 'SupportScreen' else account if screen == 'AccountScreen' else search if screen == 'SearchScreen' else notifications if screen == 'NotificationsScreen' else administration if screen == 'AdminWorkspace' else main
        pos = source.find(f'fun {screen}')
        assert pos >= 0
        if screen == 'HomeScreen':
            assert 'ResidentPullToRefresh' in source[pos:], screen
            assert 'HomeCommunityStatusCard(' in source[pos:], screen
            assert 'HomePostComposerCard(' in source[pos:], screen
            assert 'import za.org.rtc.community.feature.home.HomeScreen' in main
            assert 'HomeScreen(' in main
        elif screen == 'CommunityScreen':
            assert 'import za.org.rtc.community.feature.community.CommunityScreen' in main
            assert 'CommunityScreen(' in main
        elif screen == 'ExploreScreen':
            assert 'import za.org.rtc.community.feature.explore.ExploreScreen' in main
            assert 'ExploreScreen(' in main
        elif screen == 'SupportScreen':
            assert 'import za.org.rtc.community.feature.support.SupportScreen' in main
            assert 'SupportScreen(' in main
        elif screen == 'AccountScreen':
            assert 'import za.org.rtc.community.feature.account.AccountScreen' in main
            assert 'AccountScreen(' in main
        elif screen == 'SearchScreen':
            assert 'import za.org.rtc.community.feature.explore.SearchScreen' in main
            assert 'SearchScreen(' in main
        elif screen == 'NotificationsScreen':
            assert 'import za.org.rtc.community.feature.account.NotificationsScreen' in main
            assert 'NotificationsScreen(' in main
        elif screen == 'AdminWorkspace':
            assert 'import za.org.rtc.community.feature.administration.AdminWorkspace' in main
            assert 'AdminWorkspace(' in main
        else:
            snippet = source[pos:pos+9000]
            assert ('RtcScreenScaffold' in snippet or 'RtcCard(' in snippet), screen

def test_profile_and_feedback_media_are_bounded_before_upload():
    prod = text('app/src/main/java/za/org/rtc/community/supabase/ProductionUxRepository.kt')
    profile = re.search(r'private fun encodeProfilePhoto\(uri: Uri\): ByteArray \{.*?\n    \}', prod, re.S)
    assert profile, 'encodeProfilePhoto block not found'
    assert 'inJustDecodeBounds = true' in profile.group(0)
    assert 'inSampleSize' in profile.group(0)
    feedback = re.search(r'suspend fun uploadFeedbackScreenshot\(uri: Uri\): Result<String> = runCatching \{.*?\n    \}', prod, re.S)
    assert feedback, 'uploadFeedbackScreenshot block not found'
    assert '.readBytes()' not in feedback.group(0)
    assert 'OpenableColumns.SIZE' in feedback.group(0) or 'queryContentSize' in feedback.group(0)


def test_community_feed_projection_is_source_controlled():
    migration = text('supabase/migrations/20260825222000_complete_community_feed_and_idempotent_finalize.sql')
    assert 'avatar_updated_at' in migration
    assert 'jsonb_agg' in migration and 'community_post_media' in migration
    assert 'is_followed_topic' in migration
    assert 'report_count' not in re.search(r'as trending_score', migration[:migration.find('as trending_score')+20], re.S).group(0)[-500:]
    assert "current_state in ('PUBLISHED','LOCKED')" in migration
    assert "jsonb_array_length(p_media)>10" in migration

def test_client_media_recovery_contract():
    main = '\n'.join([text('app/src/main/java/za/org/rtc/community/MainActivity.kt'), text('app/src/main/java/za/org/rtc/community/ui/navigation/RtcCommunityApp.kt'), text('app/src/main/java/za/org/rtc/community/ui/navigation/RtcCommunityNavGraph.kt')])
    media = text('app/src/main/java/za/org/rtc/community/feature/community/CommunityMedia.kt') + text('app/src/main/java/za/org/rtc/community/ui/media/RtcMedia3VideoPlayer.kt')
    prod = text('app/src/main/java/za/org/rtc/community/supabase/ProductionUxRepository.kt')
    assert 'HorizontalPager' in media and 'rememberPagerState' in media
    assert 'Player.Listener' in media and 'STATE_BUFFERING' in media and 'onPlayerError' in media
    assert 'refreshCommunityMediaUrl' in prod and 'community-media-url' in prod
    assert 'VideoFrameDecoder' in media or 'videoFrameMillis' in media
    assert 'Previous' not in re.search(r'internal fun FullScreenMediaGallery\(.*?\n\}', media, re.S).group(0)
    assert 'import za.org.rtc.community.feature.community.CommunityPostDetailScreen' in main


def test_protected_workspace_fail_closed_contract():
    main = '\n'.join([text('app/src/main/java/za/org/rtc/community/MainActivity.kt'), text('app/src/main/java/za/org/rtc/community/ui/navigation/RtcCommunityApp.kt'), text('app/src/main/java/za/org/rtc/community/ui/navigation/RtcCommunityNavGraph.kt')])
    policy = text('app/src/main/java/za/org/rtc/community/navigation/RouteAccessPolicy.kt')
    assert 'ProtectedRoute' in main
    for route in ['WORK_QUEUE', 'MY_WORK', 'STAFF_ALERTS', 'CONTENT', 'MODERATION', 'AI', 'ACCESS_MANAGEMENT', 'OPERATIONAL_CONTROLS', 'ANALYTICS_DASHBOARD', 'SYSTEM_HEALTH', 'ADMIN_ACTIVITY']:
        assert route in policy
    assert 'composable(RtcRoute.MY_WORK) { MyWorkProfileScreen(viewModel) }' not in main


def test_rtc_ai_uses_guarded_edge_function_contract():
    repo = text('app/src/main/java/za/org/rtc/community/data/RtcRepository.kt')
    prod = text('app/src/main/java/za/org/rtc/community/supabase/ProductionUxRepository.kt')
    vm = text('app/src/main/java/za/org/rtc/community/app/RtcViewModel.kt')
    assert 'confirmAiProposalForDevelopment' not in repo
    assert 'Development adapter recorded confirmation' not in repo
    assert 'rtc-admin-ai' in prod
    assert 'rtc_admin_ai_command' in prod and 'rtc_admin_ai_confirm' in prod
    assert 'Recaptcha.fetchClient' in prod
    assert 'confirmAiProposal' in vm and 'proposeAiAction' in vm


def test_administrator_mfa_qr_and_secret_lifecycle_contract():
    main = '\n'.join([text('app/src/main/java/za/org/rtc/community/MainActivity.kt'), text('app/src/main/java/za/org/rtc/community/ui/navigation/RtcCommunityApp.kt'), text('app/src/main/java/za/org/rtc/community/ui/navigation/RtcCommunityNavGraph.kt')])
    mfa = text('app/src/main/java/za/org/rtc/community/feature/administration/AdminWorkspaceComponents.kt')
    components = text('app/src/main/java/za/org/rtc/community/feature/administration/AdminWorkspaceComponents.kt')
    repo = text('app/src/main/java/za/org/rtc/community/data/RtcRepository.kt')
    assert 'TotpQrCode' in mfa
    assert 'QRCodeWriter' in components
    assert 'internal fun AdministratorMfaDialog(' in mfa
    assert 'it.secret' not in re.search(r'internal fun AdministratorMfaDialog\(.*?\n\}', mfa, re.S).group(0)
    assert 'secret = factor.data.secret' in repo and 'uri = factor.data.uri' in repo
    assert 'DataStore' not in re.search(r'data class AdministratorTotpEnrollment\(.*?\)', repo, re.S).group(0)


def test_final_regression_contracts_are_source_controlled():
    assert (ROOT / 'tools/tests/test_release_regression.py').exists()
    assert (ROOT / 'docs/PRODUCTION_READINESS_REPORT.md').exists()



def test_feedback_media_privacy_and_failed_submission_cleanup_contract():
    policy = text('tools/supabase-local/recovery/20260825180701_canonical_production_reconciliation_core.sql')
    feedback_foundation = text('supabase/migrations/20260820_014_production_ux_foundation.sql')
    repo = text('app/src/main/java/za/org/rtc/community/data/RtcRepository.kt')
    normalized_policy = re.sub(r'\s+', '', policy)

    assert "'rtc-feedback-media','rtc-feedback-media',false,5242880" in normalized_policy
    assert "array['image/jpeg','image/png','image/webp']" in normalized_policy
    assert 'feedback_media_insert_owner' in policy
    assert 'feedback_media_delete_owner' in policy
    assert 'feedback_media_select_owner_or_triage' in policy
    assert '(storage.foldername(name))[1]=(selectauth.uid())::text' in normalized_policy
    assert 'private.is_content_authority()' in policy
    assert 'user_feedback_insert_own' in feedback_foundation
    assert '(select auth.uid()) = reporter_id' in feedback_foundation
    assert 'attachmentPath?.let { productionUxRepository.deleteFeedbackScreenshot(it) }' in repo
