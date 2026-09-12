package za.org.rtc.community.feature.explore

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.launch
import za.org.rtc.community.core.LocalDraft
import za.org.rtc.community.feature.community.UnsavedWorkDialog
import za.org.rtc.community.ui.theme.RtcSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NoticeSubmissionSheet(
    draft: LocalDraft?,
    onDismiss: () -> Unit,
    onSubmit: (String, String) -> Unit,
    onSaveDraft: (String, String) -> Unit,
    onDiscardDraft: () -> Unit,
) {
    var title by rememberSaveable { mutableStateOf(draft?.title.orEmpty()) }
    var body by rememberSaveable { mutableStateOf(draft?.body.orEmpty()) }
    var leaveConfirmationOpen by rememberSaveable { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    fun hideAnd(action: () -> Unit) {
        scope.launch {
            sheetState.hide()
            action()
        }
    }

    fun requestClose() {
        if (title.isNotBlank() || body.isNotBlank()) leaveConfirmationOpen = true else hideAnd(onDismiss)
    }
    ModalBottomSheet(onDismissRequest = ::requestClose, sheetState = sheetState) {
        Column(modifier = Modifier.padding(RtcSpacing.standard), verticalArrangement = Arrangement.spacedBy(RtcSpacing.small)) {
            Text("Submit a Community Notice", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Resident submissions are reviewed before publication. You can track Submitted, Under review, Published, or Not published in this section.")
            OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = body, onValueChange = { body = it }, label = { Text("Update details") }, modifier = Modifier.fillMaxWidth(), minLines = 4)
            Row(horizontalArrangement = Arrangement.spacedBy(RtcSpacing.small)) {
                OutlinedButton(onClick = ::requestClose, modifier = Modifier.weight(1f)) { Text("Cancel") }
                Button(onClick = { hideAnd { onSubmit(title, body) } }, enabled = title.isNotBlank() && body.isNotBlank(), modifier = Modifier.weight(1f)) { Text("Submit for review") }
            }
            Spacer(Modifier.height(RtcSpacing.small))
        }
    }
    if (leaveConfirmationOpen) {
        UnsavedWorkDialog(
            onKeepEditing = { leaveConfirmationOpen = false },
            onSaveDraft = { onSaveDraft(title, body); leaveConfirmationOpen = false; hideAnd(onDismiss) },
            onDiscardChanges = { onDiscardDraft(); leaveConfirmationOpen = false; hideAnd(onDismiss) }
        )
    }
}
