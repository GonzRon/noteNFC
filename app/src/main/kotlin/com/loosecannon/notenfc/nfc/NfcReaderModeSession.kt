package com.loosecannon.notenfc.nfc

import android.app.Activity
import android.nfc.NfcAdapter
import android.nfc.Tag

/**
 * Reader mode for in-app scanning and writing (D3 §9): callback-based, no PendingIntent, no
 * activity relaunch. `FLAG_READER_SKIP_NDEF_CHECK` keeps the platform from reading the tag for us
 * so the writer sees exactly what is there. Start in `onResume`, stop in `onPause`.
 */
class NfcReaderModeSession(private val activity: Activity, private val onTag: (Tag) -> Unit) {
    private val adapter: NfcAdapter? = NfcAdapter.getDefaultAdapter(activity)

    val available: Boolean get() = adapter != null
    val enabled: Boolean get() = adapter?.isEnabled == true

    fun start() {
        adapter?.enableReaderMode(activity, { tag -> onTag(tag) }, FLAGS, null)
    }

    fun stop() {
        adapter?.disableReaderMode(activity)
    }

    private companion object {
        const val FLAGS = NfcAdapter.FLAG_READER_NFC_A or
            NfcAdapter.FLAG_READER_NFC_B or
            NfcAdapter.FLAG_READER_NFC_F or
            NfcAdapter.FLAG_READER_NFC_V or
            NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK
    }
}
