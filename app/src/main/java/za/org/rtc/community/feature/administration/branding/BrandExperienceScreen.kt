package za.org.rtc.community.feature.administration.branding

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import za.org.rtc.community.data.ui_config.UiConfigurationRepository
import za.org.rtc.community.ui.components.RtcCard
import za.org.rtc.community.ui.components.RtcScreenScaffold
import za.org.rtc.community.ui.components.RtcSectionHeader
import za.org.rtc.community.ui.theme.RtcContentDensity
import za.org.rtc.community.ui.theme.RtcSpacing

enum class BrandEditorSection(val label: String) {
    DESIGN("Design"),
    SCREENS("Screens"),
    HOME("Home"),
    PREVIEW("Preview"),
    PUBLISH("Publish"),
    HISTORY("History"),
}

@Composable
fun BrandExperienceScreen(
    onBack: () -> Unit,
    viewModel: BrandExperienceViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val resolver = context.contentResolver
        scope.launch {
            val selection = try {
                withContext(Dispatchers.IO) {
                    val mimeType = resolver.getType(uri)
                        ?.takeIf { it in UiConfigurationRepository.ALLOWED_UI_IMAGE_TYPES }
                        ?: return@withContext null
                    val bytes = resolver.openInputStream(uri)?.use { input ->
                        readUiImageBytesCapped(input, UiConfigurationRepository.MAX_UI_IMAGE_BYTES)
                    } ?: return@withContext null
                    val dimensions = decodeImageDimensions(bytes)
                    if (dimensions.first <= 0 || dimensions.second <= 0) return@withContext null
                    UiImageSelection(bytes, mimeType, dimensions.first, dimensions.second)
                }
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                null
            }
            if (selection == null) {
                viewModel.reportImageSelectionFailure("Select a JPEG, PNG or WebP image no larger than 8 MiB with valid dimensions.")
            } else {
                viewModel.uploadHomeImage(
                    selection.bytes,
                    selection.mimeType,
                    selection.width,
                    selection.height,
                )
            }
        }
    }
    var section by remember { androidx.compose.runtime.mutableStateOf(BrandEditorSection.DESIGN) }

    RtcScreenScaffold(density = RtcContentDensity.ADMIN_COMPACT) {
        item {
            RtcSectionHeader(
                title = "Brand & Experience",
                subtitle = "Configure a bounded, accessible application experience. Server authorization remains authoritative.",
            )
        }
        item {
            RtcCard(protected = true) {
                Text(
                    text = if (state.authorized) "Verified administrator workspace" else "Protected workspace",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(state.authorizationMessage, style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(RtcSpacing.compact)) {
                    Button(onClick = onBack) { Text("Back") }
                    if (state.authorized) {
                        Button(onClick = viewModel::preview, enabled = !state.mutationInFlight) {
                            Text("Refresh preview")
                        }
                    }
                }
            }
        }
        if (state.authorized) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(RtcSpacing.compact),
                ) {
                    BrandEditorSection.entries.take(3).forEach { candidate ->
                        AssistChip(onClick = { section = candidate }, label = { Text(candidate.label) })
                    }
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(RtcSpacing.compact),
                ) {
                    BrandEditorSection.entries.drop(3).forEach { candidate ->
                        AssistChip(onClick = { section = candidate }, label = { Text(candidate.label) })
                    }
                }
            }
            when (section) {
                BrandEditorSection.DESIGN -> brandDesignSection(state, viewModel)
                BrandEditorSection.SCREENS -> brandScreensSection(state, viewModel)
                BrandEditorSection.HOME -> brandHomeSection(
                    state = state,
                    viewModel = viewModel,
                    onPickImage = {
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                )
                BrandEditorSection.PREVIEW -> brandPreviewSection(state)
                BrandEditorSection.PUBLISH -> brandPublishSection(state, viewModel)
                BrandEditorSection.HISTORY -> brandHistorySection(state, viewModel)
            }
        } else {
            item {
                RtcCard(protected = true) {
                    Text(
                        "Editing controls remain unavailable until the live System Administrator session and MFA requirements are satisfied.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

private fun decodeImageDimensions(bytes: ByteArray): Pair<Int, Int> {
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    return options.outWidth to options.outHeight
}

private data class UiImageSelection(
    val bytes: ByteArray,
    val mimeType: String,
    val width: Int,
    val height: Int,
)

internal fun readUiImageBytesCapped(input: InputStream, maxBytes: Int): ByteArray? {
    require(maxBytes > 0)
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8 * 1024)
    while (true) {
        val count = input.read(buffer)
        if (count < 0) return output.toByteArray()
        if (count == 0) continue
        if (output.size() > maxBytes - count) return null
        output.write(buffer, 0, count)
    }
}
