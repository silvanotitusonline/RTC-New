package za.org.rtc.community.ui.auth

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import java.security.MessageDigest

object GooglePlayServicesHelper {

    /**
     * Checks if Google Play Services is available and up-to-date on the device.
     */
    fun isPlayServicesAvailable(context: Context): Boolean {
        val googleApiAvailability = GoogleApiAvailability.getInstance()
        val resultCode = googleApiAvailability.isGooglePlayServicesAvailable(context)
        return resultCode == ConnectionResult.SUCCESS
    }

    /**
     * Gets the current result code string for logging or UI.
     */
    fun getPlayServicesStatusMessage(context: Context): String {
        val googleApiAvailability = GoogleApiAvailability.getInstance()
        val resultCode = googleApiAvailability.isGooglePlayServicesAvailable(context)
        return googleApiAvailability.getErrorString(resultCode)
    }

    /**
     * Extract active app signatures/fingerprints dynamically to simplify registration in Google Cloud Console.
     */
    fun getSigningFingerprints(context: Context): Map<String, String> {
        val fingerprints = mutableMapOf<String, String>()
        try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
            }
            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.signingInfo?.apkContentsSigners
            } else {
                @Suppress("DEPRECATION")
                packageInfo.signatures
            }
            signatures?.firstOrNull()?.let { signature ->
                val mdSha1 = MessageDigest.getInstance("SHA-1")
                val sha1Bytes = mdSha1.digest(signature.toByteArray())
                fingerprints["SHA-1"] = sha1Bytes.joinToString(":") { "%02X".format(it) }

                val mdSha256 = MessageDigest.getInstance("SHA-256")
                val sha256Bytes = mdSha256.digest(signature.toByteArray())
                fingerprints["SHA-256"] = sha256Bytes.joinToString(":") { "%02X".format(it) }
            }
        } catch (e: Exception) {
            fingerprints["Error"] = e.localizedMessage ?: "Failed to read signatures"
        }
        return fingerprints
    }
}
