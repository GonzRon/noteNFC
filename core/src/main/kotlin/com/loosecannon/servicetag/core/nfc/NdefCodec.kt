package com.loosecannon.servicetag.core.nfc

import com.loosecannon.servicetag.core.model.TagId
import java.nio.ByteBuffer
import java.util.UUID

/** Android-free view of one NDEF record (mirrors android.nfc.NdefRecord's tnf/type/payload). */
data class NdefRecordData(val tnf: Int, val type: ByteArray, val payload: ByteArray) {
    override fun equals(other: Any?): Boolean =
        other is NdefRecordData && tnf == other.tnf && type.contentEquals(other.type) && payload.contentEquals(other.payload)
    override fun hashCode(): Int = 31 * (31 * tnf + type.contentHashCode()) + payload.contentHashCode()
}

sealed interface TagPayload {
    /** noteNFC tag payload format v1: the tag carries a random tag id and nothing else (D4 §3). */
    data class V1(val tagId: TagId) : TagPayload

    /** The 2024 `md5_short` record; recognised best-effort, never written (D13). */
    data class LegacyMd5(val key: String) : TagPayload

    /** A `:tag` record whose version byte is above what this build understands. Never parsed. */
    data class NewerVersion(val version: Int) : TagPayload

    data class Foreign(val description: String) : TagPayload
    data class Malformed(val reason: String) : TagPayload
    data object Empty : TagPayload
}

/**
 * Pure-bytes codec for noteNFC tag payload format v1 and the legacy record. Android `NdefRecord`
 * objects are built only in the `:app` NFC adapter (D3 §9).
 */
object NdefCodec {
    const val TNF_EXTERNAL_TYPE: Int = 0x04

    /** The NFC Forum external-type domain for every noteNFC record; lower-case on the wire. */
    const val DOMAIN: String = "com.loosecannon.notenfc"

    const val V1_TYPE_NAME: String = "tag"
    const val V1_TYPE: String = "$DOMAIN:$V1_TYPE_NAME"
    const val V1_VERSION: Int = 0x01
    const val V1_FLAGS: Int = 0x00
    const val V1_PAYLOAD_LENGTH: Int = 18

    /** The package an Android Application Record pins; must equal the app's applicationId (D13 §4). */
    const val PACKAGE_NAME: String = "com.loosecannon.notenfc"
    const val AAR_TYPE: String = "android.com:pkg"

    /** The 2024 record type. Read-only, best-effort recognition (D13 §2); never written again. */
    const val LEGACY_TYPE_NAME: String = "md5_short"
    const val LEGACY_TYPE: String = "$DOMAIN:$LEGACY_TYPE_NAME"

    private val legacyKeyPattern = Regex("^[0-9a-f]{8}$")

    /** Android dispatches on the first record of the first message; so do we. */
    fun decode(records: List<NdefRecordData>): TagPayload {
        val first = records.firstOrNull() ?: return TagPayload.Empty
        val type = String(first.type, Charsets.US_ASCII)
        if (first.tnf != TNF_EXTERNAL_TYPE) return TagPayload.Foreign("tnf=${first.tnf} type=$type")
        return when (type) {
            V1_TYPE -> decodeV1(first.payload)
            LEGACY_TYPE -> decodeLegacy(first.payload)
            else -> TagPayload.Foreign("tnf=${first.tnf} type=$type")
        }
    }

    private fun decodeV1(payload: ByteArray): TagPayload {
        if (payload.isEmpty()) return TagPayload.Malformed("empty :tag payload")
        val version = payload[0].toInt() and 0xFF
        if (version > V1_VERSION) return TagPayload.NewerVersion(version)
        if (version != V1_VERSION) return TagPayload.Malformed("version byte 0x%02x".format(version))
        if (payload.size != V1_PAYLOAD_LENGTH) {
            return TagPayload.Malformed("payload is ${payload.size} bytes, expected $V1_PAYLOAD_LENGTH")
        }
        val flags = payload[1].toInt() and 0xFF
        if (flags != V1_FLAGS) return TagPayload.Malformed("flags byte 0x%02x, expected 0x00".format(flags))
        val bb = ByteBuffer.wrap(payload, 2, 16)
        val uuid = UUID(bb.long, bb.long)
        return TagPayload.V1(TagId(uuid.toString()))
    }

    private fun decodeLegacy(payload: ByteArray): TagPayload {
        val key = String(payload, Charsets.UTF_8)
        if (!legacyKeyPattern.matches(key)) {
            return TagPayload.Malformed("legacy payload is not 8 lowercase hex chars: '$key'")
        }
        return TagPayload.LegacyMd5(key)
    }

    /** The whole message for a v1 tag: the `:tag` record first, the AAR second (D4 §3). */
    fun encodeV1(tagId: TagId): List<NdefRecordData> = listOf(v1Record(tagId), applicationRecord())

    fun v1Record(tagId: TagId): NdefRecordData {
        val uuid = requireCanonicalUuid(tagId)
        val payload = ByteBuffer.allocate(V1_PAYLOAD_LENGTH)
            .put(V1_VERSION.toByte())
            .put(V1_FLAGS.toByte())
            .putLong(uuid.mostSignificantBits)
            .putLong(uuid.leastSignificantBits)
            .array()
        return NdefRecordData(TNF_EXTERNAL_TYPE, V1_TYPE.toByteArray(Charsets.US_ASCII), payload)
    }

    /** Byte-identical to `NdefRecord.createApplicationRecord(PACKAGE_NAME)`. */
    fun applicationRecord(): NdefRecordData = NdefRecordData(
        tnf = TNF_EXTERNAL_TYPE,
        type = AAR_TYPE.toByteArray(Charsets.US_ASCII),
        payload = PACKAGE_NAME.toByteArray(Charsets.US_ASCII),
    )

    /** A tag id must be the canonical lower-case UUID string so that `nfc_tag.id == payload_key` (D4 §3). */
    fun requireCanonicalUuid(tagId: TagId): UUID {
        val uuid = try {
            UUID.fromString(tagId.value)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("tag id is not a UUID: '${tagId.value}'", e)
        }
        require(uuid.toString() == tagId.value) { "tag id must be the canonical lowercase UUID form: '${tagId.value}'" }
        return uuid
    }
}
