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
class MediaPreparation @Inject constructor(@ApplicationContext private val context: Context) {
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
        val mime = withContext(Dispatchers.IO) { resolveMimeType(uri) }
        return when {
            mime in IMAGE_TYPES -> withContext(Dispatchers.IO) { prepareImage(uri) }
            mime in VIDEO_TYPES -> prepareVideo(uri, mime)
            else -> throw IllegalArgumentException("Choose a supported JPEG, PNG, WebP, MP4, or WebM file.")
        }
    }

    private fun resolveMimeType(uri: Uri): String {
        normalizeMime(context.contentResolver.getType(uri))?.let { mime ->
            if (mime in IMAGE_TYPES || mime in VIDEO_TYPES) return mime
        }

        queryDisplayName(uri)?.lowercase()?.let { name ->
            mimeFromName(name)?.let { return it }
        }

        sniffMimeType(uri)?.let { return it }
        throw IllegalArgumentException("The selected media type could not be identified. Choose a JPEG, PNG, WebP, MP4, or WebM file.")
    }

    private fun normalizeMime(raw: String?): String? = when (raw?.trim()?.lowercase()) {
        null, "", "application/octet-stream", "binary/octet-stream" -> null
        "image/jpg", "image/pjpeg" -> "image/jpeg"
        "image/x-png" -> "image/png"
        else -> raw.trim().lowercase()
    }

    private fun queryDisplayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()

    private fun mimeFromName(name: String): String? = when {
        name.endsWith(".jpg") || name.endsWith(".jpeg") -> "image/jpeg"
        name.endsWith(".png") -> "image/png"
        name.endsWith(".webp") -> "image/webp"
        name.endsWith(".mp4") || name.endsWith(".m4v") -> "video/mp4"
        name.endsWith(".webm") -> "video/webm"
        else -> null
    }

    private fun sniffMimeType(uri: Uri): String? = runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val header = ByteArray(16)
            val count = input.read(header)
            if (count < 4) return@use null
            when {
                header[0].toInt() and 0xFF == 0xFF &&
                    header[1].toInt() and 0xFF == 0xD8 &&
                    header[2].toInt() and 0xFF == 0xFF -> "image/jpeg"
                count >= 8 && header.copyOfRange(0, 8).contentEquals(PNG_SIGNATURE) -> "image/png"
                count >= 12 && ascii(header, 0, 4) == "RIFF" && ascii(header, 8, 12) == "WEBP" -> "image/webp"
                count >= 8 && ascii(header, 4, 8) == "ftyp" -> "video/mp4"
                header[0].toInt() and 0xFF == 0x1A &&
                    header[1].toInt() and 0xFF == 0x45 &&
                    header[2].toInt() and 0xFF == 0xDF &&
                    header[3].toInt() and 0xFF == 0xA3 -> "video/webm"
                else -> null
            }
        }
    }.getOrNull()

    private fun ascii(bytes: ByteArray, start: Int, end: Int): String =
        bytes.copyOfRange(start, end).toString(Charsets.US_ASCII)

    private fun prepareImage(uri: Uri): Prepared {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            ?: error("The selected image could not be read.")
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "The selected image could not be decoded." }

        var sample = 1
        while (bounds.outWidth / sample > 3072 || bounds.outHeight / sample > 3072) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val source = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: error("The selected image could not be decoded.")

        val orientation = readExifOrientation(uri)
        var normalized = source.normalizedForExif(orientation)
        if (normalized !== source) source.recycle()
        if (maxOf(normalized.width, normalized.height) > 2048) {
            val ratio = 2048f / maxOf(normalized.width, normalized.height)
            val scaled = Bitmap.createScaledBitmap(
                normalized,
                (normalized.width * ratio).toInt().coerceAtLeast(1),
                (normalized.height * ratio).toInt().coerceAtLeast(1),
                true,
            )
            if (scaled !== normalized) normalized.recycle()
            normalized = scaled
        }

        val encoding = preparedImageEncoding(normalized.hasAlpha())
        val out = stagingFile("image", encoding.extension)
        try {
            FileOutputStream(out).use { stream ->
                val format = if (encoding == PreparedImageEncoding.PNG) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
                val quality = if (encoding == PreparedImageEncoding.PNG) 100 else 88
                check(normalized.compress(format, quality, stream)) { "The selected image could not be encoded." }
            }
            val width = normalized.width
            val height = normalized.height
            require(out.length() in 1..MAX_IMAGE_BYTES) { "Prepared image exceeds 5 MB." }
            return Prepared(out, MediaKind.IMAGE, encoding.mimeType, out.length(), width, height)
        } catch (t: Throwable) {
            out.delete()
            throw t
        } finally {
            normalized.recycle()
        }
    }

    private fun readExifOrientation(uri: Uri): Int = runCatching {
        context.contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
            ExifInterface(descriptor.fileDescriptor).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        } ?: ExifInterface.ORIENTATION_NORMAL
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

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

    private fun querySize(uri: Uri): Long? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getLong(0).takeIf { it >= 0 } else null
        }
    }.getOrNull()

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
        } catch (t: Throwable) {
            out.delete()
            throw t
        }
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

                    override fun onError(
                        composition: androidx.media3.transformer.Composition,
                        exportResult: ExportResult,
                        exportException: ExportException,
                    ) {
                        if (continuation.isActive) continuation.resumeWithException(exportException)
                    }
                })
                continuation.invokeOnCancellation {
                    transformer.cancel()
                    output.delete()
                }
                transformer.start(edited, output.absolutePath)
            }
            check(output.exists() && output.length() > 0) { "Video preparation produced no output." }
            return output
        } catch (t: Throwable) {
            output.delete()
            throw t
        }
    }

    private fun videoDurationSeconds(uri: Uri): Int {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val ms = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            ((ms + 999) / 1000).toInt()
        } finally {
            retriever.release()
        }
    }

    private fun stagingFile(prefix: String, extension: String): File = File(context.cacheDir, "rtc_upload_outbox").let { dir ->
        dir.mkdirs()
        File.createTempFile("${prefix}_", extension, dir)
    }

    private fun extensionFor(mime: String) = when (mime) {
        "video/webm" -> ".webm"
        else -> ".mp4"
    }

    private fun Bitmap.normalizedForExif(orientation: Int): Bitmap {
        if (orientation == ExifInterface.ORIENTATION_NORMAL || orientation == ExifInterface.ORIENTATION_UNDEFINED) return this
        val matrix = Matrix().apply {
            when (orientation) {
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> preScale(-1f, 1f)
                ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> preScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> {
                    preScale(-1f, 1f)
                    postRotate(270f)
                }
                ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
                ExifInterface.ORIENTATION_TRANSVERSE -> {
                    preScale(-1f, 1f)
                    postRotate(90f)
                }
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
        val PNG_SIGNATURE = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
        )
    }
}
