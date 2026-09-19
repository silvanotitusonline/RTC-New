from release_contract_context import ROOT


def read(path: str) -> str:
    return (ROOT / path).read_text()


def test_local_drafts_are_account_scoped_with_forward_room_migration():
    database = read('app/src/main/java/za/org/rtc/community/data/local/RtcDatabase.kt')
    module = read('app/src/main/java/za/org/rtc/community/di/AppModule.kt')

    assert 'primaryKeys = ["owner_user_id", "area"]' in database
    assert '@ColumnInfo(name = "owner_user_id") val ownerUserId: String' in database
    assert 'WHERE owner_user_id = :ownerUserId ORDER BY savedAtEpochMillis DESC' in database
    assert 'fun observeForOwner(ownerUserId: String): Flow<List<LocalDraftEntity>>' in database
    assert 'WHERE owner_user_id = :ownerUserId AND area = :area' in database
    assert 'suspend fun deleteAreaForOwner(ownerUserId: String, area: String)' in database

    assert 'RTC_DATABASE_MIGRATION_2_3' in database
    assert 'Migration(2, 3)' in database
    assert 'Legacy drafts have no trustworthy account owner' in database
    assert 'DROP TABLE local_drafts' in database
    assert 'ALTER TABLE local_drafts_v3 RENAME TO local_drafts' in database
    assert 'INSERT INTO local_drafts_v3' not in database
    # Current production schema is version 11 after subsequent cache/media migrations.
    assert any(f'version = {version}' in database for version in (11, 10, 9, 8, 7, 6))

    assert 'import za.org.rtc.community.data.local.RTC_DATABASE_MIGRATION_2_3' in module
    assert 'RTC_DATABASE_MIGRATION_1_2' in module and 'RTC_DATABASE_MIGRATION_2_3' in module


def test_repository_draft_stream_and_mutations_follow_active_account_owner():
    repository = read('app/src/main/java/za/org/rtc/community/data/RtcRepository.kt')

    assert 'private fun draftOwnerIdOrNull(session: RtcSession): String?' in repository
    assert 'SessionAuthority.SUPABASE_AUTH, SessionAuthority.DEVELOPMENT_ADAPTER -> session.id' in repository
    assert 'SessionAuthority.PUBLIC -> null' in repository

    assert 'val ownerUserId = draftOwnerIdOrNull(activeSession) ?: return@collectLatest' in repository
    assert 'localDraftDao.observeForOwner(ownerUserId).collectLatest' in repository
    assert 'localDraftDao.observeAll()' not in repository

    save_start = repository.index('suspend fun saveDraft(')
    save_end = repository.index('suspend fun discardDraft(', save_start)
    save_body = repository[save_start:save_end]
    assert 'val ownerUserId = draftOwnerIdOrNull(_session.value) ?: return' in save_body
    assert 'ownerUserId = ownerUserId' in save_body

    discard_start = repository.index('suspend fun discardDraft(', save_end)
    discard_end = repository.index('suspend fun reportCommunityPost(', discard_start)
    discard_body = repository[discard_start:discard_end]
    assert 'val ownerUserId = draftOwnerIdOrNull(_session.value) ?: return' in discard_body
    assert 'localDraftDao.deleteAreaForOwner(ownerUserId, area.name)' in discard_body
    assert 'localDraftDao.deleteArea(' not in repository


def test_upload_recovery_remains_authenticated_owner_scoped_and_connectivity_constrained():
    worker = read('app/src/main/java/za/org/rtc/community/data/local/CommunityUploadWorker.kt')
    repository = read('app/src/main/java/za/org/rtc/community/data/RtcRepository.kt')

    assert 'production.currentAuthenticatedUserIdOrNull() ?: return Result.success()' in worker
    assert 'database.uploadOutboxDao().pendingForOwner(ownerUserId)' in worker
    assert 'pending.map { it.draftId }.distinct()' in worker
    assert 'return if (retryNeeded) Result.retry() else Result.success()' in worker

    assert 'setRequiredNetworkType(NetworkType.CONNECTED)' in repository
    assert 'enqueueUniqueWork("rtc-community-upload-recovery", ExistingWorkPolicy.KEEP, request)' in repository
    assert repository.count('enqueueUploadRecovery()') >= 4
