package com.loosecannon.servicetag.core.nfc

import com.loosecannon.servicetag.core.model.TagId
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * `Ndef.getMaxSize()` is the maximum NDEF **message** size a tag can hold, so every capacity
 * comparison in this estate is message size against message size — never the Type-2 TLV header
 * or terminator, which belong to Android and to the tag (G1, invariant 7).
 *
 * The number is a provisional seed only: `[unobserved]`, to be re-pinned from the measured
 * `Ndef.maxSize` of a physical NTAG213 in §D Session 1 (H8). Nothing branches on it — this is a
 * limits test, and its job is to fail loudly if the record ever grows past a small tag.
 */
private const val NTAG213_MAX_MESSAGE_BYTES = 137

@OptIn(ExperimentalStdlibApi::class)
class NdefCodecV1Test {
    private val identity = TagIdentity("com.loosecannon.servicetag", "tag", "com.loosecannon.servicetag")
    private val codec = NdefCodec(identity)
    private val id = TagId("123e4567-e89b-12d3-a456-426614174000")
    private val idBytes = "123e4567e89b12d3a456426614174000".hexToByteArray()

    private fun v1(payload: ByteArray) =
        NdefRecordData(NdefCodec.TNF_EXTERNAL_TYPE, identity.externalType.toByteArray(Charsets.US_ASCII), payload)

    /** The exact bytes a ServiceTag tag carries: 3 + 30 + 18 = 51 B for the record (H2). */
    @Test fun exactByteLayout() {
        val rec = codec.v1Record(id)
        assertEquals(0x04, rec.tnf)
        assertContentEquals("com.loosecannon.servicetag:tag".toByteArray(Charsets.US_ASCII), rec.type)
        assertEquals(18, rec.payload.size)
        assertContentEquals(byteArrayOf(0x01, 0x00) + idBytes, rec.payload)
        // kotlin.test puts the message LAST, unlike JUnit's Assert -- `:core` is kotlin.test.
        assertEquals(51, 3 + rec.type.size + rec.payload.size, "the :tag record is 51 bytes")
    }

    @Test fun messageIsTagRecordThenApplicationRecord() {
        val msg = codec.encodeV1(id)
        assertEquals(2, msg.size)
        assertEquals(codec.v1Record(id), msg[0])
        assertEquals(assertNotNull(codec.applicationRecord()), msg[1])
    }

    @Test fun roundTrips() {
        assertEquals(TagPayload.V1(id), codec.decode(codec.encodeV1(id)))
    }

    @Test fun decodedIdIsCanonicalLowercase() {
        val p = codec.decode(listOf(v1(byteArrayOf(0x01, 0x00) + idBytes)))
        assertEquals(TagPayload.V1(TagId("123e4567-e89b-12d3-a456-426614174000")), p)
    }

    @Test fun refusesNonCanonicalIdOnEncode() {
        assertFailsWith<IllegalArgumentException> { codec.v1Record(TagId("123E4567-E89B-12D3-A456-426614174000")) }
        assertFailsWith<IllegalArgumentException> { codec.v1Record(TagId("not-a-uuid")) }
        assertFailsWith<IllegalArgumentException> { codec.v1Record(TagId("")) }
    }

    /** 51 B for the record plus 3 + 15 + 26 = 44 B for the AAR: a 95 B message (H2). */
    @Test fun theWholeMessageIs95Bytes() {
        val onTag = codec.encodeV1(id).sumOf { 3 + it.type.size + it.payload.size }
        assertEquals(95, onTag)
    }

    @Test fun fitsAnNtag213() {
        val onTag = codec.encodeV1(id).sumOf { 3 + it.type.size + it.payload.size }
        assertTrue(onTag <= NTAG213_MAX_MESSAGE_BYTES, "the message needs $onTag bytes; the seed is $NTAG213_MAX_MESSAGE_BYTES")
    }

    @Test fun newerVersionIsReportedNotParsed() {
        assertEquals(TagPayload.NewerVersion(2), codec.decode(listOf(v1(byteArrayOf(0x02, 0x00) + idBytes))))
        // a future format may have any length; the version byte alone decides
        assertEquals(TagPayload.NewerVersion(0x7f), codec.decode(listOf(v1(byteArrayOf(0x7f, 0x01, 0x02)))))
        assertEquals(TagPayload.NewerVersion(0xff), codec.decode(listOf(v1(byteArrayOf(0xff.toByte())))))
    }

    @Test fun versionZeroIsMalformed() {
        assertIs<TagPayload.Malformed>(codec.decode(listOf(v1(byteArrayOf(0x00, 0x00) + idBytes))))
    }

    @Test fun wrongLengthIsMalformed() {
        assertIs<TagPayload.Malformed>(codec.decode(listOf(v1(byteArrayOf(0x01, 0x00) + idBytes.copyOf(15)))))
        assertIs<TagPayload.Malformed>(codec.decode(listOf(v1(byteArrayOf(0x01, 0x00) + idBytes + 0x00))))
        assertIs<TagPayload.Malformed>(codec.decode(listOf(v1(byteArrayOf(0x01)))))
    }

    @Test fun nonZeroFlagsIsMalformed() {
        assertIs<TagPayload.Malformed>(codec.decode(listOf(v1(byteArrayOf(0x01, 0x01) + idBytes))))
        assertIs<TagPayload.Malformed>(codec.decode(listOf(v1(byteArrayOf(0x01, 0x80.toByte()) + idBytes))))
    }

    @Test fun emptyPayloadIsMalformed() {
        assertIs<TagPayload.Malformed>(codec.decode(listOf(v1(ByteArray(0)))))
    }

    @Test fun applicationRecordAloneIsForeign() {
        assertIs<TagPayload.Foreign>(codec.decode(listOf(assertNotNull(codec.applicationRecord()))))
    }

    @Test fun tagRecordUnderWrongTnfIsForeign() {
        val rec = NdefRecordData(0x02, identity.externalType.toByteArray(Charsets.US_ASCII), byteArrayOf(0x01, 0x00) + idBytes)
        assertIs<TagPayload.Foreign>(codec.decode(listOf(rec)))
    }

    /** An identity with no AAR writes one record and nothing else (O13's default). */
    @Test fun anIdentityWithoutAnAarWritesOneRecord() {
        val lone = NdefCodec(TagIdentity("com.example.app", "tag"))
        assertEquals(1, lone.encodeV1(id).size)
        assertEquals(null, lone.applicationRecord())
    }
}
