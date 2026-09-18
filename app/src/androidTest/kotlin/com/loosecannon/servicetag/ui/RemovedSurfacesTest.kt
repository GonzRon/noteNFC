package com.loosecannon.servicetag.ui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 2.6 — the surfaces that are gone, asked of the platform. A grep proves the source tree; this
 * proves the manifest the APK actually shipped, which is what decides whether a share sheet or a
 * `servicetag://link/…` URI can reach ServiceTag at all. The asset host is asserted in the same
 * breath so a manifest that lost everything fails as loudly as one that kept the link host.
 */
@RunWith(AndroidJUnit4::class)
class RemovedSurfacesTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun handlersHere(intent: Intent): Int =
        context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            .count { it.activityInfo.packageName == context.packageName }

    @Test fun nothingInThisPackageAcceptsSharedText() {
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, "https://example.invalid/x")
        assertEquals(0, handlersHere(send))
    }

    @Test fun theLinkHostIsUnregisteredAndTheAssetHostStillIsNot() {
        val id = "123e4567-e89b-12d3-a456-426614174000"
        assertEquals(0, handlersHere(Intent(Intent.ACTION_VIEW, Uri.parse("servicetag://link/$id"))))
        assertEquals(1, handlersHere(Intent(Intent.ACTION_VIEW, Uri.parse("servicetag://asset/$id"))))
    }
}
