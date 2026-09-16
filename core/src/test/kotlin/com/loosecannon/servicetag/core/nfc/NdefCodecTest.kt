package com.loosecannon.servicetag.core.nfc

import com.loosecannon.servicetag.core.model.TagId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** The envelope's own rules, on an identity that belongs to no real product. */
class NdefCodecTest {
    private val identity = TagIdentity("com.example.app", "tag", "com.example.app")
    private val codec = NdefCodec(identity)

    private fun external(type: String, payload: String) =
        NdefRecordData(NdefCodec.TNF_EXTERNAL_TYPE, type.toByteArray(Charsets.US_ASCII), payload.toByteArray(Charsets.UTF_8))

    @Test fun emptyMessageIsEmpty() = assertEquals(TagPayload.Empty, codec.decode(emptyList()))

    @Test fun onlyFirstRecordMatters() {
        val ours = codec.v1Record(TagId("123e4567-e89b-12d3-a456-426614174000"))
        val foreign = external("com.example.other:tag", "whatever")
        assertIs<TagPayload.V1>(codec.decode(listOf(ours, foreign)))
        assertIs<TagPayload.Foreign>(codec.decode(listOf(foreign, ours)))
    }

    @Test fun anotherDomainIsForeign() {
        assertIs<TagPayload.Foreign>(codec.decode(listOf(external("com.example.other:tag", "63b37acf"))))
    }

    @Test fun anotherTypeNameInOurDomainIsForeign() {
        assertIs<TagPayload.Foreign>(codec.decode(listOf(external("com.example.app:md5_short", "63b37acf"))))
    }

    @Test fun uriRecordIsForeign() {
        val uri = NdefRecordData(tnf = 0x01, type = byteArrayOf('U'.code.toByte()), payload = byteArrayOf(0x01) + "example.com".toByteArray())
        assertIs<TagPayload.Foreign>(codec.decode(listOf(uri)))
    }
}
