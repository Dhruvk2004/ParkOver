package com.dhruvk.parkover.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

/** Opens turn-by-turn directions in Google Maps (or any maps app / the browser). */
fun openDirections(context: Context, latitude: Double?, longitude: Double?, label: String) {
    val destination = if (latitude != null && longitude != null && (latitude != 0.0 || longitude != 0.0)) {
        "$latitude,$longitude"
    } else {
        label
    }
    val uri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=${Uri.encode(destination)}&travelmode=driving")
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
    } catch (e: ActivityNotFoundException) {
        // Nothing can open maps links; ignore
    }
}
