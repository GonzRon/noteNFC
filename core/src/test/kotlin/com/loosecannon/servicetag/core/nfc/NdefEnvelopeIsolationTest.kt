package com.loosecannon.servicetag.core.nfc

import com.loosecannon.nfc.tagcore.NdefEnvelope
import com.loosecannon.nfc.tagcore.NdefRecordData
import com.loosecannon.nfc.tagcore.TagIdentity
import com.loosecannon.servicetag.core.model.TagId
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Sibling isolation, ServiceTag's direction (target §4.3 invariant 1, C8). A NoteTag record must
 * come back `Foreign` even when its bytes would parse: the type gate runs before any body parse,
 * and a product never reads a payload it did not type-check.
 *
 * NoteTag's own v1 envelope is `version|kind|flags|body` on `com.loosecannon.notetag:tag` (O13,
 * O14). This test writes those bytes by hand — it must not import anything of NoteTag's, which
 * does not exist in this repository and never will.
 */
class NdefEnvelopeIsolationTest {
    // TODO(Phase F): this test names an application; it stays behind when nfc-core moves to nfc-tag-core
    private val ours = TagIdentity("com.loosecannon.servicetag", "tag", "com.loosecannon.servicetag")
    private val codec = NdefCodec(ours)

    private fun external(type: String, payload: ByteArray) =
        NdefRecordData(NdefEnvelope.TNF_EXTERNAL_TYPE, type.toByteArray(Charsets.US_ASCII), payload)

    /** A NoteTag JOPLIN_NOTE record: version 1, kind 0x01, flags 0, then 16 raw id bytes. */
    @OptIn(ExperimentalStdlibApi::class)
    private val noteTagJoplinRecord = external(
        // TODO(Phase F): this test names an application; it stays behind when nfc-core moves to nfc-tag-core
        "com.loosecannon.notetag:tag",
        byteArrayOf(0x01, 0x01, 0x00) + "123e4567e89b12d3a456426614174000".hexToByteArray(),
    )

    @Test fun aNoteTagRecordIsForeign() {
        val decoded = codec.decode(listOf(noteTagJoplinRecord))
        assertIs<TagPayload.Foreign>(decoded)
        // kotlin.test puts the message LAST, unlike JUnit's Assert -- `:core` is kotlin.test.
        assertTrue(
            decoded.description.contains("com.loosecannon.notetag:tag"),
            "the refusal names the type it saw, so the UI can say what the tag is",
        )
    }

    /**
     * The dangerous case: a sibling record whose body is byte-for-byte a valid ServiceTag v1
     * payload. Only the type gate can tell these apart, and it must.
     */
    @Test fun aSiblingRecordCarryingOurOwnPayloadIsStillForeign() {
        val ourPayload = codec.v1Record(TagId("123e4567-e89b-12d3-a456-426614174000")).payload
        assertIs<TagPayload.Foreign>(codec.decode(listOf(external("com.loosecannon.notetag:tag", ourPayload))))
    }

    /** And the reverse framing check: our type with a sibling's body is ours, and malformed. */
    @Test fun ourTypeWithASiblingBodyIsOursAndMalformed() {
        val noteTagBody = byteArrayOf(0x01, 0x01, 0x00) + ByteArray(16)
        assertIs<TagPayload.Malformed>(codec.decode(listOf(external(ours.externalType, noteTagBody))))
    }

    /** A NoteTag AAR in second place changes nothing: the platform reads the first record. */
    @Test fun aSiblingAarDoesNotMakeATagOurs() {
        val siblingAar = external("android.com:pkg", "com.loosecannon.notetag".toByteArray(Charsets.US_ASCII))
        assertIs<TagPayload.Foreign>(codec.decode(listOf(noteTagJoplinRecord, siblingAar)))
    }
}
