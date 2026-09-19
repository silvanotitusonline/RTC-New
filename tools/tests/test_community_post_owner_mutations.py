from release_contract_context import ROOT


def test_owner_post_mutations_use_genuine_rpc_contracts_end_to_end():
    migration = ROOT / 'supabase/migrations/20260918120000_community_post_owner_edit_hard_delete.sql'
    repo = (ROOT / 'app/src/main/java/za/org/rtc/community/feature/community/SupabaseCommunityRepository.kt').read_text()
    contract = (ROOT / 'app/src/main/java/za/org/rtc/community/feature/community/CommunityRepository.kt').read_text()
    vm = (ROOT / 'app/src/main/java/za/org/rtc/community/feature/community/CommunityViewModel.kt').read_text()
    card = (ROOT / 'app/src/main/java/za/org/rtc/community/feature/community/CommunityPostCard.kt').read_text()
    feed = (ROOT / 'app/src/main/java/za/org/rtc/community/feature/community/CommunityFeedScreen.kt').read_text()
    detail = (ROOT / 'app/src/main/java/za/org/rtc/community/feature/community/CommunityPostDetailScreen.kt').read_text()

    sql = migration.read_text()
    assert 'create or replace function public.edit_community_post' in sql
    assert 'create or replace function public.delete_community_post' in sql
    assert "author_id = auth.uid()" in sql
    assert 'delete from public.community_posts' in sql
    assert "jsonb_build_object('hardDelete', true)" in sql
    assert 'grant execute on function public.edit_community_post(uuid, text, text, text) to authenticated, service_role;' in sql
    assert 'grant execute on function public.delete_community_post(uuid) to authenticated, service_role;' in sql

    assert 'suspend fun updatePost(postId: String, body: String, category: String)' in contract
    assert 'function = "edit_community_post"' in repo
    assert 'function = "delete_community_post"' in repo
    assert 'supabase.from("community_posts").delete' not in repo
    assert 'fun updatePost(post: CommunityPost, body: String' in vm
    assert 'pendingPostIds' in vm
    assert 'onEditPost' in card and 'onDeletePost' in card
    assert 'CommunityPostEditorDialog' in feed and 'Delete permanently' in feed
    assert 'CommunityPostEditorDialog' in detail and 'Delete permanently' in detail


def test_owner_post_mutation_ui_does_not_grant_staff_override():
    feed = (ROOT / 'app/src/main/java/za/org/rtc/community/feature/community/CommunityFeedScreen.kt').read_text()
    detail = (ROOT / 'app/src/main/java/za/org/rtc/community/feature/community/CommunityPostDetailScreen.kt').read_text()
    assert 'post.authorId == session.id' in feed
    assert 'activePost.authorId == session.id' in detail
    assert 'UserRole.MODERATOR' not in feed[feed.find('val isPostOwner'):feed.find('val isPostOwner') + 700]
