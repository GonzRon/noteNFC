package com.loosecannon.notenfc.ui.interim

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.nfc.Tag
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import android.widget.Toast
import com.loosecannon.notenfc.NoteNfcApp
import com.loosecannon.notenfc.R
import com.loosecannon.notenfc.core.model.AssetId
import com.loosecannon.notenfc.core.model.LinkId
import com.loosecannon.notenfc.core.model.TagBinding
import com.loosecannon.notenfc.core.model.TagTarget
import com.loosecannon.notenfc.core.nfc.NdefCodec
import com.loosecannon.notenfc.core.nfc.NdefRecordData
import com.loosecannon.notenfc.core.nfc.OverwriteDecision
import com.loosecannon.notenfc.core.nfc.OverwritePolicy
import com.loosecannon.notenfc.core.nfc.TagPayload
import com.loosecannon.notenfc.di.AppGraph
import com.loosecannon.notenfc.nfc.NfcReaderModeSession
import com.loosecannon.notenfc.nfc.TagInspection
import com.loosecannon.notenfc.nfc.TagWriter
import com.loosecannon.notenfc.nfc.WriteResult
import com.loosecannon.notenfc.nfc.toHexOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Interim (Phase 1B) writer screen — plain Views, replaced by Compose in Phase 1C. The workflow is
 * the one D3 §9 specifies: read first, confirm before overwriting anything but an empty tag or the
 * same id, check capacity, write off the main thread, read back and compare, optional lock.
 *
 * A row is provisioned on the first tap and reused for every retry on this screen; if the screen
 * closes before a verified write, the row is abandoned (deleted) so no phantom tag remains.
 */
class WriteTagActivity : Activity() {

    private val scope = MainScope()
    private val graph: AppGraph get() = (application as NoteNfcApp).graph
    private lateinit var status: TextView
    private lateinit var lock: CheckBox
    private lateinit var session: NfcReaderModeSession
    private lateinit var target: TagTarget
    private var label: String? = null

    @Volatile private var pending: TagBinding? = null
    @Volatile private var awaitingVerify = false

    /**
     * What the user agreed to overwrite. The `Tag` handle captured before the confirmation dialog
     * can go stale while the dialog is up (the NFC service re-discovers the tag and then refuses
     * the old handle with "Tag is out of date" — seen on an Android 17 phone), so a confirmation is
     * remembered as consent for *this content* and honoured on the next tap of a tag carrying it.
     */
    @Volatile private var confirmedOverwrite: TagPayload? = null
    @Volatile private var done = false
    @Volatile private var busy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_write_tag)
        status = findViewById(R.id.write_status)
        lock = findViewById(R.id.write_lock)
        findViewById<Button>(R.id.write_done).setOnClickListener { finish() }

        val t = targetFrom(intent)
        if (t == null) {
            Toast.makeText(this, "Nothing to write: no target given.", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        target = t
        label = intent.getStringExtra(EXTRA_LABEL)
        session = NfcReaderModeSession(this) { tag -> onTag(tag) }

        lock.setOnCheckedChangeListener { _, checked ->
            if (checked) {
                AlertDialog.Builder(this)
                    .setTitle("Lock permanently?")
                    .setMessage("A locked tag can never be rewritten or reused. Only lock tags that are installed for good.")
                    .setPositiveButton("Lock after writing", null)
                    .setNegativeButton("Don't lock") { _, _ -> lock.isChecked = false }
                    // Back press or a tap outside must not leave the box armed: dismissing the
                    // warning without accepting it is not consent to lock the tag for good.
                    .setOnCancelListener { lock.isChecked = false }
                    .show()
            }
        }

        status.text = when {
            !session.available -> "This phone has no NFC hardware."
            !session.enabled -> "NFC is turned off. Enable it in system settings, then come back."
            else -> "Hold a blank or reusable tag to the back of the phone.\n\nTarget: ${describe(target)}"
        }
    }

    override fun onResume() { super.onResume(); if (::session.isInitialized) session.start() }
    override fun onPause() { if (::session.isInitialized) session.stop(); super.onPause() }

    override fun onDestroy() {
        scope.cancel()
        val row = pending
        if (!done && row != null) graph.appScope.launch { graph.provisionTag.abandon(row.id) }
        super.onDestroy()
    }

    // --- reader-mode callback (binder thread) -----------------------------------------------

    private fun onTag(tag: Tag) {
        if (busy || done) return
        busy = true
        scope.launch(Dispatchers.IO) {
            var dialogOwnsBusy = false
            try {
                dialogOwnsBusy = handle(tag)
            } catch (e: Exception) {
                say("Failed: ${e.javaClass.simpleName}: ${e.message}\nHold the tag still and try again.")
            } finally {
                if (!dialogOwnsBusy) busy = false
            }
        }
    }

    /** Returns true when a confirmation dialog now owns the `busy` flag. */
    private suspend fun handle(tag: Tag): Boolean {
        val row = pending ?: graph.provisionTag.begin(target, label).also { pending = it }
        val intended = NdefCodec.encodeV1(row.id)
        val inspection = TagWriter.inspect(tag)
        if (inspection == null) {
            say("This tag does not support NDEF. Use an NTAG213/215/216 or similar.")
            return false
        }
        if (awaitingVerify) {
            verify(tag, inspection, intended, row)
            return false
        }
        if (!inspection.writable) {
            say("This tag is read-only (locked). Nothing written.")
            return false
        }
        val consent = confirmedOverwrite
        if (consent != null) {
            if (consent == inspection.existing) {
                // The user already agreed to replace exactly this content; this tap carries a
                // fresh handle, so the write can go ahead without asking twice.
                write(tag, intended, row)
                return false
            }
            confirmedOverwrite = null   // a different tag: the earlier consent does not carry over
        }
        return when (val d = OverwritePolicy.decide(inspection.existing, row.id)) {
            OverwriteDecision.Proceed -> { write(tag, intended, row); false }
            is OverwriteDecision.Confirm -> { confirm(d.reason, inspection.existing, tag, intended, row); true }
        }
    }

    private suspend fun write(tag: Tag, intended: List<NdefRecordData>, row: TagBinding) {
        val wantLock = withContext(Dispatchers.Main) { lock.isChecked }
        when (val r = TagWriter.write(tag, intended, lock = wantLock)) {
            is WriteResult.Written -> if (r.verified) {
                finishWrite(row, tag.id.toHexOrNull(), r.locked)
            } else {
                awaitingVerify = true
                say("Formatted and written (${r.bytes} bytes). Lift the tag off, then hold it again to verify the read-back.")
            }
            is WriteResult.TooSmall -> say("Tag too small: it holds ${r.maxSize} bytes, the message needs ${r.needed}.")
            WriteResult.ReadOnly -> say("This tag is read-only (locked). Nothing written.")
            WriteResult.Unsupported -> say("This tag does not support NDEF.")
            is WriteResult.VerifyMismatch -> say("Read-back differs from what was written. Nothing recorded — try again.")
            is WriteResult.Failed -> say(
                if (confirmedOverwrite != null) {
                    "Overwrite confirmed, but the write did not go through (${r.reason}).\nLift the tag off and hold it to the phone again to finish."
                } else {
                    "Write failed: ${r.reason}\nHold the tag still and try again."
                },
            )
        }
    }

    private suspend fun verify(tag: Tag, inspection: TagInspection, intended: List<NdefRecordData>, row: TagBinding) {
        if (inspection.existingRecords == intended) {
            // The format path writes unlocked; the lock only happens here, once the read-back
            // has proved the bytes on the tag are the ones we meant to put there.
            val wantLock = withContext(Dispatchers.Main) { lock.isChecked }
            val locked = if (wantLock && inspection.canLock && inspection.writable) {
                TagWriter.lock(tag)
            } else {
                !inspection.writable
            }
            finishWrite(row, inspection.uid, locked)
        } else {
            awaitingVerify = false
            say("Read-back differs: the tag holds ${describe(inspection.existing)}. Try writing again.")
        }
    }

    private suspend fun finishWrite(row: TagBinding, uid: String?, locked: Boolean) {
        val completed = graph.provisionTag.complete(row.id, uid)
        done = true
        say(
            "Written and read back byte-identical.\n\n" +
                "Tag id: ${completed.id.value}\nTarget: ${describe(target)}\nLocked: ${if (locked) "yes" else "no"}\n\n" +
                "Close the app and scan the tag to test dispatch.",
        )
    }

    private suspend fun confirm(reason: String, existing: TagPayload, tag: Tag, intended: List<NdefRecordData>, row: TagBinding) {
        withContext(Dispatchers.Main) {
            if (isFinishing || isDestroyed) { busy = false; return@withContext }
            AlertDialog.Builder(this@WriteTagActivity)
                .setTitle("Overwrite this tag?")
                .setMessage("The tag already holds $reason.\n\nChoose Overwrite to replace it; if the phone lost the tag while this was open, hold it to the phone again.")
                .setPositiveButton("Overwrite") { _, _ ->
                    confirmedOverwrite = existing
                    scope.launch(Dispatchers.IO) {
                        try {
                            write(tag, intended, row)
                        } catch (e: Exception) {
                            say("Overwrite confirmed, but the tag was lost (${e.message}). Hold it to the phone again to finish.")
                        } finally {
                            busy = false
                        }
                    }
                }
                .setNegativeButton("Keep it") { _, _ ->
                    confirmedOverwrite = null
                    busy = false
                    scope.launch { say("Not written. The tag was left as it was.\n\nTarget: ${describe(target)}") }
                }
                .setOnCancelListener { confirmedOverwrite = null; busy = false }
                .show()
        }
    }

    private suspend fun say(text: String) = withContext(Dispatchers.Main) { status.text = text }

    private fun describe(t: TagTarget): String = when (t) {
        is TagTarget.AssetTarget -> "asset ${t.assetId.value}" + (label?.let { " ($it)" } ?: "")
        is TagTarget.LinkTarget -> "link ${t.linkId.value}" + (label?.let { " ($it)" } ?: "")
        TagTarget.None -> "none yet (spare tag; bind it on first scan)"
    }

    private fun describe(p: TagPayload): String = when (p) {
        is TagPayload.V1 -> "noteNFC tag ${p.tagId.value}"
        is TagPayload.LegacyMd5 -> "legacy tag ${p.key}"
        is TagPayload.NewerVersion -> "a newer noteNFC format (${p.version})"
        is TagPayload.Foreign -> "foreign content (${p.description})"
        is TagPayload.Malformed -> "unreadable content (${p.reason})"
        TagPayload.Empty -> "nothing"
    }

    companion object {
        const val EXTRA_TARGET_KIND = "target_kind"
        const val EXTRA_TARGET_ID = "target_id"
        const val EXTRA_LABEL = "label"

        fun start(activity: Activity, target: TagTarget, label: String? = null) {
            val intent = Intent(activity, WriteTagActivity::class.java).putExtra(EXTRA_LABEL, label)
            when (target) {
                is TagTarget.AssetTarget -> intent.putExtra(EXTRA_TARGET_KIND, "asset").putExtra(EXTRA_TARGET_ID, target.assetId.value)
                is TagTarget.LinkTarget -> intent.putExtra(EXTRA_TARGET_KIND, "link").putExtra(EXTRA_TARGET_ID, target.linkId.value)
                TagTarget.None -> intent.putExtra(EXTRA_TARGET_KIND, "none")
            }
            activity.startActivity(intent)
        }

        fun targetFrom(intent: Intent): TagTarget? {
            val id = intent.getStringExtra(EXTRA_TARGET_ID)
            return when (intent.getStringExtra(EXTRA_TARGET_KIND)) {
                "asset" -> id?.let { TagTarget.AssetTarget(AssetId(it)) }
                "link" -> id?.let { TagTarget.LinkTarget(LinkId(it)) }
                "none" -> TagTarget.None
                else -> null
            }
        }
    }
}
