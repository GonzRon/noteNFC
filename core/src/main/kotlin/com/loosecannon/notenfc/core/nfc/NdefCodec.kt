package com.loosecannon.notenfc.core.nfc

/** Android-free view of one NDEF record (mirrors android.nfc.NdefRecord's tnf/type/payload). */
data class NdefRecordData(val tnf: Int, val type: ByteArray, val payload: ByteArray) {
    override fun equals(other: Any?): Boolean =
        other is NdefRecordData && tnf == other.tnf && type.contentEquals(other.type) && payload.contentEquals(other.payload)
    override fun hashCode(): Int = 31 * (31 * tnf + type.contentHashCode()) + payload.contentHashCode()
}

sealed interface TagPayload {
    data class LegacyMd5(val key: String) : TagPayload
    data class Foreign(val description: String) : TagPayload
    data class Malformed(val reason: String) : TagPayload
    data object Empty : TagPayload
}

object NdefCodec {
    const val TNF_EXTERNAL_TYPE: Int = 0x04

    /** The NFC Forum external-type domain for every noteNFC record; lower-case on the wire. */
    const val DOMAIN: String = "com.loosecannon.notenfc"

    /** The 2024 record type. Read-only, best-effort recognition (D13 §2); never written again. */
    const val LEGACY_TYPE_NAME: String = "md5_short"
    const val LEGACY_TYPE: String = "$DOMAIN:$LEGACY_TYPE_NAME"

    private val legacyKeyPattern = Regex("^[0-9a-f]{8}$")

    /** Android dispatches on the first record of the first message; so do we. */
    fun decode(records: List<NdefRecordData>): TagPayload {
        val first = records.firstOrNull() ?: return TagPayload.Empty
        val type = String(first.type, Charsets.US_ASCII)
        if (first.tnf != TNF_EXTERNAL_TYPE || type != LEGACY_TYPE) {
            return TagPayload.Foreign("tnf=${first.tnf} type=$type")
        }
        val key = String(first.payload, Charsets.UTF_8)
        if (!legacyKeyPattern.matches(key)) {
            return TagPayload.Malformed("legacy payload is not 8 lowercase hex chars: '$key'")
        }
        return TagPayload.LegacyMd5(key)
    }
}
