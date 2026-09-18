package za.org.rtc.community.feature.community

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import java.io.File
import java.util.UUID
import za.org.rtc.community.core.LocalDraft
import za.org.rtc.community.ui.theme.RtcSize
import za.org.rtc.community.ui.theme.RtcSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PostComposer(
    draft: LocalDraft?,
    onDismiss: () -> Unit,
    isSubmitting: Boolean,
    onSubmit: (String, List<Uri>, String) -> Unit,
    onSaveDraft: (String) -> Unit,
    onDiscardDraft: () -> Unit,
) {
    var composer by remember { mutableStateOf(CommunityComposerState(body = draft?.body.orEmpty())) }
    var isSubmitted by remember { mutableStateOf(false) }
    val clientPostId = rememberSaveable { UUID.randomUUID().toString() }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    var cameraChooserOpen by rememberSaveable { mutableStateOf(false) }
    var leaveConfirmationOpen by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(composer.body, isSubmitted) {
        if (!isSubmitted && composer.body.isNotBlank()) {
            onSaveDraft(composer.body)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (!isSubmitted && composer.body.isNotBlank()) {
                onSaveDraft(composer.body)
            }
        }
    }

    fun asAttachment(uri: Uri) = CommunityComposerAttachment(
        id = uri.toString().ifBlank { UUID.randomUUID().toString() },
        uri = uri.toString(),
    )

    fun appendMedia(uri: Uri) {
        if (composer.attachments.size < 10) composer = composer.addAttachments(listOf(asAttachment(uri)))
    }

    fun createCaptureUri(extension: String): Uri {
        val directory = File(context.cacheDir, "community_media").apply { mkdirs() }
        val file = File.createTempFile("community_", extension, directory)
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    val galleryPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { uris ->
        composer = composer.addAttachments(uris.take(10).map(::asAttachment)).copy(
            attachments = composer.addAttachments(uris.map(::asAttachment)).attachments.take(10)
        )
    }
    val cameraPhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        pendingCameraUri?.takeIf { success }?.let(::appendMedia)
        pendingCameraUri = null
    }
    val cameraVideo = rememberLauncherForActivityResult(ActivityResultContracts.CaptureVideo()) { success ->
        pendingCameraUri?.takeIf { success }?.let(::appendMedia)
        pendingCameraUri = null
    }

    fun requestClose() {
        if (isSubmitted || isSubmitting) onDismiss()
        else if (composer.isDirty) leaveConfirmationOpen = true else onDismiss()
    }

    ModalBottomSheet(onDismissRequest = ::requestClose, sheetState = rememberModalBottomSheetState()) {
        Column(modifier = Modifier.padding(RtcSpacing.standard), verticalArrangement = Arrangement.spacedBy(RtcSpacing.small)) {
            Text("Create Community post", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Your post will be shared under your Community profile. Please keep it respectful and do not include sensitive personal information.")
            OutlinedTextField(
                value = composer.body,
                onValueChange = { composer = composer.withBody(it.take(280)) },
                label = { Text("What would you like to share?") },
                supportingText = { Text("${composer.body.trim().length}/280 · ${composer.attachments.size}/10 media items") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 4,
                enabled = !isSubmitting,
            )
            if (composer.attachments.isNotEmpty()) {
                Text("Selected media", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(RtcSpacing.compact)) {
                    items(composer.attachments, key = { it.id }) { attachment ->
                        Box {
                            AsyncImage(
                                model = attachment.uri,
                                contentDescription = "Selected Community media preview",
                                modifier = Modifier
                                    .size(RtcSize.mediaThumbnail)
                                    .clip(MaterialTheme.shapes.medium),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            )
                            IconButton(
                                onClick = { composer = composer.removeAttachment(attachment.id) },
                                enabled = !isSubmitting,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .semantics { contentDescription = "Remove selected media" },
                            ) {
                                Icon(Icons.Filled.Close, contentDescription = null)
                            }
                        }
                    }
                }
                Text("Images must be 5 MB or smaller; videos must be 20 MB or smaller and no longer than 3 minutes after preparation.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (isSubmitting) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "Preparing and uploading Community post" },
                )
                Text("Preparing and uploading securely. Your draft remains available if the upload is interrupted.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(RtcSpacing.compact), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { galleryPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) },
                    modifier = Modifier.weight(1f),
                    enabled = !isSubmitting && composer.attachments.size < 10,
                ) { Text("Choose gallery") }
                OutlinedButton(
                    onClick = { cameraChooserOpen = true },
                    modifier = Modifier.weight(1f),
                    enabled = !isSubmitting && composer.attachments.size < 10,
                ) { Text("Use camera") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(RtcSpacing.small)) {
                OutlinedButton(onClick = ::requestClose, modifier = Modifier.weight(1f), enabled = !isSubmitting) { Text("Cancel") }
                Button(
                    onClick = {
                        isSubmitted = true
                        onDiscardDraft()
                        onSubmit(
                            composer.body,
                            composer.attachments.map { Uri.parse(it.uri) },
                            clientPostId,
                        )
                    },
                    enabled = !isSubmitting && (composer.body.trim().isNotEmpty() || composer.attachments.isNotEmpty()),
                    modifier = Modifier.weight(1f),
                ) { Text(if (isSubmitting) "Posting…" else "Post") }
            }
            Spacer(Modifier.height(RtcSpacing.small))
        }
    }

    if (cameraChooserOpen) {
        AlertDialog(
            onDismissRequest = { cameraChooserOpen = false },
            title = { Text("Capture Community media") },
            text = { Text("Choose whether to capture a photo or video. Videos are limited to 3 minutes and 20 MB after preparation.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingCameraUri = createCaptureUri(".jpg")
                    cameraChooserOpen = false
                    cameraPhoto.launch(requireNotNull(pendingCameraUri))
                }) { Text("Take photo") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        pendingCameraUri = createCaptureUri(".mp4")
                        cameraChooserOpen = false
                        cameraVideo.launch(requireNotNull(pendingCameraUri))
                    }) { Text("Record video") }
                    TextButton(onClick = { cameraChooserOpen = false }) { Text("Cancel") }
                }
            },
        )
    }

    if (leaveConfirmationOpen) {
        UnsavedWorkDialog(
            onKeepEditing = { leaveConfirmationOpen = false },
            onSaveDraft = { onSaveDraft(composer.body); leaveConfirmationOpen = false; onDismiss() },
            onDiscardChanges = { onDiscardDraft(); leaveConfirmationOpen = false; onDismiss() },
        )
    }
}

@Composable
internal fun UnsavedWorkDialog(onKeepEditing: () -> Unit, onSaveDraft: () -> Unit, onDiscardChanges: () -> Unit) {
    AlertDialog(
        onDismissRequest = onKeepEditing,
        title = { Text("Keep this work?") },
        text = { Text("You have unsaved changes. You can keep editing, save a local draft for later, or discard the changes.") },
        confirmButton = { Button(onClick = onSaveDraft) { Text("Save draft") } },
        dismissButton = {
            Row {
                TextButton(onClick = onKeepEditing) { Text("Keep editing") }
                TextButton(onClick = onDiscardChanges) { Text("Discard changes") }
            }
        }
    )
}
