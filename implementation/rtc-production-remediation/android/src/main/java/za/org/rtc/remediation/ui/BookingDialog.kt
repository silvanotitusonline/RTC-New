package za.org.rtc.remediation.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import za.org.rtc.remediation.presentation.BookingState
import za.org.rtc.remediation.presentation.BookingViewModel
import za.org.rtc.remediation.presentation.PresentationRules

@Composable
fun BookingDialog(state: BookingState, viewModel: BookingViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val zone = ZoneId.systemDefault()
    AlertDialog(
        onDismissRequest = { if (!state.submitting) onDismiss() },
        title = { Text(if (state.booking != null) "Booking received" else "Book service") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (state.booking != null) {
                    Text("Status: ${PresentationRules.status(state.booking.status)}")
                    Text("Requested for ${PresentationRules.timestamp(state.booking.startsAt)}")
                } else {
                    Text("Choose a date and time in ${zone.id.replace('_', ' ')}.")
                    OutlinedButton(onClick = {
                        val initial = runCatching { Instant.parse(state.startsAt).atZone(zone) }
                            .getOrElse { ZonedDateTime.now(zone).plusHours(1) }
                        DatePickerDialog(context, { _, year, month, day ->
                            TimePickerDialog(context, { _, hour, minute ->
                                val selected = java.time.LocalDateTime.of(year, month + 1, day, hour, minute)
                                    .atZone(zone).toInstant()
                                viewModel.setStartsAt(selected.toString())
                            }, initial.hour, initial.minute, true).show()
                        }, initial.year, initial.monthValue - 1, initial.dayOfMonth).apply {
                            datePicker.minDate = System.currentTimeMillis()
                        }.show()
                    }, enabled = !state.submitting, modifier = Modifier.fillMaxWidth()) {
                        Text(if (state.startsAt.isBlank()) "Choose date and time" else PresentationRules.timestamp(state.startsAt))
                    }
                    OutlinedTextField(
                        state.notes, viewModel::setNotes, modifier = Modifier.fillMaxWidth(),
                        label = { Text("Booking notes (optional)") }, minLines = 2,
                        enabled = !state.submitting,
                        isError = PresentationRules.characterCount(state.notes) > 2_000,
                        supportingText = { Text("${PresentationRules.characterCount(state.notes)}/2,000") },
                    )
                    if (state.startsAt.isNotBlank()) PresentationRules.bookingError(state.input, Instant.now())?.let {
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            }
        },
        confirmButton = {
            if (state.booking != null) TextButton(onClick = onDismiss) { Text("Done") }
            else Button(onClick = viewModel::submit, enabled = state.canSubmit) { Text(if (state.submitting) "Booking…" else "Confirm booking") }
        },
        dismissButton = {
            if (state.booking == null) TextButton(onClick = onDismiss, enabled = !state.submitting) { Text("Close · keep draft") }
        },
    )
}
