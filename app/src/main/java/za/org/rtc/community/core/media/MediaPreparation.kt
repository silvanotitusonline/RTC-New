package za.org.rtc.community.core.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.annotation.OptIn
import androidx.exifinterface.media.ExifInterface
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import za.org.rtc.community.core.MediaKind

@Singleton
class MediaPreparation @Inject constructor(
    @ApplicationContext private val context: Context,
    private val compressor: ImageCompressionUtility = ImageCompressionUtility(),
) {
    data class Prepared(
        val file: File,
        val kind: MediaKind,
        val mimeType: String,
        val byteSize: Long,
        val width: Int? = null,
        val height: Int? = null,
        val durationSeconds: Int? = null,
    )

    suspend fun prepare(uri: Uri): Prepared {
        val mime = withContext(Dispatchers.IO) {
            context.contentResolver.getType(uri)?.lowercase() ?: run {
                val path = uri.path?.lowercase() ?: ""
                when {
                    path.endsWith(".jpg") || path.endsWith(".jpeg") -> "image/jpeg"
                    path.endsWith(".png") -> "image/png"
                    path.endsWith(".webp") -> "image/webp"
                    path.endsWith(".mp4") -> "video/mp4"
                    path.endsWith(".webm") -> "video/webm"
                    else -> "image/jpeg"
                }
            }
        }
        return when {
            mime in IMAGE_TYPES -> prepareImage(uri)
            mime in VIDEO_TYPES -> prepareVideo(uri, mime)
            else -> error("Unsupported media type: $mime")
        }
    }

    private suspend fun prepareImage(uri: Uri): Prepared = withContext(Dispatchers.IO) {
        val compressed = compressor.compressUriToStagingFile(
            context = context,
            uri = uri,
            preset = ImageCompressionUtility.CompressionPreset.FEED_IMAGE,
            prefix = "rtc_community_feed"
        )
        Prepared(
            file = compressed.file,
            kind = MediaKind.IMAGE,
            mimeType = compressed.mimeType,
            byteSize = compressed.compressedByteSize,
            width = compressed.width,
            height = compressed.height
        )
    }

    @OptIn(markerClass = [UnstableApi::class])
    private suspend fun prepareVideo(uri: Uri, originalMime: String): Prepared {
        val duration = withContext(Dispatchers.IO) { videoDurationSeconds(uri) }
        require(duration in 1..MAX_VIDEO_SECONDS) { "Videos must be between 1 second and 3 minutes long." }
        val knownSize = withContext(Dispatchers.IO) { querySize(uri) }
        val original = if (knownSize != null && knownSize <= MAX_VIDEO_BYTES) {
            withContext(Dispatchers.IO) { copyUri(uri, extensionFor(originalMime), MAX_VIDEO_BYTES) }
        } else {
            null
        }
        val prepared = original ?: transcodeWithinLimit(uri)
        val mime = if (original != null) originalMime else "video/mp4"
        require(prepared.length() in 1..MAX_VIDEO_BYTES) { prepared.delete(); "The video cannot be prepared below 20 MB." }
        return Prepared(prepared, MediaKind.VIDEO, mime, prepared.length(), durationSeconds = duration)
    }

    private fun querySize(uri: Uri): Long? = context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getLong(0).takeIf { it >= 0 } else null
    }

    private fun copyUri(uri: Uri, extension: String, limit: Long): File {
        val out = stagingFile("media", extension)
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(out).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var total = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > limit) throw IllegalArgumentException("Selected media is larger than the allowed upload size.")
                        output.write(buffer, 0, read)
                    }
                }
            } ?: error("The selected media could not be read.")
            return out
        } catch (t: Throwable) { out.delete(); throw t }
    }

    @OptIn(markerClass = [UnstableApi::class])
    private suspend fun transcodeWithinLimit(uri: Uri): File {
        val first = transform(uri, 720)
        if (first.length() <= MAX_VIDEO_BYTES) return first
        first.delete()
        val second = transform(uri, 480)
        if (second.length() <= MAX_VIDEO_BYTES) return second
        second.delete()
        error("The selected video cannot be compressed below 20 MB on this device.")
    }

    @OptIn(markerClass = [UnstableApi::class])
    private suspend fun transform(uri: Uri, height: Int): File {
        val output = withContext(Dispatchers.IO) { stagingFile("video", ".mp4") }
        val transformer = Transformer.Builder(context).setVideoMimeType(MimeTypes.VIDEO_H264).build()
        val edited = EditedMediaItem.Builder(MediaItem.fromUri(uri))
            .setEffects(Effects(emptyList(), listOf(Presentation.createForHeight(height))))
            .build()
        try {
            suspendCancellableCoroutine<Unit> { continuation ->
                transformer.addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: androidx.media3.transformer.Composition, exportResult: ExportResult) {
                        if (continuation.isActive) continuation.resume(Unit)
                    }
                    override fun onError(composition: androidx.media3.transformer.Composition, exportResult: ExportResult, exportException: ExportException) {
                        if (continuation.isActive) continuation.resumeWithException(exportException)
                    }
                })
                continuation.invokeOnCancellation { transformer.cancel(); output.delete() }
                transformer.start(edited, output.absolutePath)
            }
            check(output.exists() && output.length() > 0) { "Video preparation produced no output." }
            return output
        } catch (t: Throwable) { output.delete(); throw t }
    }

    private fun videoDurationSeconds(uri: Uri): Int {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val ms = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            ((ms + 999) / 1000).toInt()
        } finally { retriever.release() }
    }

    private fun stagingFile(prefix: String, extension: String): File = File(context.cacheDir, "rtc_upload_outbox").let { dir ->
        dir.mkdirs(); File.createTempFile("${prefix}_", extension, dir)
    }

    private fun extensionFor(mime: String) = when (mime) { "video/webm" -> ".webm"; else -> ".mp4" }

    private fun Bitmap.normalizedForExif(orientation: Int): Bitmap {
        if (orientation == ExifInterface.ORIENTATION_NORMAL || orientation == ExifInterface.ORIENTATION_UNDEFINED) return this
        val matrix = Matrix().apply {
            when (orientation) {
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> preScale(-1f, 1f)
                ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> preScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> { preScale(-1f, 1f); postRotate(270f) }
                ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
                ExifInterface.ORIENTATION_TRANSVERSE -> { preScale(-1f, 1f); postRotate(90f) }
                ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(270f)
            }
        }
        return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
    }

    private companion object {
        const val MAX_IMAGE_BYTES = 5L * 1024 * 1024
        const val MAX_VIDEO_BYTES = 20L * 1024 * 1024
        const val MAX_VIDEO_SECONDS = 180
        val IMAGE_TYPES = setOf("image/jpeg", "image/png", "image/webp")
        val VIDEO_TYPES = setOf("video/mp4", "video/webm")
    }
}
