package com.loosecannon.notenfc.ui.interim

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.loosecannon.notenfc.NoteNfcApp
import com.loosecannon.notenfc.core.links.LinkCheck
import com.loosecannon.notenfc.core.links.LinkLaunchPolicy
import com.loosecannon.notenfc.core.model.TagTarget
import com.loosecannon.notenfc.di.AppGraph
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Interim (Phase 1B) share-sheet entry: `ACTION_SEND text/plain` → first URI → policy → standalone
 * link → write screen, then back to the caller (the original one-tap flow, D13 §2 last row).
 * Phase 1C replaces this with the link card (D6 §8 / #6).
 */
class ShareLinkActivity : Activity() {

    private val scope = MainScope()
    private val graph: AppGraph get() = (application as NoteNfcApp).graph

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // A CharSequence, not a String: apps that share styled text put a `Spanned` in here.
        val text = if (intent.action == Intent.ACTION_SEND) intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString() else null
        val uri = LinkLaunchPolicy.extractUri(text)
        if (uri == null) {
            Toast.makeText(this, "No link found in the shared text.", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        when (val check = LinkLaunchPolicy.check(uri)) {
            is LinkCheck.Rejected -> {
                Toast.makeText(this, "This link can't be saved: ${check.reason}", Toast.LENGTH_LONG).show()
                finish()
            }
            is LinkCheck.Accepted -> save(check.uri, labelFrom(text, check.uri), confirmedOther = false)
            is LinkCheck.NeedsConfirmation -> AlertDialog.Builder(this)
                .setTitle("Unknown link type")
                .setMessage("'${check.scheme}' links are not in noteNFC's list. It will open with whatever app claims that scheme. Save it anyway?\n\n${check.uri}")
                .setPositiveButton("Save") { _, _ -> save(check.uri, labelFrom(text, check.uri), confirmedOther = true) }
                .setNegativeButton(android.R.string.cancel) { _, _ -> finish() }
                .setOnCancelListener { finish() }
                .show()
        }
    }

    override fun onDestroy() { scope.cancel(); super.onDestroy() }

    private fun save(uri: String, label: String?, confirmedOther: Boolean) {
        scope.launch {
            try {
                val link = graph.saveLink.run(uri, label, confirmedOther)
                WriteTagActivity.start(this@ShareLinkActivity, TagTarget.LinkTarget(link.id), link.label)
            } catch (e: Exception) {
                Toast.makeText(this@ShareLinkActivity, "Could not save the link: ${e.message}", Toast.LENGTH_LONG).show()
            }
            finish()
        }
    }

    /** The first line of the shared text that is not the URI itself, e.g. a note title. */
    private fun labelFrom(text: String?, uri: String): String? =
        text?.lineSequence()?.map { it.trim() }?.firstOrNull { it.isNotEmpty() && !it.contains(uri) }?.take(80)
}
