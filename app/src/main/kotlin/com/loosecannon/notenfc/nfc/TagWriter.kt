package com.loosecannon.notenfc.nfc

import android.nfc.FormatException
import android.nfc.Tag
import android.nfc.TagLostException
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import com.loosecannon.notenfc.core.nfc.NdefCodec
import com.loosecannon.notenfc.core.nfc.NdefRecordData
import com.loosecannon.notenfc.core.nfc.TagPayload
import java.io.IOException

class TagInspection(
    val uid: String?,
    val existing: TagPayload,
    val existingRecords: List<NdefRecordData>,
    /** `Ndef.maxSize`, or -1 for a tag that still needs formatting (capacity unknown until then). */
    val maxSize: Int,
    val writable: Boolean,
    val needsFormat: Boolean,
    val canLock: Boolean,
)

sealed interface WriteResult {
    /** [verified] is false only on the format path, where the same `Tag` object cannot be re-read. */
    data class Written(val readBack: List<NdefRecordData>, val bytes: Int, val verified: Boolean, val locked: Boolean) : WriteResult
    data class TooSmall(val maxSize: Int, val needed: Int) : WriteResult
    data object ReadOnly : WriteResult
    data object Unsupported : WriteResult
    data class VerifyMismatch(val readBack: List<NdefRecordData>) : WriteResult
    data class Failed(val reason: String) : WriteResult
}

/**
 * Read-first, write, read-back (D3 §9). Every function blocks on tag I/O: call from a worker
 * thread, never the main thread. Decisions (overwrite? which target?) are made by the caller
 * between [inspect] and [write], while the tag stays in the field.
 *
 * The two halves report failure differently on purpose: [write] never throws for tag I/O and
 * folds every such failure into [WriteResult.Failed], while [inspect] lets it propagate.
 */
object TagWriter {

    /**
     * What is on the tag right now, or null when it is neither `Ndef` nor `NdefFormatable`.
     *
     * @throws java.io.IOException (including `android.nfc.TagLostException`) when the tag leaves
     * the field or I/O fails mid-read; callers run this off the main thread inside a try/catch.
     */
    fun inspect(tag: Tag): TagInspection? {
        val uid = tag.id.toHexOrNull()
        Ndef.get(tag)?.let { ndef ->
            return try {
                ndef.connect()
                val records = try {
                    ndef.ndefMessage.toRecordData()
                } catch (e: FormatException) {
                    return TagInspection(uid, TagPayload.Malformed("NDEF on tag could not be parsed"), emptyList(), ndef.maxSize, ndef.isWritable, false, ndef.canMakeReadOnly())
                }
                TagInspection(uid, NdefCodec.decode(records), records, ndef.maxSize, ndef.isWritable, needsFormat = false, canLock = ndef.canMakeReadOnly())
            } finally {
                runCatching { ndef.close() }
            }
        }
        NdefFormatable.get(tag) ?: return null
        return TagInspection(uid, TagPayload.Empty, emptyList(), maxSize = -1, writable = true, needsFormat = true, canLock = true)
    }

    fun write(tag: Tag, records: List<NdefRecordData>, lock: Boolean): WriteResult {
        val message = records.toNdefMessage()
        val needed = message.toByteArray().size
        val ndef = Ndef.get(tag)
        if (ndef != null) {
            return try {
                ndef.connect()
                if (!ndef.isWritable) return WriteResult.ReadOnly
                if (ndef.maxSize < needed) return WriteResult.TooSmall(ndef.maxSize, needed)
                ndef.writeNdefMessage(message)
                val back = ndef.ndefMessage.toRecordData()
                if (back != records) return WriteResult.VerifyMismatch(back)
                var locked = false
                if (lock && ndef.canMakeReadOnly()) locked = ndef.makeReadOnly()
                WriteResult.Written(back, needed, verified = true, locked = locked)
            } catch (e: TagLostException) {
                WriteResult.Failed("tag left the field")
            } catch (e: IOException) {
                WriteResult.Failed(e.message ?: "I/O error")
            } catch (e: FormatException) {
                WriteResult.Failed("tag rejected the message: ${e.message}")
            } finally {
                runCatching { ndef.close() }
            }
        }
        val formatable = NdefFormatable.get(tag) ?: return WriteResult.Unsupported
        return try {
            formatable.connect()
            formatable.format(message)
            // The Tag object was discovered as NdefFormatable only; Ndef.get(tag) stays null
            // until the tag is rediscovered, so verification is the next tap's job -- and so is
            // the lock: the tag is formatted unlocked and [TagWriter.lock] is applied only
            // after that second tap's verification, never blind.
            WriteResult.Written(emptyList(), needed, verified = false, locked = false)
        } catch (e: TagLostException) {
            WriteResult.Failed("tag left the field")
        } catch (e: IOException) {
            WriteResult.Failed(e.message ?: "I/O error while formatting")
        } catch (e: FormatException) {
            WriteResult.Failed("tag could not be formatted: ${e.message}")
        } finally {
            runCatching { formatable.close() }
        }
    }

    /** Makes an NDEF tag permanently read-only. Call only after a verified read-back. Blocks; call off the main thread. */
    fun lock(tag: Tag): Boolean {
        val ndef = Ndef.get(tag) ?: return false
        return try {
            ndef.connect()
            ndef.canMakeReadOnly() && ndef.makeReadOnly()
        } catch (e: IOException) {
            false
        } finally {
            runCatching { ndef.close() }
        }
    }
}
