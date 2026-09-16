package com.loosecannon.servicetag.nfc

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.nfc.NfcAdapter
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The C9 binding, device half (target §4.8 test 2). It asks the platform the question the
 * platform will be asked by a real tag: who resolves `vnd.android.nfc://ext/<externalType>` for
 * `ACTION_NDEF_DISCOVERED`? That reads the MERGED manifest as installed, so it is the only test
 * that can catch a placeholder that resolved to the wrong string.
 *
 * Emulator only (no NFC hardware needed — this is a PackageManager query, not a scan).
 */
@RunWith(AndroidJUnit4::class)
class TagIdentityDispatchTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val graph get() = (context.applicationContext as com.loosecannon.servicetag.ServiceTagApp).graph

    @Test fun theAarPackageIsThisApplicationId() {
        assertEquals(context.packageName, graph.tagIdentity.aarPackage)
    }

    @Test fun ourExternalTypeResolvesToOurDispatchActivity() {
        val intent = Intent(NfcAdapter.ACTION_NDEF_DISCOVERED)
            .setData(Uri.parse("vnd.android.nfc://ext/${graph.tagIdentity.externalType}"))

        val matches = context.packageManager.queryIntentActivities(intent, 0)

        assertEquals("exactly one activity may claim our external type", 1, matches.size)
        val info = matches.single().activityInfo
        assertEquals(context.packageName, info.packageName)
        assertEquals(NfcDispatchActivity::class.java.name, info.name)
    }

    /** The retired identity belongs to nobody now (O3). */
    @Test fun theRetiredExternalTypeResolvesToNothingOfOurs() {
        val intent = Intent(NfcAdapter.ACTION_NDEF_DISCOVERED)
            .setData(Uri.parse("vnd.android.nfc://ext/com.loosecannon.notenfc:tag"))

        val ours = context.packageManager.queryIntentActivities(intent, 0)
            .filter { it.activityInfo.packageName == context.packageName }

        assertEquals("nothing of ours may still claim the retired type", 0, ours.size)
    }
}
