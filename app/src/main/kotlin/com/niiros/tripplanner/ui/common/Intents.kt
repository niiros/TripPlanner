package com.niiros.tripplanner.ui.common

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.niiros.tripplanner.R
import com.niiros.tripplanner.core.model.GeoPoint
import java.util.Locale

object Intents {
    private const val GOOGLE_MAPS_PACKAGE = "com.google.android.apps.maps"

    /** Opens turn-by-turn navigation in Google Maps or Waze; falls back to the web link. */
    fun navigate(context: Context, point: GeoPoint, waze: Boolean) {
        val ll = String.format(Locale.ROOT, "%.6f,%.6f", point.lat, point.lng)
        val primary: Intent
        val fallback: Uri
        if (waze) {
            // Universal link: opens Waze when installed, otherwise the browser.
            val uri = Uri.parse("https://waze.com/ul?ll=$ll&navigate=yes")
            primary = Intent(Intent.ACTION_VIEW, uri).setPackage("com.waze")
            fallback = uri
        } else {
            primary = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=$ll&mode=d")).setPackage(GOOGLE_MAPS_PACKAGE)
            fallback = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$ll&travelmode=driving")
        }
        try {
            context.startActivity(primary.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, R.string.nav_no_app, Toast.LENGTH_SHORT).show()
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, fallback).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (_: ActivityNotFoundException) {
                // No browser either: nothing more to do.
            }
        }
    }

    fun shareText(context: Context, text: String) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        context.startActivity(Intent.createChooser(send, context.getString(R.string.share_chooser)))
    }
}
