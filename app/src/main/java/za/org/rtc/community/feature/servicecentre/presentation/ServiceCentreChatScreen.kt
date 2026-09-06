package za.org.rtc.community.feature.servicecentre.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import za.org.rtc.community.feature.servicecentre.presentation.viewmodel.ServiceCentreBookingViewModel
import za.org.rtc.community.ui.theme.RtcSize
import za.org.rtc.community.ui.theme.RtcSpacing

@Composable
fun ServiceCentreChatRoute(
    bookingId: String,
    viewModel: ServiceCentreBookingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    var body by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(bookingId, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                viewModel.refreshMessages(bookingId)
                delay(5_000)
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(RtcSpacing.pageGutter), verticalArrangement = Arrangement.spacedBy(RtcSpacing.contentGroup)) {
        Text("Booking chat", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        ServiceCentreMessageBanner(state.message, viewModel::dismissMessage)
        if (state.working) LinearProgressIndicator(Modifier.fillMaxWidth())
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(RtcSpacing.listGap),
        ) {
            if (state.messages.isEmpty()) item { Text("No messages yet. Use chat for simple booking details or negotiation.") }
            items(state.messages, key = { it.id }) { message ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(RtcSpacing.cardPadding), verticalArrangement = Arrangement.spacedBy(RtcSpacing.relatedText)) {
                        Text(if (message.senderUserId == state.detail?.customerUserId) "Customer" else "Provider", fontWeight = FontWeight.SemiBold)
                        Text(message.body)
                        Text(message.createdAt.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM · HH:mm")), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(RtcSpacing.controlGap), verticalAlignment = Alignment.Bottom) {
            OutlinedTextField(
                value = body,
                onValueChange = { body = it.take(1000) },
                label = { Text("Message") },
                supportingText = { Text("${body.length}/1000") },
                modifier = Modifier.weight(1f),
                minLines = 1,
                maxLines = 4,
            )
            Button(
                onClick = {
                    viewModel.sendMessage(bookingId, body)
                    if (body.trim().isNotEmpty()) body = ""
                },
                enabled = !state.working && body.trim().isNotEmpty(),
                modifier = Modifier.height(RtcSize.minimumTouchTarget),
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send message")
                Spacer(Modifier.size(RtcSpacing.micro))
            }
        }
    }
}
