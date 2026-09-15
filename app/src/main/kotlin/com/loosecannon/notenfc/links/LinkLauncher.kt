package com.loosecannon.notenfc.links

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/** Fires `ACTION_VIEW` for a URI that `OpenLink` has already checked; never crashes on a missing handler. */
object LinkLauncher {
    fun open(activity: Activity, uri: String): Boolean = try {
        activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)))
        true
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(activity, "No app can open this link:\n$uri", Toast.LENGTH_LONG).show()
        false
    }
}
