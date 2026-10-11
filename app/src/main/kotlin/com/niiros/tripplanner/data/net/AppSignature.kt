package com.niiros.tripplanner.data.net

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import java.security.MessageDigest

/** SHA-1 of the APK signing certificate, as upper-case hex without separators (X-Android-Cert format). */
object AppSignature {

    @SuppressLint("PackageManagerGetSignatures")
    @Suppress("DEPRECATION")
    fun sha1(context: Context): String? = try {
        val pm = context.packageManager
        val signatures: Array<Signature>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo?.apkContentsSigners
        } else {
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures
        }
        signatures?.firstOrNull()?.let { sig ->
            MessageDigest.getInstance("SHA-1").digest(sig.toByteArray()).joinToString("") { "%02X".format(it) }
        }
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }
}
