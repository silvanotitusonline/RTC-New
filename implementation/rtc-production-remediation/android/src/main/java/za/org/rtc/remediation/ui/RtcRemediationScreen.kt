package za.org.rtc.remediation.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.Assignment
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import za.org.rtc.remediation.data.RtcRepository
import za.org.rtc.remediation.model.FeedPost
import za.org.rtc.remediation.model.PreparedImage
import za.org.rtc.remediation.model.Provider
import za.org.rtc.remediation.presentation.BookingViewModel
import za.org.rtc.remediation.presentation.CommunityViewModel
import za.org.rtc.remediation.presentation.PostComposerViewModel
import za.org.rtc.remediation.presentation.ReportFormViewModel

/** Mount once under the Activity or parent NavGraph owner, keyed by signed-in account.
 * All tab destinations share that owner; removing a destination never discards its draft.
 * The host must clear this account's owner on logout before mounting another account.
 * [onRequestReportLocation] should invoke the location module and call setLocation on the
 * provided report VM. [providerDistances] contains measured route meters, never mock data. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RtcRemediationScreen(
    repository: RtcRepository,
    accountId: String,
    prepareImage: suspend (Uri) -> PreparedImage,
    onExit: () -> Unit,
    onProviderDirections: (Provider) -> Unit,
    modifier: Modifier = Modifier,
    createCameraUri: (() -> Uri)? = null,
    providerDistances: Map<String, Long?> = emptyMap(),
    onRequestReportLocation: ((ReportFormViewModel) -> Unit)? = null,
    owner: ViewModelStoreOwner = checkNotNull(LocalViewModelStoreOwner.current),
) {
    val community: CommunityViewModel = viewModel(owner, key = "$accountId:community", factory = CommunityViewModel.factory(repository))
    val form: ReportFormViewModel = viewModel(owner, key = "$accountId:report", factory = ReportFormViewModel.factory(repository))
    val composer: PostComposerViewModel = viewModel(owner, key = "$accountId:composer", factory = PostComposerViewModel.factory(repository))
    val booking: BookingViewModel = viewModel(owner, key = "$accountId:booking", factory = BookingViewModel.factory(repository))
    val feed by community.feed.collectAsStateWithLifecycle()
    val reports by community.reports.collectAsStateWithLifecycle()
    val providers by community.providers.collectAsStateWithLifecycle()
    val outbox by community.outbox.collectAsStateWithLifecycle()
    val dashboard by community.dashboard.collectAsStateWithLifecycle()
    val reportState by form.state.collectAsStateWithLifecycle()
    val composerState by composer.state.collectAsStateWithLifecycle()
    val bookingState by booking.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable(accountId) { mutableStateOf("Feed") }
    var pendingLeave by remember { mutableStateOf<(() -> Unit)?>(null) }
    var openedPost by remember { mutableStateOf<FeedPost?>(null) }
    var bookingOpen by rememberSaveable(accountId) { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val currentDirty = when (tab) { "Report" -> reportState.isDirty; "Feed" -> composerState.isDirty; else -> false }
    val currentBusy = reportState.submitting || composerState.submitting || composerState.preparing

    fun requestLeave(action: () -> Unit) {
        when {
            currentBusy -> scope.launch { snackbar.showSnackbar("Wait for the current save to finish.") }
            currentDirty -> pendingLeave = action
            else -> action()
        }
    }

    BackHandler(enabled = openedPost == null && !bookingOpen) {
        requestLeave { if (tab == "Feed") onExit() else tab = "Feed" }
    }
    LaunchedEffect(community) { community.messages.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(reportState.queuedId) {
        if (reportState.queuedId != null) {
            form.dismissQueued()
            val message = if (repository.isOnline()) "Report saved for delivery." else "Network offline. Report saved for later."
            scope.launch { snackbar.showSnackbar(message) }
        }
    }
    LaunchedEffect(composerState.queuedId) {
        if (composerState.queuedId != null) {
            composer.dismissQueued()
            val message = if (repository.isOnline()) "Update saved for delivery." else "Network offline. Update saved for later."
            scope.launch { snackbar.showSnackbar(message) }
        }
    }

    Scaffold(
        modifier = modifier, contentWindowInsets = WindowInsets.safeDrawing,
        topBar = { TopAppBar(
            title = { Text("RTC · $tab") },
            navigationIcon = { TextButton(onClick = { requestLeave(onExit) }) { Text("Back") } },
            actions = { TextButton(onClick = community::refresh) { Text("Refresh") } },
        ) },
        bottomBar = { NavigationBar {
            listOf("Feed" to Icons.Outlined.Home, "Report" to Icons.Outlined.AddCircleOutline,
                "Activity" to Icons.Outlined.Assignment, "Market" to Icons.Outlined.Storefront).forEach { (destination, icon) ->
                NavigationBarItem(
                    selected = tab == destination,
                    onClick = { if (tab != destination) requestLeave { tab = destination } },
                    icon = { Icon(icon, contentDescription = null) },
                    label = { Text(destination) },
                )
            }
        } },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val screenModifier = Modifier.padding(padding).consumeWindowInsets(padding).fillMaxSize()
        when (tab) {
            "Report" -> ReportFormScreen(reportState, form, screenModifier, onRequestReportLocation?.let { callback -> { callback(form) } })
            "Activity" -> ReportsScreen(reports, dashboard, outbox, repository.imageLoader, community::refresh, community::retryOutbox, screenModifier)
            "Market" -> MarketplaceScreen(providers, providerDistances, repository.imageLoader, community::refresh,
                onBook = { provider -> booking.selectProvider(provider.id); bookingOpen = true },
                onDirections = onProviderDirections, modifier = screenModifier)
            else -> FeedScreen(feed, outbox, composerState, composer, repository.imageLoader, prepareImage,
                createCameraUri, community::refresh, { openedPost = it }, community::vote, community::retryOutbox,
                screenModifier.imePadding())
        }
    }

    if (pendingLeave != null) AlertDialog(
        onDismissRequest = { pendingLeave = null },
        title = { Text("Discard unsaved changes?") },
        text = { Text("You can keep this draft for later or discard it before leaving.") },
        confirmButton = { TextButton(onClick = {
            if (tab == "Report") form.discard() else if (tab == "Feed") composer.discard()
            val leave = pendingLeave; pendingLeave = null; leave?.invoke()
        }) { Text("Discard and leave") } },
        dismissButton = { Column {
            TextButton(onClick = { val leave = pendingLeave; pendingLeave = null; leave?.invoke() }) { Text("Keep draft and leave") }
            TextButton(onClick = { pendingLeave = null }) { Text("Keep editing") }
        } },
    )
    openedPost?.let { post -> AlertDialog(
        onDismissRequest = { openedPost = null }, title = { Text("Community update") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(post.body)
            RemotePhoto(post.imageUrl, repository.imageLoader, "Photo attached to community update")
            Text(za.org.rtc.remediation.presentation.PresentationRules.timestamp(post.createdAt))
        } },
        confirmButton = { TextButton(onClick = { openedPost = null }) { Text("Close") } },
    ) }
    if (bookingOpen) BookingDialog(bookingState, booking) { bookingOpen = false }
}
