package com.loosecannon.notenfc.ui.interim

import android.app.Activity
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.os.Parcelable
import android.widget.Button
import android.widget.TextView
import com.loosecannon.notenfc.NoteNfcApp
import com.loosecannon.notenfc.R
import com.loosecannon.notenfc.di.AppGraph
import com.loosecannon.notenfc.nfc.NfcDispatchActivity
import com.loosecannon.notenfc.nfc.NfcReaderModeSession
import com.loosecannon.notenfc.nfc.TagWriter
import com.loosecannon.notenfc.nfc.toNdefMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Interim (Phase 1B) launcher screen: counts, in-app scan (reader mode → the same dispatch path a
 * background scan takes), and "write a new tag". Replaced by the Compose shell in Phase 1C.
 */
class TagToolsActivity : Activity() {

    private val scope = MainScope()
    private val graph: AppGraph get() = (application as NoteNfcApp).graph
    private lateinit var counts: TextView
    private lateinit var status: TextView
    private lateinit var session: NfcReaderModeSession
    @Volatile private var busy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tag_tools)
        counts = findViewById(R.id.tools_counts)
        status = findViewById(R.id.tools_status)
        session = NfcReaderModeSession(this) { tag -> onTag(tag) }
        findViewById<Button>(R.id.tools_write).setOnClickListener {
            TargetPicker.show(this, graph, scope, allowNone = true) { target -> WriteTagActivity.start(this, target) }
        }
        if (!session.available) status.text = "This phone has no NFC hardware."
    }

    override fun onResume() {
        super.onResume()
        session.start()
        if (session.available && !session.enabled) status.text = "NFC is turned off. Enable it in system settings."
        refreshCounts()
    }

    override fun onPause() { session.stop(); super.onPause() }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }

    /** Reads the tag ourselves (reader mode skips the platform NDEF read) and hands it to dispatch. */
    private fun onTag(tag: Tag) {
        if (busy) return
        busy = true
        scope.launch(Dispatchers.IO) {
            try {
                val records = TagWriter.inspect(tag)?.existingRecords.orEmpty()
                val intent = Intent(this@TagToolsActivity, NfcDispatchActivity::class.java)
                    .setAction(NfcAdapter.ACTION_NDEF_DISCOVERED)
                    .putExtra(NfcAdapter.EXTRA_TAG, tag)
                if (records.isNotEmpty()) {
                    intent.putExtra(NfcAdapter.EXTRA_NDEF_MESSAGES, arrayOf<Parcelable>(records.toNdefMessage()))
                }
                withContext(Dispatchers.Main) { startActivity(intent) }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { status.text = "Could not read the tag: ${e.message}" }
            } finally {
                busy = false
            }
        }
    }

    private fun refreshCounts() {
        scope.launch {
            counts.text = try {
                "${graph.assets.all().size} assets · ${graph.tags.all().size} tags · ${graph.links.all().size} links"
            } catch (e: Exception) {
                "counts unavailable: ${e.message}"
            }
        }
    }
}
