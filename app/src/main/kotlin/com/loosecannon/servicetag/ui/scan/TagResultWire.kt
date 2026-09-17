package com.loosecannon.servicetag.ui.scan

import com.loosecannon.servicetag.core.model.PayloadFormat
import com.loosecannon.servicetag.core.model.TagId
import com.loosecannon.servicetag.core.nfc.NdefCodec
import com.loosecannon.servicetag.core.nfc.TagPayload

/**
 * The two words the (format, key) extras can carry, and the only place either is written or read.
 *
 * The pair — not a `Resolution` object — is what crosses from `NfcDispatchActivity` to
 * `MainActivity`, because it survives process death and a backup import while an in-memory
 * resolution does not.
 */
object TagResultWire {

    /** Not a payload format: "this tag is not ours, and the key is why". */
    const val FORMAT_NONE: String = "NONE"

    /** The format word for a stored row. */
    fun wordFor(format: PayloadFormat): String = format.name

    /** The word for a payload the sheet can resolve; [FORMAT_NONE] for everything else. */
    fun formatOf(payload: TagPayload): String = when (payload) {
        is TagPayload.V1 -> PayloadFormat.V1.name
        is TagPayload.NewerVersion, is TagPayload.Foreign, is TagPayload.Malformed, TagPayload.Empty -> FORMAT_NONE
    }

    /**
     * The payload those extras named, or null when there is nothing to resolve — an unknown word,
     * or a key that is not a tag id. A null short-circuits to the not-ours sheet and `ResolveTag`
     * is never called (arch §5.12).
     */
    fun payloadOf(format: String, key: String): TagPayload? = when (format) {
        PayloadFormat.V1.name -> runCatching { NdefCodec.requireCanonicalUuid(TagId(key)) }
            .getOrNull()?.let { TagPayload.V1(TagId(key)) }
        else -> null
    }
}
