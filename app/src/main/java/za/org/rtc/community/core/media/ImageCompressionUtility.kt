package za.org.rtc.community.core.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Image compression utility to optimize user-uploaded photos before uploading to Supabase,
 * ensuring fast load times in the community feeds and minimizing bandwidth usage.
 */
@Singleton
class ImageCompressionUtility @Inject constructor() {

    enum class CompressionPreset(
        val maxDimension: Int,
        val targetMaxBytes: Long,
        val initialQuality: Int,
        val minQuality: Int
    ) {
        FEED_IMAGE(maxDimension = 1920, targetMaxBytes = 750 * 1024L, initialQuality = 85, minQuality = 60),
        THUMBNAIL(maxDimension = 600, targetMaxBytes = 150 * 1024L, initialQuality = 80, minQuality = 55),
        AVATAR(maxDimension = 512, targetMaxBytes = 200 * 1024L, initialQuality = 85, minQuality = 65),
        HIGH_QUALITY_EVIDENCE(maxDimension = 2048, targetMaxBytes = 1500 * 1024L, initialQuality = 90, minQuality = 70)
    }

    data class CompressionResult(
        val file: File,
        val originalByteSize: Long,
        val compressedByteSize: Long,
        val width: Int,
        val height: Int,
        val mimeType: String = "image/jpeg"
    ) {
        val compressionRatioPercent: Int
            get() = if (originalByteSize > 0) {
                ((1.0 - (compressedByteSize.toDouble() / originalByteSize.toDouble())) * 100).toInt().coerceAtLeast(0)
            } else 0
    }

    /**
     * Compress an image [Uri] according to the specified [preset] and save to a staging file.
     */
    suspend fun compressUriToStagingFile(
        context: Context,
        uri: Uri,
        preset: CompressionPreset = CompressionPreset.FEED_IMAGE,
        prefix: String = "community_photo"
    ): CompressionResult = withContext(Dispatchers.IO) {
        val originalSize = queryUriSize(context, uri) ?: 0L

        // Decode bounds to check dimension
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, bounds)
        } ?: error("The selected image could not be opened.")

        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Invalid image dimensions." }

        // Calculate sample size for initial downscaling
        var sampleSize = 1
        while (bounds.outWidth / sampleSize > preset.maxDimension * 1.5 ||
            bounds.outHeight / sampleSize > preset.maxDimension * 1.5
        ) {
            sampleSize *= 2
        }

        val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val decodedBitmap = context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, decodeOptions)
        } ?: error("Failed to decode image content.")

        // Normalize EXIF orientation
        val orientation = context.contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
            ExifInterface(descriptor.fileDescriptor).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        } ?: ExifInterface.ORIENTATION_NORMAL

        var workingBitmap = rotateAndFlip(decodedBitmap, orientation)
        if (workingBitmap !== decodedBitmap) {
            decodedBitmap.recycle()
        }

        // Scale to maximum dimension while preserving aspect ratio
        val maxDim = maxOf(workingBitmap.width, workingBitmap.height)
        if (maxDim > preset.maxDimension) {
            val scaleRatio = preset.maxDimension.toFloat() / maxDim.toFloat()
            val scaledWidth = (workingBitmap.width * scaleRatio).toInt().coerceAtLeast(1)
            val scaledHeight = (workingBitmap.height * scaleRatio).toInt().coerceAtLeast(1)
            val scaledBitmap = Bitmap.createScaledBitmap(workingBitmap, scaledWidth, scaledHeight, true)
            if (scaledBitmap !== workingBitmap) {
                workingBitmap.recycle()
            }
            workingBitmap = scaledBitmap
        }

        // Iterative quality compression to meet target file size limit
        var currentQuality = preset.initialQuality
        val outputStream = ByteArrayOutputStream()
        var compressedBytes: ByteArray

        do {
            outputStream.reset()
            workingBitmap.compress(Bitmap.CompressFormat.JPEG, currentQuality, outputStream)
            compressedBytes = outputStream.toByteArray()
            if (compressedBytes.size <= preset.targetMaxBytes || currentQuality <= preset.minQuality) {
                break
            }
            currentQuality -= 8
        } while (currentQuality >= preset.minQuality)

        val finalWidth = workingBitmap.width
        val finalHeight = workingBitmap.height
        workingBitmap.recycle()

        // Save to cache directory
        val stagingDir = File(context.cacheDir, "rtc_compressed_photos").apply { mkdirs() }
        val outputFile = File.createTempFile("${prefix}_compressed_", ".jpg", stagingDir)
        FileOutputStream(outputFile).use { fos ->
            fos.write(compressedBytes)
        }

        CompressionResult(
            file = outputFile,
            originalByteSize = if (originalSize > 0) originalSize else compressedBytes.size.toLong(),
            compressedByteSize = outputFile.length(),
            width = finalWidth,
            height = finalHeight,
            mimeType = "image/jpeg"
        )
    }

    /**
     * Compress bitmap directly to JPEG byte array optimized for upload.
     */
    fun compressBitmapToJpegBytes(
        bitmap: Bitmap,
        preset: CompressionPreset = CompressionPreset.AVATAR
    ): ByteArray {
        var workingBitmap = bitmap
        val maxDim = maxOf(workingBitmap.width, workingBitmap.height)
        if (maxDim > preset.maxDimension) {
            val scale = preset.maxDimension.toFloat() / maxDim
            val scaled = Bitmap.createScaledBitmap(
                workingBitmap,
                (workingBitmap.width * scale).toInt().coerceAtLeast(1),
                (workingBitmap.height * scale).toInt().coerceAtLeast(1),
                true
            )
            workingBitmap = scaled
        }

        var quality = preset.initialQuality
        val stream = ByteArrayOutputStream()
        var bytes: ByteArray

        do {
            stream.reset()
            workingBitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
            bytes = stream.toByteArray()
            if (bytes.size <= preset.targetMaxBytes || quality <= preset.minQuality) {
                break
            }
            quality -= 10
        } while (quality >= preset.minQuality)

        if (workingBitmap !== bitmap) {
            workingBitmap.recycle()
        }

        return bytes
    }

    private fun queryUriSize(context: Context, uri: Uri): Long? {
        return context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                if (index >= 0) cursor.getLong(index) else null
            } else null
        }
    }

    private fun rotateAndFlip(bitmap: Bitmap, orientation: Int): Bitmap {
        if (orientation == ExifInterface.ORIENTATION_NORMAL || orientation == ExifInterface.ORIENTATION_UNDEFINED) {
            return bitmap
        }
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
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}
