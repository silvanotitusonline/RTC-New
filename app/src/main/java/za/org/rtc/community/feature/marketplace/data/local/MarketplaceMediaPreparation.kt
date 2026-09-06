package za.org.rtc.community.feature.marketplace.data.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
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
        val sourceMime = context.contentResolver.getType(uri)?.lowercase()
        require(sourceMime in setOf("image/jpeg", "image/png", "image/webp")) { "Choose a JPEG, PNG, or WebP image." }
        val sourceBytes = context.contentResolver.openAssetFileDescriptor(uri, "r")?.length
        require(sourceBytes == null || sourceBytes in 1..MAX_SOURCE_BYTES) { "Choose an image smaller than 10 MB." }
        val bitmap = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
            ?: error("The selected image could not be decoded.")
        require(bitmap.width > 0 && bitmap.height > 0) { "The selected image is invalid." }
        val scaled = if (maxOf(bitmap.width, bitmap.height) > MAX_DIMENSION) {
            val ratio = MAX_DIMENSION.toFloat() / maxOf(bitmap.width, bitmap.height)
            Bitmap.createScaledBitmap(bitmap, (bitmap.width * ratio).toInt(), (bitmap.height * ratio).toInt(), true).also { bitmap.recycle() }
        } else bitmap
        val transparent = scaled.hasAlpha() && sourceMime != "image/jpeg"
        val output = File(context.cacheDir, "rtc_upload_outbox").also { it.mkdirs() }.let { directory ->
            File.createTempFile("marketplace_image_", if (transparent) ".png" else ".jpg", directory)
        }
        try {
            FileOutputStream(output).use { stream ->
                check(scaled.compress(if (transparent) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, if (transparent) 100 else 88, stream))
            }
            require(output.length() in 1..MAX_OUTPUT_BYTES) { "Prepared image exceeds 5 MB." }
            return PreparedImage(output, if (transparent) "image/png" else "image/jpeg", output.length(), scaled.width, scaled.height)
        } catch (failure: Throwable) {
            output.delete()
            throw failure
        } finally {
            scaled.recycle()
        }
    }

    private companion object {
        const val MAX_SOURCE_BYTES = 10L * 1024 * 1024
        const val MAX_OUTPUT_BYTES = 5L * 1024 * 1024
        const val MAX_DIMENSION = 2048
    }
}
