package com.loosecannon.servicetag.ui.scan

import android.nfc.Tag
import com.loosecannon.servicetag.core.model.TagBinding
import com.loosecannon.servicetag.core.model.TagTarget
import com.loosecannon.servicetag.core.nfc.NdefCodec
import com.loosecannon.servicetag.core.nfc.NdefRecordData
import com.loosecannon.servicetag.core.nfc.OverwriteDecision
import com.loosecannon.servicetag.core.nfc.OverwritePolicy
import com.loosecannon.servicetag.core.nfc.TagPayload
import com.loosecannon.servicetag.core.usecase.ProvisionTag
import com.loosecannon.servicetag.di.AppGraph
import com.loosecannon.servicetag.nfc.TagInspection
import com.loosecannon.servicetag.nfc.TagWriter
import com.loosecannon.servicetag.nfc.WriteResult
import com.loosecannon.servicetag.nfc.toHexOrNull
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * One tap's handle on a physical tag. Reader mode hands out an `android.nfc.Tag`, which no JVM
 * test can build, so the controller only ever sees this: the chip's UID, plus whatever the real
 * [TagIo] needs to hide behind it.
 */
interface TagHandle {
    /** The chip's hardware UID as lower-case hex, or null when the platform did not supply one. */
    val uid: String?
}

/** The reader-mode handle. Only [RealTagIo] ever looks inside it. */
class NfcTagHandle(val tag: Tag) : TagHandle {
    override val uid: String? = tag.id.toHexOrNull()
}

/**
 * The three blocking tag operations, as a seam. Everything above it — read first, ask before
 * overwriting, verify, lock last — is decision logic, and decision logic belongs in a test.
 *
 * Implementations block on tag I/O; [TagWriteController] is what guarantees they are called off
 * the main thread.
 */
interface TagIo {
    /** @throws java.io.IOException when the tag leaves the field mid-read (`TagWriter.inspect`). */
    fun inspect(tag: TagHandle): TagInspection?
    fun write(tag: TagHandle, records: List<NdefRecordData>, lock: Boolean): WriteResult
    fun lock(tag: TagHandle): Boolean
}

/** [TagWriter] behind the seam; the only place an `android.nfc.Tag` comes back out of a handle. */
class RealTagIo(private val codec: NdefCodec) : TagIo {
    override fun inspect(tag: TagHandle): TagInspection? = TagWriter.inspect(tag.nfc(), codec)
    override fun write(tag: TagHandle, records: List<NdefRecordData>, lock: Boolean): WriteResult =
        TagWriter.write(tag.nfc(), records, lock)
    override fun lock(tag: TagHandle): Boolean = TagWriter.lock(tag.nfc())

    private fun TagHandle.nfc(): Tag = (this as? NfcTagHandle)?.tag
        ?: error("RealTagIo only accepts a handle delivered by reader mode")
}

/** What the write screen draws. One state at a time; the lock switch is separate state. */
sealed interface WriteState {
    /** Nothing has happened yet, or the last tap deliberately left the tag alone. */
    data class Idle(val message: String) : WriteState

    /** The tag already holds something; [reason] names it, in `OverwritePolicy`'s words. */
    data class Confirm(val reason: String) : WriteState

    /** A formatted tag was written but could not be re-read on the same handle: tap it again. */
    data class Verifying(val message: String) : WriteState

    data class Written(val tagId: String, val locked: Boolean) : WriteState
    data class Error(val message: String) : WriteState
}

/**
 * The Phase 1B write flow, unchanged in behaviour and lifted out of an Activity (D3 §9): read
 * first, confirm before overwriting anything but an empty tag or the same id, write off the main
 * thread, read back and compare, lock only after a verified read-back.
 *
 * A row is provisioned on the first tap and reused for every retry; [abandonIfUnwritten] deletes
 * it if the screen closes before a verified write, so no phantom tag is left behind.
 */
class TagWriteController(
    private val provisionTag: ProvisionTag,
    /** Outlives the screen: abandoning a row must finish even though the screen is going away. */
    private val appScope: CoroutineScope,
    private val io: TagIo,
    private val codec: NdefCodec,
    private val target: TagTarget,
    private val label: String?,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    constructor(graph: AppGraph, io: TagIo, target: TagTarget, label: String?, scope: CoroutineScope) :
        this(graph.provisionTag, graph.appScope, io, graph.ndefCodec, target, label, scope)

    private val _state = MutableStateFlow<WriteState>(InitialState)
    val state: StateFlow<WriteState> = _state.asStateFlow()

    private val _lock = MutableStateFlow(false)

    /** Whether the user has armed "lock permanently"; the warning dialog lives on the screen. */
    val lock: StateFlow<Boolean> = _lock.asStateFlow()

    @Volatile private var pending: TagBinding? = null
    @Volatile private var awaitingVerify = false

    /**
     * What the user agreed to overwrite. The handle captured before the confirmation sheet can go
     * stale while it is up (the NFC service re-discovers the tag and then refuses the old handle
     * with "Tag is out of date" — seen on an Android 17 phone), so a confirmation is remembered as
     * consent for *this content* and honoured on the next tap of a tag carrying it.
     */
    @Volatile private var confirmedOverwrite: TagPayload? = null
    @Volatile private var done = false
    @Volatile private var busy = false

    /** The tap the confirmation sheet is asking about; it owns [busy] until it is answered. */
    @Volatile private var awaitingAnswer: PendingWrite? = null

    private class PendingWrite(
        val existing: TagPayload,
        val tag: TagHandle,
        val intended: List<NdefRecordData>,
        val row: TagBinding,
    )

    fun setLock(value: Boolean) { _lock.value = value }

    /** Reader mode calls this from a binder thread; nothing here touches the main thread. */
    fun onTag(tag: TagHandle) {
        if (busy || done) return
        busy = true
        scope.launch {
            var sheetOwnsBusy = false
            try {
                sheetOwnsBusy = handle(tag)
            } catch (e: Exception) {
                _state.value = WriteState.Error(
                    "Failed: ${e.javaClass.simpleName}: ${e.message}\nHold the tag still and try again.",
                )
            } finally {
                if (!sheetOwnsBusy) busy = false
            }
        }
    }

    /** Returns true when the confirmation sheet now owns the [busy] flag. */
    private suspend fun handle(tag: TagHandle): Boolean {
        val row = pending ?: provisionTag.begin(target, label).also { pending = it }
        val intended = codec.encodeV1(row.id)
        val inspection = withContext(ioDispatcher) { io.inspect(tag) }
        if (inspection == null) {
            _state.value = WriteState.Error("This tag does not support NDEF. Use an NTAG213/215/216 or similar.")
            return false
        }
        if (awaitingVerify) {
            verify(tag, inspection, intended, row)
            return false
        }
        if (!inspection.writable) {
            _state.value = WriteState.Error("This tag is read-only (locked). Nothing written.")
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
            is OverwriteDecision.Confirm -> {
                awaitingAnswer = PendingWrite(inspection.existing, tag, intended, row)
                _state.value = WriteState.Confirm(d.reason)
                true
            }
        }
    }

    /** "Overwrite" on the confirmation sheet. */
    fun confirmOverwrite() {
        val asked = awaitingAnswer ?: return
        awaitingAnswer = null
        confirmedOverwrite = asked.existing
        scope.launch {
            try {
                write(asked.tag, asked.intended, asked.row)
            } catch (e: Exception) {
                _state.value = WriteState.Error(
                    "Overwrite confirmed, but the tag was lost (${e.message}). Hold it to the phone again to finish.",
                )
            } finally {
                busy = false
            }
        }
    }

    /** "Keep it", and the same thing a dismissed sheet means: the tag is left exactly as it was. */
    fun keepIt() {
        if (awaitingAnswer == null) return
        awaitingAnswer = null
        confirmedOverwrite = null
        busy = false
        _state.value = WriteState.Idle("Not written. The tag was left as it was.")
    }

    private suspend fun write(tag: TagHandle, intended: List<NdefRecordData>, row: TagBinding) {
        val wantLock = _lock.value
        when (val r = withContext(ioDispatcher) { io.write(tag, intended, wantLock) }) {
            is WriteResult.Written -> if (r.verified) {
                finishWrite(row, tag.uid, r.locked)
            } else {
                awaitingVerify = true
                _state.value = WriteState.Verifying(
                    "Formatted and written (${r.bytes} bytes). Lift the tag off, then hold it again to verify the read-back.",
                )
            }
            is WriteResult.TooSmall ->
                _state.value = WriteState.Error("Tag too small: it holds ${r.maxSize} bytes, the message needs ${r.needed}.")
            WriteResult.ReadOnly ->
                _state.value = WriteState.Error("This tag is read-only (locked). Nothing written.")
            WriteResult.Unsupported ->
                _state.value = WriteState.Error("This tag does not support NDEF.")
            is WriteResult.VerifyMismatch ->
                _state.value = WriteState.Error("Read-back differs from what was written. Nothing recorded — try again.")
            is WriteResult.Failed -> _state.value = WriteState.Error(
                if (confirmedOverwrite != null) {
                    "Overwrite confirmed, but the write did not go through (${r.reason}).\n" +
                        "Lift the tag off and hold it to the phone again to finish."
                } else {
                    "Write failed: ${r.reason}\nHold the tag still and try again."
                },
            )
        }
    }

    private suspend fun verify(
        tag: TagHandle,
        inspection: TagInspection,
        intended: List<NdefRecordData>,
        row: TagBinding,
    ) {
        if (inspection.existingRecords == intended) {
            // The format path writes unlocked; the lock only happens here, once the read-back has
            // proved the bytes on the tag are the ones we meant to put there.
            val locked = if (_lock.value && inspection.canLock && inspection.writable) {
                withContext(ioDispatcher) { io.lock(tag) }
            } else {
                !inspection.writable
            }
            finishWrite(row, inspection.uid, locked)
        } else {
            awaitingVerify = false
            _state.value = WriteState.Error(
                "Read-back differs: the tag holds ${describe(inspection.existing)}. Try writing again.",
            )
        }
    }

    private suspend fun finishWrite(row: TagBinding, uid: String?, locked: Boolean) {
        val completed = provisionTag.complete(row.id, uid)
        done = true
        _state.value = WriteState.Written(completed.id.value, locked)
    }

    /**
     * Deletes the provisioned row unless a verified write already claimed it. Returns the job so
     * a test can wait for it; the screen fires and forgets, on a scope that outlives it.
     */
    fun abandonIfUnwritten(): Job? {
        val row = pending
        if (done || row == null) return null
        return appScope.launch { provisionTag.abandon(row.id) }
    }

    private companion object {
        val InitialState = WriteState.Idle("Hold a blank or reusable tag to the back of the phone.")

        fun describe(p: TagPayload): String = when (p) {
            is TagPayload.V1 -> "ServiceTag tag ${p.tagId.value}"
            is TagPayload.NewerVersion -> "a newer ServiceTag format (${p.version})"
            is TagPayload.Foreign -> "foreign content (${p.description})"
            is TagPayload.Malformed -> "unreadable content (${p.reason})"
            TagPayload.Empty -> "nothing"
        }
    }
}
