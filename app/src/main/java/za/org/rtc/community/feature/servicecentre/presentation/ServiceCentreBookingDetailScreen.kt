package za.org.rtc.community.feature.servicecentre.presentation

import android.content.Intent
import android.net.Uri
import za.org.rtc.community.ui.navigation.LocalSharedTransitionScope
import za.org.rtc.community.ui.navigation.LocalNavAnimatedVisibilityScope
import za.org.rtc.community.ui.navigation.EmphasizedEasing
import za.org.rtc.community.ui.navigation.CardExpandDurationMs
import za.org.rtc.community.ui.components.BookingCardSkeleton
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import za.org.rtc.community.feature.servicecentre.domain.ServiceCentreBooking
import za.org.rtc.community.feature.servicecentre.domain.ServiceCentreBookingStatus
import za.org.rtc.community.feature.servicecentre.presentation.viewmodel.ServiceCentreBookingViewModel
import za.org.rtc.community.ui.theme.RtcSpacing

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun ServiceCentreBookingDetailRoute(
    bookingId: String,
    onNavigate: (String) -> Unit,
    viewModel: ServiceCentreBookingViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val state by viewModel.state.collectAsStateWithLifecycle()

    var showBottomSheet by rememberSaveable { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var chatBody by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(bookingId) {
        viewModel.loadDetail(bookingId)
    }

    LaunchedEffect(bookingId, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                viewModel.refreshMessages(bookingId)
                delay(5_000)
            }
        }
    }

    val booking = state.detail


    val sharedTransitionScope = LocalSharedTransitionScope.current
    val animatedVisibilityScope = LocalNavAnimatedVisibilityScope.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Service Request & Timeline",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "Reference #${bookingId.uppercase()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { onNavigate("account") }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                actions = {
                    booking?.let {
                        IconButton(onClick = {
                            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:0800123456"))
                            context.startActivity(intent)
                        }) {
                            Icon(
                                imageVector = Icons.Filled.Call,
                                contentDescription = "Call Provider",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        val sharedModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
            with(sharedTransitionScope) {
                Modifier.sharedBounds(
                    sharedContentState = rememberSharedContentState(key = "service_booking_${bookingId}"),
                    animatedVisibilityScope = animatedVisibilityScope,
                    boundsTransform = { _, _ -> tween(CardExpandDurationMs, easing = EmphasizedEasing) },
                    resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
                    renderInOverlayDuringTransition = false,
                    zIndexInOverlay = 1f,
                ).skipToLookaheadSize()
            }
        } else { Modifier }

        Column(
            modifier = Modifier.then(sharedModifier)
                .fillMaxSize()
                .padding(padding),
        ) {
            ServiceCentreMessageBanner(state.message, viewModel::dismissMessage)
            if (state.loading || state.working) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }

            if (booking == null && (state.loading || state.working)) {
                Column(modifier = Modifier.padding(RtcSpacing.pageGutter)) {
                    BookingCardSkeleton()
                }
            } else if (booking == null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Booking details could not be found.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else if (booking != null) {
                BookingTimelineTabContent(
                    booking = booking,
                    viewModel = viewModel,
                    onSwitchToChat = { showBottomSheet = true },
                )
                
                if (showBottomSheet) {
                    ModalBottomSheet(
                        onDismissRequest = { showBottomSheet = false },
                        sheetState = sheetState,
                        containerColor = MaterialTheme.colorScheme.surface,
                    ) {
                        BookingChatTabContent(
                            bookingId = booking.id,
                            messages = state.messages,
                            chatBody = chatBody,
                            onBodyChange = { chatBody = it },
                            onSendMessage = {
                                viewModel.sendMessage(booking.id, chatBody)
                                chatBody = ""
                            },
                            isWorking = state.working,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BookingTimelineTabContent(
    booking: ServiceCentreBooking,
    viewModel: ServiceCentreBookingViewModel,
    onSwitchToChat: () -> Unit,
) {
    val context = LocalContext.current
    val actions = booking.primaryActions()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Status Banner Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = when (booking.status) {
                        ServiceCentreBookingStatus.CONFIRMED -> Color(0xFFE8F5E9)
                        ServiceCentreBookingStatus.PENDING_PROVIDER -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f)
                        ServiceCentreBookingStatus.COMPLETED -> Color(0xFFE3F2FD)
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    },
                ),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        imageVector = when (booking.status) {
                            ServiceCentreBookingStatus.CONFIRMED -> Icons.Filled.CheckCircle
                            ServiceCentreBookingStatus.PENDING_PROVIDER -> Icons.Filled.HourglassTop
                            ServiceCentreBookingStatus.COMPLETED -> Icons.Filled.Verified
                            else -> Icons.Filled.Info
                        },
                        contentDescription = null,
                        tint = when (booking.status) {
                            ServiceCentreBookingStatus.CONFIRMED -> Color(0xFF2E7D32)
                            ServiceCentreBookingStatus.PENDING_PROVIDER -> MaterialTheme.colorScheme.onTertiaryContainer
                            ServiceCentreBookingStatus.COMPLETED -> Color(0xFF1565C0)
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(32.dp),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = serviceCentreStatusLabel(booking.status),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = when (booking.status) {
                                ServiceCentreBookingStatus.CONFIRMED -> Color(0xFF1B5E20)
                                ServiceCentreBookingStatus.PENDING_PROVIDER -> MaterialTheme.colorScheme.onTertiaryContainer
                                ServiceCentreBookingStatus.COMPLETED -> Color(0xFF0D47A1)
                                else -> MaterialTheme.colorScheme.onSurface
                            },
                        )
                        Text(
                            text = when (booking.status) {
                                ServiceCentreBookingStatus.CONFIRMED -> "Service provider confirmed the booking! Appointment is scheduled for ${serviceCentreDateTime(booking.requestedStartAt)}."
                                ServiceCentreBookingStatus.PENDING_PROVIDER -> "Booking request submitted. Awaiting confirmation from ${booking.counterpartyDisplayName}."
                                ServiceCentreBookingStatus.COMPLETED -> "Service successfully completed and verified."
                                else -> "Booking status updated."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // Interactive Progress Stepper Visualizer
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "Booking Progress Stepper",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )

                    val steps = listOf(
                        Triple("Request Submitted", "Customer sent booking request", booking.createdAt),
                        Triple("Provider Confirmed", "Provider accepted time slot & quote", booking.acceptedAt ?: booking.confirmedAt),
                        Triple("Service In Progress", "Provider en route / performing work", booking.confirmedAt),
                        Triple("Service Completed", "Work completed & job closed", booking.completedAt),
                    )

                    val activeStepIndex = when (booking.status) {
                        ServiceCentreBookingStatus.PENDING_PROVIDER -> 0
                        ServiceCentreBookingStatus.CONFIRMED -> 2
                        ServiceCentreBookingStatus.COMPLETED -> 3
                        else -> 0
                    }

                    steps.forEachIndexed { idx, (title, sub, time) ->
                        val isDone = idx <= activeStepIndex
                        val isCurrent = idx == activeStepIndex

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.width(36.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isDone) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.surfaceVariant
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (isDone) {
                                        Icon(
                                            imageVector = Icons.Filled.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(14.dp),
                                        )
                                    } else {
                                        Text(
                                            text = "${idx + 1}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }

                                if (idx < steps.size - 1) {
                                    Box(
                                        modifier = Modifier
                                            .width(2.dp)
                                            .height(32.dp)
                                            .background(
                                                if (idx < activeStepIndex) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                            ),
                                    )
                                }
                            }

                            Spacer(Modifier.width(8.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isDone) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = sub,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                time?.let {
                                    Text(
                                        text = serviceCentreDateTime(it),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Service Provider Contact Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "Assigned Service Provider",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(28.dp),
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = booking.counterpartyDisplayName,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                )
                                Spacer(Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Filled.Verified,
                                    contentDescription = "Verified",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                            Text(
                                text = booking.categoryName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = {
                                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:0800123456"))
                                context.startActivity(intent)
                            },
                            modifier = Modifier.weight(1f).height(38.dp),
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Icon(Icons.Filled.Call, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Call Provider", style = MaterialTheme.typography.labelMedium)
                        }

                        OutlinedButton(
                            onClick = onSwitchToChat,
                            modifier = Modifier.weight(1f).height(38.dp),
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Chat & Notes", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }

        // Booking & Service Details Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = "Booking & Location Details",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Category", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(booking.categoryName, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Scheduled Date", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(serviceCentreDateTime(booking.requestedStartAt), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Service Address", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(booking.serviceLocationText, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Agreed Offer", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(serviceCentreMoney(booking.offerAmount), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }

        // Contextual Actions Bar
        item {
            if (actions.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("Booking Actions", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if ("ACCEPT" in actions) {
                                Button(
                                    onClick = { viewModel.accept(booking.id) },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text("Accept Booking")
                                }
                            }
                            if ("DECLINE" in actions) {
                                OutlinedButton(
                                    onClick = { viewModel.decline(booking.id) },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text("Decline")
                                }
                            }
                            if ("CANCEL" in actions) {
                                OutlinedButton(
                                    onClick = { viewModel.cancel(booking.id) },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text("Cancel Request")
                                }
                            }
                            if ("COMPLETE" in actions) {
                                Button(
                                    onClick = { viewModel.complete(booking.id) },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text("Mark Completed")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
