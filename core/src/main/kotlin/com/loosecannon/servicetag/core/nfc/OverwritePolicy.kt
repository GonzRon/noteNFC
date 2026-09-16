package com.loosecannon.servicetag.core.nfc

import com.loosecannon.servicetag.core.model.TagId

sealed interface OverwriteDecision {
    data object Proceed : OverwriteDecision

    /** The tag already holds something worth a question; [reason] is shown verbatim to the user. */
    data class Confirm(val reason: String) : OverwriteDecision
}

/**
 * Read-before-write rule (D3 §9): only an empty tag, or a tag that already carries the very id we
 * are about to write (a retry), is written without asking.
 */
object OverwritePolicy {
    fun decide(existing: TagPayload, intended: TagId): OverwriteDecision = when (existing) {
        TagPayload.Empty -> OverwriteDecision.Proceed
        is TagPayload.V1 ->
            if (existing.tagId == intended) OverwriteDecision.Proceed
            else OverwriteDecision.Confirm("a different noteNFC tag (${existing.tagId.value})")
        is TagPayload.NewerVersion -> OverwriteDecision.Confirm("a noteNFC tag written by a newer app (format ${existing.version})")
        is TagPayload.Foreign -> OverwriteDecision.Confirm("foreign NDEF content (${existing.description})")
        is TagPayload.Malformed -> OverwriteDecision.Confirm("unreadable NDEF content (${existing.reason})")
    }
}
