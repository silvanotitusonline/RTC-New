from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

def read(path: str) -> str:
    return (ROOT / path).read_text()

explore = read("app/src/main/java/za/org/rtc/community/feature/explore/ExploreScreen.kt")
routes = read("app/src/main/java/za/org/rtc/community/navigation/RtcNavigation.kt")
bindings = read("app/src/main/java/za/org/rtc/community/ui/navigation/ResidentModernisationBindings.kt")
admin_catalog = read("app/src/main/java/za/org/rtc/community/feature/administration/AdminWorkspaceDestinationCatalog.kt")
domain = read("app/src/main/java/za/org/rtc/community/feature/dailypost/domain/DailyPostModels.kt")
repository = read("app/src/main/java/za/org/rtc/community/feature/dailypost/data/SupabaseDailyPostRepository.kt")
resident_ui = read("app/src/main/java/za/org/rtc/community/feature/dailypost/presentation/DailyPostScreen.kt")
view_model = read("app/src/main/java/za/org/rtc/community/feature/dailypost/presentation/DailyPostViewModel.kt")
global_host = read("app/src/main/java/za/org/rtc/community/feature/dailypost/presentation/DailyPostGlobalHost.kt")
studio_ui = read("app/src/main/java/za/org/rtc/community/feature/dailypost/presentation/AdminDailyPostStudioScreen.kt")
main_activity = read("app/src/main/java/za/org/rtc/community/MainActivity.kt")
fcm_service = read("app/src/main/java/za/org/rtc/community/app/RtcFirebaseMessagingService.kt")
home_screen = read("app/src/main/java/za/org/rtc/community/feature/home/HomeScreen.kt")
security = read("supabase/migrations/20260912011000_daily_post_security_and_workflows.sql")
storage = read("supabase/migrations/20260912010500_daily_post_storage.sql")
storage_security = read("supabase/migrations/20260912011500_daily_post_storage_security.sql")
scheduler_activation = read("supabase/migrations/20260912012000_daily_post_scheduler_activation_and_comment_delete.sql")
language_fn = read("supabase/functions/daily-post-language/index.ts")
scheduler_fn = read("supabase/functions/daily-post-scheduler/index.ts")
supabase_config = read("supabase/config.toml")
onboarding = read("app/src/main/java/za/org/rtc/community/feature/onboarding/InteractiveOnboardingTutorial.kt")
preferences = read("app/src/main/java/za/org/rtc/community/data/local/UserPreferencesStore.kt")

# Explore product architecture.
assert 'Text("The Daily Post"' in explore
assert 'Text("Community Updates"' in explore
assert 'DailyPostScreen(' in explore
assert 'CommunityUpdatesScreen(' in explore
assert 'Text("Events Calendar"' not in explore
assert 'MonthlyEventsCalendar' not in explore
assert 'Text("Community Notices"' in explore
assert 'Text("Projects and Opportunities"' in explore
assert 'InteractiveMunicipalCanvasMap(' in explore

# Home uses the same live Daily Post feed and replaces the former lower-half utility stack.
assert 'DailyPostViewModel' in home_screen
assert 'hiltViewModel' in home_screen
assert 'DailyPostSnapshotSection(' in home_screen
assert '.take(2)' in home_screen
assert 'dailyPostViewModel.refresh()' in home_screen
assert 'The Daily Post' in home_screen
assert 'Read full story' in home_screen
assert 'View all Daily Post' in home_screen
assert 'MainActivity.ACTION_OPEN_DAILY_POST' in home_screen
assert 'MainActivity.EXTRA_DAILY_POST_ID' in home_screen
for removed_home_surface in (
    'QuickAccessSection(',
    'ContinueDraftCard(',
    'PendingSyncIndicator(',
    'SupportCaseCard(',
    'NoticeCard(',
    'Text("Help & FAQs")',
):
    assert removed_home_surface not in home_screen, removed_home_surface

# Calendar is no longer a navigable application feature.
assert 'const val EVENTS =' not in routes
assert 'const val ADMIN_EVENTS =' not in routes
assert 'CommunityEventsScreen' not in bindings
assert 'AdminEventsScreen' not in bindings
assert 'event_moderation' not in admin_catalog
for removed in (
    "app/src/main/java/za/org/rtc/community/feature/events/presentation/AdminEventsScreen.kt",
    "app/src/main/java/za/org/rtc/community/feature/events/presentation/AdminEventsViewModel.kt",
    "app/src/main/java/za/org/rtc/community/feature/events/presentation/CommunityEventsScreen.kt",
    "app/src/main/java/za/org/rtc/community/feature/events/presentation/CommunityEventsViewModel.kt",
):
    assert not (ROOT / removed).exists(), removed

# Daily Post resident and editorial contracts.
for token in ('NEWS', 'BREAKING', 'DRAFT', 'SCHEDULED', 'PUBLISHED', 'ARCHIVED'):
    assert token in domain
for token in ('Breaking News', 'Hero Story', 'Gallery Story', 'Video Lead', 'Community Briefing', 'Feature Story'):
    assert token in domain
for token in ('page(', 'comments(', 'addComment(', 'translate(', 'narration(', 'uploadMedia(', 'publishNow(', 'schedule(', 'archive('):
    assert token in repository, token
for token in ('Conversation', 'Translate', 'Listen', 'DailyPostPreviewDialog', 'Reply'):
    assert token in resident_ui, token
assert 'comments' in resident_ui.lower()
for token in ('Templates', 'Preview', 'Schedule', 'Push notification'):
    assert token.lower() in studio_ui.lower(), token
# Existing publications/history are browsable and manageable across lifecycle states.
assert 'Text("Publications"' in studio_ui
assert 'DailyPostState.entries.forEach' in studio_ui
assert 'items(state.publications' in studio_ui
assert 'PublicationManagementCard(' in studio_ui
assert 'onEdit(post)' in studio_ui
assert 'onArchive(post)' in studio_ui
# Quoting is verified by behavior rather than one exact copy label.
assert 'Quote an RTC publication' in studio_ui
assert 'DailyPostBlockType.QUOTED_PUBLICATION' in studio_ui
assert 'quotedPostId = quoted.id' in studio_ui
assert 'draft.copy(quotedPostId = quoted.id' in studio_ui
assert 'daily_post_studio' in admin_catalog

# One-time foreground preview and push/deep-link open path.
assert 'Lifecycle.Event.ON_START' in global_host
assert 'viewModel.checkPreview()' in global_host
assert 'viewModel.dismissPreview(open = false)' in global_host
assert 'DailyPostScreen(' in global_host
assert 'DailyPostGlobalHost(' in main_activity
assert 'ACTION_OPEN_DAILY_POST' in main_activity
assert 'EXTRA_DAILY_POST_ID' in main_activity
assert 'rtc://community/daily-post/' in fcm_service
assert 'postDailyPostNotification' in fcm_service
assert 'notificationType == "DAILY_POST"' in fcm_service
# Automatic preview claiming is owned only by the activity-level lifecycle host; route-scoped
# DailyPostViewModels refresh the feed but do not independently claim an unseen publication.
init_block = view_model.split('init {', 1)[1].split('}', 1)[0]
assert 'refresh()' in init_block
assert 'checkPreview()' not in init_block

# Server trust boundaries and one-time preview semantics.
for token in (
    'daily_post_require_editor',
    'daily_post_comment_create_v1',
    'daily_post_preview_next_v1',
    'daily_post_preview_mark_v1',
    'daily_post_publish_v1',
    'daily_post_claim_due_jobs_v1',
    'daily_post_audit_events',
):
    assert token in security, token
assert "state = 'PUBLISHED'" in security
assert "auth.uid()" in security
assert "aal2" in security
# Publication media is private storage with published/editor RLS. AI narration is a distinct
# private bucket with no client RLS policy; the service-role Edge Function alone issues signed URLs.
assert "('daily-post-media', 'daily-post-media', false" in storage
assert "('daily-post-ai-audio', 'daily-post-ai-audio', false" in storage
assert "bucket_id = 'daily-post-media'" in storage_security
assert 'daily_post_ai_audio_client_read' in storage_security
assert 'daily_post_ai_audio_client_write' in storage_security
assert 'CREATE POLICY daily_post_ai_audio' not in storage_security
assert 'verifyDailyPostSchedulerCaller' in scheduler_fn
assert 'assert_daily_post_scheduler_secret' in scheduler_fn
assert 'x-rtc-daily-post-scheduler-secret' in scheduler_fn
assert 'idempotency_key' in scheduler_fn
assert 'notification_type: "DAILY_POST"' in scheduler_fn
assert 'DAILY_POST' in scheduler_fn
assert 'rtc_daily_post_scheduler_secret' in scheduler_activation
assert "'rtc-daily-post-scheduler'" in scheduler_activation
assert 'x-rtc-daily-post-scheduler-secret' in scheduler_activation
assert "state = 'DELETED' AND body = ''" in scheduler_activation
assert '[functions.daily-post-language]' in supabase_config
assert '[functions.daily-post-scheduler]' in supabase_config
assert 'verify_jwt = false' in supabase_config.split('[functions.daily-post-scheduler]', 1)[1]
assert 'enforceRateLimit' in language_fn
assert 'geminiTranslation' in language_fn
providers = (ROOT / 'supabase/functions/daily-post-language/providers.ts').read_text()
assert 'texttospeech.googleapis.com' in providers
assert 'get_firebase_fcm_service_account' in providers
assert 'validate_only: true' in scheduler_fn
assert 'verifyDailyPostSchedulerCaller(req, admin)' in scheduler_fn
assert 'daily_post_translations' in language_fn

# Multilingual onboarding persists a locale choice instead of hard-coding one language.
for token in ('English', 'isiZulu', 'isiXhosa', 'Afrikaans', 'Setswana'):
    assert token in onboarding, token
assert 'onboardingLanguage' in preferences
assert 'onboarding_language' in preferences
assert 'SUPPORTED_ONBOARDING_LANGUAGES' in preferences

print('Daily Post feature contracts passed.')
