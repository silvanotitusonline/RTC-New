package za.org.rtc.community.feature.marketplace.data.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/** Re-encodes owner-selected Marketplace images to remove embedded metadata and preserve alpha. */
@Singleton
class MarketplaceMediaPreparation @Inject constructor(@param:ApplicationContext private val context: Context) {
    data class PreparedImage(val file: File, val mimeType: String, val byteSize: Long, val width: Int, val height: Int)

    fun prepareImage(uri: Uri): PreparedImage {
        val sourceMime = resolveImageMime(uri)
        val sourceBytes = runCatching {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length.takeIf { size -> size >= 0 } }
        }.getOrNull()
        require(sourceBytes == null || sourceBytes in 1..MAX_SOURCE_BYTES) { "Choose an image smaller than 10 MB." }

        val bitmap = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
            ?: error("The selected image could not be decoded. Choose a valid JPEG, PNG, or WebP image.")
        require(bitmap.width > 0 && bitmap.height > 0) { "The selected image is invalid." }

        val scaled = if (maxOf(bitmap.width, bitmap.height) > MAX_DIMENSION) {
            val ratio = MAX_DIMENSION.toFloat() / maxOf(bitmap.width, bitmap.height)
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * ratio).toInt().coerceAtLeast(1),
                (bitmap.height * ratio).toInt().coerceAtLeast(1),
                true,
            ).also { if (it !== bitmap) bitmap.recycle() }
        } else bitmap

        val transparent = scaled.hasAlpha() && sourceMime != "image/jpeg"
        val output = File(context.cacheDir, "rtc_upload_outbox").also { it.mkdirs() }.let { directory ->
            File.createTempFile("marketplace_image_", if (transparent) ".png" else ".jpg", directory)
        }
        try {
            FileOutputStream(output).use { stream ->
                check(
                    scaled.compress(
                        if (transparent) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG,
                        if (transparent) 100 else 88,
                        stream,
                    ),
                ) { "The selected image could not be encoded." }
            }
            require(output.length() in 1..MAX_OUTPUT_BYTES) { "Prepared image exceeds 5 MB." }
            return PreparedImage(
                output,
                if (transparent) "image/png" else "image/jpeg",
                output.length(),
                scaled.width,
                scaled.height,
            )
        } catch (failure: Throwable) {
            output.delete()
            throw failure
        } finally {
            scaled.recycle()
        }
    }

    private fun resolveImageMime(uri: Uri): String {
        normalizeMime(context.contentResolver.getType(uri))?.let { if (it in IMAGE_TYPES) return it }
        queryDisplayName(uri)?.lowercase()?.let { name -> mimeFromName(name)?.let { return it } }
        sniffImageMime(uri)?.let { return it }
        throw IllegalArgumentException("Choose a valid JPEG, PNG, or WebP image.")
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
        else -> null
    }

    private fun sniffImageMime(uri: Uri): String? = runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val header = ByteArray(12)
            val count = input.read(header)
            when {
                count >= 3 && header[0].toInt() and 0xFF == 0xFF &&
                    header[1].toInt() and 0xFF == 0xD8 &&
                    header[2].toInt() and 0xFF == 0xFF -> "image/jpeg"
                count >= 8 && header.copyOfRange(0, 8).contentEquals(PNG_SIGNATURE) -> "image/png"
                count >= 12 && ascii(header, 0, 4) == "RIFF" && ascii(header, 8, 12) == "WEBP" -> "image/webp"
                else -> null
            }
        }
    }.getOrNull()

    private fun ascii(bytes: ByteArray, start: Int, end: Int): String =
        bytes.copyOfRange(start, end).toString(Charsets.US_ASCII)

    private companion object {
        const val MAX_SOURCE_BYTES = 10L * 1024 * 1024
        const val MAX_OUTPUT_BYTES = 5L * 1024 * 1024
        const val MAX_DIMENSION = 2048
        val IMAGE_TYPES = setOf("image/jpeg", "image/png", "image/webp")
        val PNG_SIGNATURE = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
        )
    }
}
