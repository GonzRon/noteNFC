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
    /** Tag payload format v1: the tag carries a random tag id and nothing else (D4 §3). */
    data class V1(val tagId: TagId) : TagPayload

    /** A `:tag` record whose version byte is above what this build understands. Never parsed. */
    data class NewerVersion(val version: Int) : TagPayload

    data class Foreign(val description: String) : TagPayload
    data class Malformed(val reason: String) : TagPayload
    data object Empty : TagPayload
}

/**
 * Pure-bytes codec for tag payload format v1. Android `NdefRecord` objects are built only in the
 * `:app` NFC adapter (D3 §9). The product's wire identity arrives as [identity] and is never a
 * constant in here — Phase F moves this class into `nfc-tag-core`, which may not know either
 * application's name (O5, target §4.2).
 */
class NdefCodec(val identity: TagIdentity) {

    /** Android dispatches on the first record of the first message; so do we. */
    fun decode(records: List<NdefRecordData>): TagPayload {
        val first = records.firstOrNull() ?: return TagPayload.Empty
        val type = String(first.type, Charsets.US_ASCII)
        if (first.tnf != TNF_EXTERNAL_TYPE) return TagPayload.Foreign("tnf=${first.tnf} type=$type")
        // The type gate comes before any body parse, and a sibling product's record must come
        // back Foreign even though its body would also parse as a UUID (C8, invariant 1).
        if (type != identity.externalType) return TagPayload.Foreign("tnf=${first.tnf} type=$type")
        return decodeV1(first.payload)
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

    /** The whole message for a v1 tag: the `:tag` record, then the AAR when this identity has one. */
    fun encodeV1(tagId: TagId): List<NdefRecordData> = listOfNotNull(v1Record(tagId), applicationRecord())

    fun v1Record(tagId: TagId): NdefRecordData {
        val uuid = requireCanonicalUuid(tagId)
        val payload = ByteBuffer.allocate(V1_PAYLOAD_LENGTH)
            .put(V1_VERSION.toByte())
            .put(V1_FLAGS.toByte())
            .putLong(uuid.mostSignificantBits)
            .putLong(uuid.leastSignificantBits)
            .array()
        return NdefRecordData(TNF_EXTERNAL_TYPE, identity.externalType.toByteArray(Charsets.US_ASCII), payload)
    }

    /**
     * Byte-identical to `NdefRecord.createApplicationRecord(identity.aarPackage)`, or null when
     * this identity carries no AAR. An AAR is never first: put it first and the tag stops matching
     * the NDEF_DISCOVERED filter (invariant 2).
     */
    fun applicationRecord(): NdefRecordData? = identity.aarPackage?.let { pkg ->
        NdefRecordData(
            tnf = TNF_EXTERNAL_TYPE,
            type = AAR_TYPE.toByteArray(Charsets.US_ASCII),
            payload = pkg.toByteArray(Charsets.US_ASCII),
        )
    }

    companion object {
        const val TNF_EXTERNAL_TYPE: Int = 0x04

        /** Platform constant, not identity. */
        const val AAR_TYPE: String = "android.com:pkg"

        const val V1_VERSION: Int = 0x01
        const val V1_FLAGS: Int = 0x00
        const val V1_PAYLOAD_LENGTH: Int = 18

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
}
