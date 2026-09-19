from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding='utf-8')


def test_database_deletion_suite_covers_database_feeds_cache_and_authorization():
    test = read('supabase/tests/community_post_deletion_and_moderation_test.sql')
    for required in (
        'public.community_posts',
        'public.community_post_feed',
        'public.community_personalized_feed',
        'public.community_comments',
        'public.community_post_media',
        'public.community_bookmarks',
        'public.community_reactions',
        'DELETE_NOT_ALLOWED',
        'community_moderation_actions',
        'COMMUNITY_POST_DELETED',
    ):
        assert required in test, required
    assert "'moderator REMOVE succeeds'" in test
    assert "'moderator REMOVE writes an audit event'" in test


def test_moderator_remove_is_not_a_soft_hide_disguised_as_delete():
    migration = read('supabase/migrations/20260918130000_moderator_true_remove_and_deletion_contract.sql')
    audit_migration = read('supabase/migrations/20260918140000_moderator_activity_audit_scope.sql')
    moderation = read('app/src/main/java/za/org/rtc/community/feature/administration/AdministrationContentModeration.kt')
    adapter = read('app/src/main/java/za/org/rtc/community/supabase/ProductionUxRepository.kt')
    repository = read('app/src/main/java/za/org/rtc/community/data/RtcRepository.kt')
    view_model = read('app/src/main/java/za/org/rtc/community/app/RtcViewModel.kt')
    assert "v_action = 'REMOVE'" in migration
    assert 'delete from public.community_posts' in migration
    assert "'hardDelete', v_action = 'REMOVE'" in migration
    assert "'REMOVE'" in migration
    assert 'create or replace function public.moderation_list_activity' in audit_migration
    assert 'moderation_list_activity' in adapter
    assert 'moderationActivity' in repository and 'moderationActivity' in view_model
    assert 'onRemove' in moderation
    assert 'viewModel.decideModerationReport(report.reportId, "REMOVE", decisionReason)' in moderation
    assert 'Community moderation audit' in moderation


def test_moderation_dashboard_exposes_flag_queue_decision_and_appeal_audit_boundaries():
    moderation = read('app/src/main/java/za/org/rtc/community/feature/administration/AdministrationContentModeration.kt')
    migration = read('supabase/migrations/20260822050000_release1_operations_hub_foundation.sql')
    assert 'Reports' in moderation
    assert 'Dismiss' in moderation and 'Lock' in moderation and 'Hide' in moderation and 'Remove permanently' in moderation
    assert 'Appeals' in moderation
    assert 'Required moderation / appeal reason' in moderation
    assert 'COMMUNITY_MODERATION_' in migration
    assert 'ADMINISTRATIVE_ACTIVITY_VIEWED' in migration
    assert 'MODERATION_REPORT' in migration


def test_moderation_role_boundary_is_enforced_in_ui_and_rpc_contract():
    moderation = read('app/src/main/java/za/org/rtc/community/feature/administration/AdministrationContentModeration.kt')
    migration = read('tools/supabase-local/recovery/20260825122945_recover_community_reporting_and_moderation_baseline.sql')
    assert 'UserRole.MODERATOR, UserRole.SYSTEM_ADMIN' in moderation
    assert 'private.is_moderation_authority()' in migration
    assert 'private.community_staff()' in migration
    assert 'ops_assert_staff()' in migration
