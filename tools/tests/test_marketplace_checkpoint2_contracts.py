from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
MARKETPLACE = ROOT / "app/src/main/java/za/org/rtc/community/feature/marketplace"
MUTATION = MARKETPLACE / "domain/MarketplaceMutation.kt"
MEDIA = MARKETPLACE / "domain/MarketplaceMediaState.kt"
MODELS = MARKETPLACE / "domain/MarketplaceModels.kt"
OWNER_VIEWMODEL = MARKETPLACE / "presentation/viewmodel/MarketplaceOwnerViewModel.kt"
DRAFTS = MARKETPLACE / "data/local/MarketplaceDraftCheckpointStore.kt"
REPOSITORY = MARKETPLACE / "data/remote/SupabaseMarketplaceRepository.kt"
MIGRATION = ROOT / "supabase/migrations/20260828101500_marketplace_mutation_replay_hardening.sql"
TARGET_BINDING_MIGRATION = ROOT / "supabase/migrations/20260828101600_marketplace_replay_target_binding.sql"


def test_marketplace_checkpoint2_tracks_logical_mutations_and_resume_state():
    assert MUTATION.exists(), "Checkpoint 2 must add MarketplaceMutationTracker."
    assert OWNER_VIEWMODEL.exists(), "Checkpoint 2 mutation and resume state must remain in the scoped owner ViewModel."
    mutation = MUTATION.read_text()
    drafts = DRAFTS.read_text()
    owner_viewmodel = OWNER_VIEWMODEL.read_text()
    assert "class MarketplaceMutationTracker" in mutation
    assert "fun failed(command: MarketplaceMutationCommand)" in mutation
    assert "fun succeeded(command: MarketplaceMutationCommand)" in mutation
    assert "suspend fun read(): MarketplaceDraftCheckpoint?" in drafts
    assert "private val mutations = MarketplaceMutationTracker()" in owner_viewmodel
    assert "checkpoints.read()" in owner_viewmodel


def test_marketplace_checkpoint2_media_recovery_is_bounded():
    assert MEDIA.exists(), "Checkpoint 2 must add explicit Marketplace media operation state."
    media = MEDIA.read_text()
    models = MODELS.read_text()
    repository = REPOSITORY.read_text()
    assert "enum class MarketplaceMediaStage" in media
    assert "class MarketplaceSignedUrlCache" in media
    assert "maxEntries" in media, "Signed URL cache must have a hard entry bound."
    assert "entries.size > maxEntries" in media
    assert "onProgress: (MarketplaceMediaStage, Float) -> Unit" in models
    assert "mediaUrl(path: String)" in models
    assert "MarketplaceSignedUrlCache" in repository


def test_marketplace_checkpoint2_sends_idempotency_keys_for_mutations():
    models = MODELS.read_text()
    repository = REPOSITORY.read_text()
    assert "createDraft(displayName: String, idempotencyKey: String)" in models
    assert "saveLocation(businessId: String, locationId: String?, payload: JsonObject, idempotencyKey: String)" in models
    assert "saveOffering(businessId: String, offeringId: String?, payload: JsonObject, idempotencyKey: String)" in models
    assert "reportReview(reviewId: String, reason: String, details: String, idempotencyKey: String)" in models
    assert 'put("p_idempotency_key", idempotencyKey)' in repository


def test_marketplace_checkpoint2_backend_replays_create_and_report_mutations():
    assert MIGRATION.exists(), "Checkpoint 2 must add a forward-only replay-hardening migration."
    sql = MIGRATION.read_text()
    assert sql.startswith("begin;")
    assert sql.rstrip().endswith("commit;")
    assert "private.marketplace_replay_metadata" in sql
    assert "create or replace function public.marketplace_upsert_location" in sql
    assert "create or replace function public.marketplace_upsert_offering" in sql
    assert "create or replace function public.marketplace_report_review" in sql
    assert "LOCATION_SAVED" in sql
    assert "OFFERING_SAVED" in sql
    assert "REVIEW_REPORTED" in sql
    assert "p_idempotency_key uuid" in sql
    assert "security definer set search_path = ''" in sql
    assert "revoke execute on function public.marketplace_report_review(uuid,uuid,text,text) from public, anon, authenticated;" in sql
    assert "grant execute on function public.marketplace_report_review(uuid,uuid,text,text,uuid) to authenticated;" in sql


def test_marketplace_checkpoint2_replay_keys_are_bound_to_the_original_target():
    assert TARGET_BINDING_MIGRATION.exists()
    sql = TARGET_BINDING_MIGRATION.read_text()
    assert "private.marketplace_assert_replay_target" in sql
    assert "Marketplace idempotency key was already used for another business." in sql
    assert "Marketplace idempotency key was already used for another submission." in sql
    assert "Marketplace idempotency key was already used for another media asset." in sql
    assert "Marketplace idempotency key was already used for another review." in sql
    assert "security definer set search_path = ''" in sql
    assert sql.startswith("begin;")
    assert sql.rstrip().endswith("commit;")
