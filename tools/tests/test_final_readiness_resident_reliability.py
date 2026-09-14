from release_contract_context import ROOT


def read(path: str) -> str:
    return (ROOT / path).read_text()


def test_resident_shell_surfaces_sanitized_refresh_failure_with_retry_and_dismiss():
    shell = read('app/src/main/java/za/org/rtc/community/ui/navigation/RtcCommunityApp.kt')
    assert 'val liveContentMessage by viewModel.liveContentMessage.collectAsStateWithLifecycle()' in shell
    assert 'ResidentRefreshFailureBanner(' in shell
    assert 'onRetry = viewModel::refreshLiveContent' in shell
    assert 'onDismiss = viewModel::dismissLiveContentMessage' in shell


def test_resident_snapshot_labels_do_not_claim_live_when_freshness_is_unknown():
    home = read('app/src/main/java/za/org/rtc/community/feature/home/HomeComponents.kt')
    explore = read('app/src/main/java/za/org/rtc/community/feature/explore/ExploreScreen.kt')
    assert 'Text("Live"' not in home
    assert 'RtcStatusChip("Updated live"' not in explore


def test_saved_draft_discard_requires_explicit_confirmation():
    components = read('app/src/main/java/za/org/rtc/community/feature/home/HomeComponents.kt')
    start = components.index('fun ContinueDraftCard(')
    body = components[start:]
    assert 'rememberSaveable' in body
    assert 'AlertDialog(' in body
    assert 'Discard saved draft?' in body
    assert 'onDiscard()' in body
    assert 'TextButton(onClick = onDiscard)' not in body


def test_service_centre_booking_detail_enters_and_leaves_loading_state():
    view_model = read('app/src/main/java/za/org/rtc/community/feature/servicecentre/presentation/viewmodel/ServiceCentreBookingViewModel.kt')
    start = view_model.index('fun loadDetail(bookingId: String)')
    end = view_model.index('fun createBooking(', start)
    body = view_model[start:end]
    request = body.index('bookingRepository.bookingDetail(bookingId)')
    loading_true = body.index('_state.value = _state.value.copy(loading = true, message = null)')
    assert loading_true < request
    assert body.count('loading = false') >= 2


def test_marketplace_save_mutation_has_visible_failure_feedback_without_destroying_detail():
    view_model = read('app/src/main/java/za/org/rtc/community/feature/marketplace/presentation/viewmodel/MarketplaceDiscoveryViewModel.kt')
    screen = read('app/src/main/java/za/org/rtc/community/feature/marketplace/presentation/MarketplaceBusinessScreen.kt')
    assert 'private val _actionMessage = MutableStateFlow<String?>(null)' in view_model
    assert 'val actionMessage = _actionMessage.asStateFlow()' in view_model
    assert 'fun dismissActionMessage()' in view_model
    start = view_model.index('fun toggleSaved(')
    body = view_model[start:]
    assert '.onFailure' in body
    assert '_actionMessage.value' in body
    assert '_detail.value = MarketplaceLoadState.Failure' not in body
    assert 'val actionMessage by viewModel.actionMessage.collectAsStateWithLifecycle()' in screen
    assert 'MarketplaceNotice(' in screen
    assert 'onDismiss = viewModel::dismissActionMessage' in screen


def test_home_snapshot_copy_does_not_overstate_refresh_freshness():
    home = read('app/src/main/java/za/org/rtc/community/feature/home/HomeScreen.kt')
    cards = read('app/src/main/java/za/org/rtc/community/feature/home/HomeResidentModernisationCards.kt')
    assert 'Live project and service visibility' not in home
    assert 'Text("Community snapshot"' in cards
    assert 'Verified Public Reports.' in cards
    assert 'Updated live' not in cards
    assert 'latest refresh' not in cards.lower()


def test_directory_pagination_rejects_stale_responses_and_deduplicates_items():
    repository = read('app/src/main/java/za/org/rtc/community/data/RtcRepository.kt')
    assert 'private var directoryGeneration = 0L' in repository
    assert 'val refreshGeneration = ++directoryGeneration' in repository
    assert 'if (refreshGeneration == directoryGeneration)' in repository

    wrappers_start = repository.index('suspend fun loadMoreProjects()')
    wrappers_end = repository.index('suspend fun searchPublicContent(', wrappers_start)
    wrappers = repository[wrappers_start:wrappers_end]
    assert wrappers.count('generation = directoryGeneration') == 3
    assert wrappers.count('currentState =') == 3

    helper_start = repository.index('private suspend fun <T> loadMore(')
    helper_end = repository.index('fun dismissLiveContentMessage()', helper_start)
    helper = repository[helper_start:helper_end]
    assert 'generation: Long' in helper
    assert 'currentState: () -> DirectoryPage<T>' in helper
    assert 'generation != directoryGeneration || currentState() != current' in helper
    assert 'items = (current.items + next.items).distinct()' in helper
