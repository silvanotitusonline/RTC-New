package za.org.rtc.community.feature.servicecentre.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import za.org.rtc.community.feature.servicecentre.domain.ServiceCentreBookingTab
import za.org.rtc.community.feature.servicecentre.presentation.viewmodel.ServiceCentreBookingViewModel
import za.org.rtc.community.ui.navigation.LocalSharedTransitionScope
import za.org.rtc.community.ui.navigation.LocalNavAnimatedVisibilityScope
import za.org.rtc.community.ui.navigation.EmphasizedEasing
import za.org.rtc.community.ui.navigation.CardExpandDurationMs
import za.org.rtc.community.ui.components.BookingCardSkeleton
import za.org.rtc.community.ui.components.LocalLazyListState
import za.org.rtc.community.ui.components.parallaxScrollItem
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.ExperimentalSharedTransitionApi
import za.org.rtc.community.navigation.RtcRoute
import za.org.rtc.community.ui.theme.RtcSpacing
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import za.org.rtc.community.ui.animation.RtcMotionPatterns

private data class ServiceCentreTabSpec(val title: String, val tab: ServiceCentreBookingTab)

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun ServiceCentreBookingHubRoute(
    onNavigate: (String) -> Unit,
    viewModel: ServiceCentreBookingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var selectedIndex by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf(
        ServiceCentreTabSpec("Pending", ServiceCentreBookingTab.PENDING),
        ServiceCentreTabSpec("Accepted", ServiceCentreBookingTab.ACCEPTED),
        ServiceCentreTabSpec("Completed / Declined", ServiceCentreBookingTab.HISTORY),
    )
    LaunchedEffect(Unit) { viewModel.loadBookings() }
    val visible = state.bookings.filter { it.status.tab == tabs[selectedIndex].tab }


    val sharedTransitionScope = LocalSharedTransitionScope.current
    val animatedVisibilityScope = LocalNavAnimatedVisibilityScope.current
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(RtcSpacing.contentGroup)) {
            Text("Booking Hub", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            ServiceCentreMessageBanner(state.message, viewModel::dismissMessage)
            if (state.loading || state.working) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        PrimaryTabRow(selectedTabIndex = selectedIndex) {
            tabs.forEachIndexed { index, tab ->
                Tab(selected = selectedIndex == index, onClick = { selectedIndex = index }, text = { Text(tab.title) })
            }
        }
        AnimatedContent(
            targetState = selectedIndex,
            transitionSpec = RtcMotionPatterns.lateralTransitionSpec(),
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectHorizontalDragGestures { _, dragAmount ->
                        if (dragAmount < -40 && selectedIndex < tabs.size - 1) {
                            selectedIndex++
                        } else if (dragAmount > 40 && selectedIndex > 0) {
                            selectedIndex--
                        }
                    }
                },
            label = "ServiceCentreBookingTabLateral",
        ) { tabIdx ->
            val visible = state.bookings.filter { it.status.tab == tabs[tabIdx].tab }
            val listState = rememberLazyListState()
            CompositionLocalProvider(LocalLazyListState provides listState) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(RtcSpacing.pageGutter),
                    verticalArrangement = Arrangement.spacedBy(RtcSpacing.cardGap),
                ) {
                    if (visible.isEmpty() && state.loading) {
                        items(3) {
                            BookingCardSkeleton()
                        }
                    } else if (visible.isEmpty()) {
                        item { Text("No bookings in this section yet.") }
                    }
                    itemsIndexed(visible, key = { _, it -> it.id }) { index, booking ->
                        val actions = booking.primaryActions()
                        val sharedModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
                            with(sharedTransitionScope) {
                                Modifier.sharedBounds(
                                    sharedContentState = rememberSharedContentState(key = "service_booking_${booking.id}"),
                                    animatedVisibilityScope = animatedVisibilityScope,
                                    boundsTransform = { _, _ -> tween(CardExpandDurationMs, easing = EmphasizedEasing) },
                                    resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
                                    renderInOverlayDuringTransition = false,
                                    zIndexInOverlay = 1f,
                                ).skipToLookaheadSize()
                            }
                        } else { Modifier }

                        ServiceCentreBookingCard(
                            booking = booking,
                            modifier = Modifier
                                .parallaxScrollItem(index = index, rate = 0.05f)
                                .then(sharedModifier),
                            onOpen = { onNavigate(RtcRoute.serviceCentreBooking(booking.id)) },
                            onChat = { onNavigate(RtcRoute.serviceCentreChat(booking.id)) },
                            onAccept = if ("ACCEPT" in actions) ({ viewModel.accept(booking.id) }) else null,
                            onDecline = if ("DECLINE" in actions) ({ viewModel.decline(booking.id) }) else null,
                            onCancel = if ("CANCEL" in actions) ({ viewModel.cancel(booking.id) }) else null,
                            onComplete = if ("COMPLETE" in actions) ({ viewModel.complete(booking.id) }) else null,
                        )
                    }
                }
            }
        }
    }
}
