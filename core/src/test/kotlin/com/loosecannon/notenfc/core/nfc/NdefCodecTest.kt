package com.loosecannon.notenfc.core.nfc

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertContentEquals

class NdefCodecTest {
    private fun legacy(payload: String, type: String = NdefCodec.LEGACY_TYPE) =
        NdefRecordData(NdefCodec.TNF_EXTERNAL_TYPE, type.toByteArray(Charsets.US_ASCII), payload.toByteArray(Charsets.UTF_8))

    @Test fun decodesValidLegacyKey() {
        val p = NdefCodec.decode(listOf(legacy("63b37acf")))
        assertEquals(TagPayload.LegacyMd5("63b37acf"), p)
    }
    @Test fun onlyFirstRecordMatters() {
        val p = NdefCodec.decode(listOf(legacy("63b37acf"), legacy("deadbeef")))
        assertEquals(TagPayload.LegacyMd5("63b37acf"), p)
    }
    @Test fun emptyMessageIsEmpty() = assertEquals(TagPayload.Empty, NdefCodec.decode(emptyList()))
    @Test fun uppercaseKeyIsMalformed() { assertIs<TagPayload.Malformed>(NdefCodec.decode(listOf(legacy("63B37ACF")))) }
    @Test fun wrongLengthIsMalformed() { assertIs<TagPayload.Malformed>(NdefCodec.decode(listOf(legacy("63b37ac")))) }
    @Test fun nonHexIsMalformed() { assertIs<TagPayload.Malformed>(NdefCodec.decode(listOf(legacy("63b37acz")))) }
    @Test fun evernoteEraTypeIsForeign() {
        val p = NdefCodec.decode(listOf(legacy("63b37acf", type = "com.loosecannon.evernotenfc:md5_short")))
        assertIs<TagPayload.Foreign>(p)
    }
    @Test fun uriRecordIsForeign() {
        val uri = NdefRecordData(tnf = 0x01, type = byteArrayOf('U'.code.toByte()), payload = byteArrayOf(0x01) + "example.com".toByteArray())
        assertIs<TagPayload.Foreign>(NdefCodec.decode(listOf(uri)))
    }
    @Test fun encodeLegacyRoundTrips() {
        val rec = NdefCodec.encodeLegacy("63b37acf")
        assertEquals(NdefCodec.TNF_EXTERNAL_TYPE, rec.tnf)
        assertContentEquals(NdefCodec.LEGACY_TYPE.toByteArray(Charsets.US_ASCII), rec.type)
        assertContentEquals("63b37acf".toByteArray(Charsets.US_ASCII), rec.payload)
        assertEquals(TagPayload.LegacyMd5("63b37acf"), NdefCodec.decode(listOf(rec)))
    }
}
