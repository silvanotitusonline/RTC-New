package za.org.rtc.remediation.media

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import za.org.rtc.remediation.model.PreparedImage
import java.io.File
import java.util.UUID

class PhotoPreparation(private val context: Context) {
    private val directory get() = File(context.filesDir,"rtc-outbox-media").apply { mkdirs() }
    /** Copy while the picker grant is valid. WorkManager never receives a content URI. */
    suspend fun prepare(uri: Uri): PreparedImage = withContext(Dispatchers.IO) {
        val target = File(directory, "${UUID.randomUUID()}.upload")
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64*1024)
                    var total = 0L
                    while (true) {
                        val size = input.read(buffer)
                        if (size < 0) break
                        total += size
                        require(total <= 10L*1024*1024) { "Choose a photo smaller than 10 MiB." }
                        output.write(buffer,0,size)
                    }
                }
            } ?: error("This photo could not be opened. Choose it again.")
            val options = BitmapFactory.Options().apply { inJustDecodeBounds=true }
            BitmapFactory.decodeFile(target.path,options)
            require(options.outWidth>0 && options.outHeight>0 && options.outWidth.toLong()*options.outHeight<=25_000_000) {
                "Choose a valid image up to 25 megapixels."
            }
            require(options.outMimeType in setOf("image/jpeg","image/png","image/webp")) {
                "Choose a JPEG, PNG or WebP photo."
            }
            PreparedImage(target.absolutePath, requireNotNull(options.outMimeType))
        } catch (error: Throwable) { target.delete(); throw error }
    }
    /** Use ActivityResultContracts.TakePicture(), not TakePicturePreview()'s thumbnail. */
    fun cameraDestination(): Uri {
        val file = File(directory,"capture-${UUID.randomUUID()}.jpg")
        check(file.createNewFile())
        return FileProvider.getUriForFile(context,"${context.packageName}.rtc.remediation.photos",file)
    }
    fun deleteCapture(uri: Uri) { context.contentResolver.delete(uri,null,null) }
    fun deletePrepared(image: PreparedImage) {
        val file=File(image.path).canonicalFile
        require(file.parentFile==directory.canonicalFile)
        file.delete()
    }
}
