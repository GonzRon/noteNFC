package com.loosecannon.servicetag.core.nfc

import com.loosecannon.nfc.tagcore.NdefEnvelope
import com.loosecannon.nfc.tagcore.NdefRecordData
import com.loosecannon.nfc.tagcore.TagContent
import com.loosecannon.nfc.tagcore.TagIdentity
import com.loosecannon.nfc.tagcore.UuidBytes
import com.loosecannon.servicetag.core.model.TagId
import java.util.UUID

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
 * ServiceTag's v1 BODY codec: `version | flags | 16-byte UUID` inside the envelope nfc-tag-core
 * owns. The type gate (TNF, then the exact external type) is the library's `NdefEnvelope.decode`;
 * everything after the gate — the version byte, the flags, the length, the UUID — is this product's
 * and stays here (target §4.7, §5). Android `NdefRecord` objects are built only in the library's
 * adapter.
 */
class NdefCodec(val identity: TagIdentity) {

    /** Android dispatches on the first record of the first message; so does the envelope. */
    fun decode(records: List<NdefRecordData>): TagPayload = when (val c = NdefEnvelope.decode(identity, records)) {
        TagContent.Empty -> TagPayload.Empty
        is TagContent.Foreign -> TagPayload.Foreign(c.description)
        is TagContent.Recognised -> decodeV1(c.body)
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
        return TagPayload.V1(TagId(UuidBytes.fromBytes(payload, offset = 2).toString()))
    }

    /** The whole message for a v1 tag: the `:tag` record, then the AAR when this identity has one. */
    fun encodeV1(tagId: TagId): List<NdefRecordData> = NdefEnvelope.encode(identity, v1Body(tagId))

    fun v1Record(tagId: TagId): NdefRecordData = encodeV1(tagId).first()

    /** Null when this identity carries no AAR; otherwise byte-identical to the platform's record. */
    fun applicationRecord(): NdefRecordData? = identity.aarPackage?.let(NdefEnvelope::applicationRecord)

    private fun v1Body(tagId: TagId): ByteArray =
        byteArrayOf(V1_VERSION.toByte(), V1_FLAGS.toByte()) + UuidBytes.toBytes(requireCanonicalUuid(tagId))

    companion object {
        const val V1_VERSION: Int = 0x01
        const val V1_FLAGS: Int = 0x00
        const val V1_PAYLOAD_LENGTH: Int = 18

        /** A tag id must be the canonical lower-case UUID string so that `nfc_tag.id == payload_key` (D4 §3). */
        fun requireCanonicalUuid(tagId: TagId): UUID = try {
            UuidBytes.requireCanonical(tagId.value)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("tag id must be the canonical lowercase UUID form: '${tagId.value}'", e)
        }
    }
}
