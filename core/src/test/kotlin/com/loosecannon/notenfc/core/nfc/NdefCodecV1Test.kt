package com.loosecannon.notenfc.core.nfc

import com.loosecannon.notenfc.core.model.TagId
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalStdlibApi::class)
class NdefCodecV1Test {
    private val id = TagId("123e4567-e89b-12d3-a456-426614174000")
    private val idBytes = "123e4567e89b12d3a456426614174000".hexToByteArray()

    private fun v1(payload: ByteArray) =
        NdefRecordData(NdefCodec.TNF_EXTERNAL_TYPE, NdefCodec.V1_TYPE.toByteArray(Charsets.US_ASCII), payload)

    @Test fun exactByteLayout() {
        val rec = NdefCodec.v1Record(id)
        assertEquals(0x04, rec.tnf)
        assertContentEquals("com.loosecannon.notenfc:tag".toByteArray(Charsets.US_ASCII), rec.type)
        assertEquals(18, rec.payload.size)
        assertContentEquals(byteArrayOf(0x01, 0x00) + idBytes, rec.payload)
    }

    @Test fun messageIsTagRecordThenApplicationRecord() {
        val msg = NdefCodec.encodeV1(id)
        assertEquals(2, msg.size)
        assertEquals(NdefCodec.v1Record(id), msg[0])
        assertEquals(0x04, msg[1].tnf)
        assertContentEquals("android.com:pkg".toByteArray(Charsets.US_ASCII), msg[1].type)
        assertContentEquals("com.loosecannon.notenfc".toByteArray(Charsets.US_ASCII), msg[1].payload)
    }

    @Test fun roundTrips() {
        assertEquals(TagPayload.V1(id), NdefCodec.decode(NdefCodec.encodeV1(id)))
    }

    @Test fun decodedIdIsCanonicalLowercase() {
        val p = NdefCodec.decode(listOf(v1(byteArrayOf(0x01, 0x00) + idBytes)))
        assertEquals(TagPayload.V1(TagId("123e4567-e89b-12d3-a456-426614174000")), p)
    }

    @Test fun refusesNonCanonicalIdOnEncode() {
        assertFailsWith<IllegalArgumentException> { NdefCodec.v1Record(TagId("123E4567-E89B-12D3-A456-426614174000")) }
        assertFailsWith<IllegalArgumentException> { NdefCodec.v1Record(TagId("not-a-uuid")) }
        assertFailsWith<IllegalArgumentException> { NdefCodec.v1Record(TagId("")) }
    }

    @Test fun newerVersionIsReportedNotParsed() {
        assertEquals(TagPayload.NewerVersion(2), NdefCodec.decode(listOf(v1(byteArrayOf(0x02, 0x00) + idBytes))))
        // a future format may have any length; the version byte alone decides
        assertEquals(TagPayload.NewerVersion(0x7f), NdefCodec.decode(listOf(v1(byteArrayOf(0x7f, 0x01, 0x02)))))
        assertEquals(TagPayload.NewerVersion(0xff), NdefCodec.decode(listOf(v1(byteArrayOf(0xff.toByte())))))
    }

    @Test fun versionZeroIsMalformed() {
        assertIs<TagPayload.Malformed>(NdefCodec.decode(listOf(v1(byteArrayOf(0x00, 0x00) + idBytes))))
    }

    @Test fun wrongLengthIsMalformed() {
        assertIs<TagPayload.Malformed>(NdefCodec.decode(listOf(v1(byteArrayOf(0x01, 0x00) + idBytes.copyOf(15)))))
        assertIs<TagPayload.Malformed>(NdefCodec.decode(listOf(v1(byteArrayOf(0x01, 0x00) + idBytes + 0x00))))
        assertIs<TagPayload.Malformed>(NdefCodec.decode(listOf(v1(byteArrayOf(0x01)))))
    }

    @Test fun nonZeroFlagsIsMalformed() {
        assertIs<TagPayload.Malformed>(NdefCodec.decode(listOf(v1(byteArrayOf(0x01, 0x01) + idBytes))))
        assertIs<TagPayload.Malformed>(NdefCodec.decode(listOf(v1(byteArrayOf(0x01, 0x80.toByte()) + idBytes))))
    }

    @Test fun emptyPayloadIsMalformed() {
        assertIs<TagPayload.Malformed>(NdefCodec.decode(listOf(v1(ByteArray(0)))))
    }

    @Test fun applicationRecordAloneIsForeign() {
        assertIs<TagPayload.Foreign>(NdefCodec.decode(listOf(NdefCodec.applicationRecord())))
    }

    @Test fun tagRecordUnderWrongTnfIsForeign() {
        val rec = NdefRecordData(0x02, NdefCodec.V1_TYPE.toByteArray(Charsets.US_ASCII), byteArrayOf(0x01, 0x00) + idBytes)
        assertIs<TagPayload.Foreign>(NdefCodec.decode(listOf(rec)))
    }

    @Test fun legacyRecordStillDecodes() {
        val rec = NdefRecordData(NdefCodec.TNF_EXTERNAL_TYPE, NdefCodec.LEGACY_TYPE.toByteArray(Charsets.US_ASCII), "63b37acf".toByteArray())
        assertEquals(TagPayload.LegacyMd5("63b37acf"), NdefCodec.decode(listOf(rec)))
    }

    @Test fun fitsAnNtag213() {
        // short-record header = flags + type length + payload length (3 bytes); plus NDEF TLV (2) + terminator (1)
        val onTag = NdefCodec.encodeV1(id).sumOf { 3 + it.type.size + it.payload.size } + 3
        assertTrue(onTag <= 144, "v1 message needs $onTag bytes; NTAG213 holds 144")
    }
}
