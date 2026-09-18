package com.loosecannon.servicetag.ui.scan

import com.loosecannon.servicetag.core.model.PayloadFormat
import com.loosecannon.servicetag.core.model.TagId
import com.loosecannon.servicetag.core.nfc.TagPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The (format, key) pair is what survives process death between the NFC trampoline and the sheet,
 * so it is a wire format with exactly two words in it. One owner, both directions, one test.
 */
class TagResultWireTest {

    private val id = TagId("123e4567-e89b-12d3-a456-426614174000")

    @Test fun aV1PayloadRoundTripsThroughTheEnumName() {
        val format = TagResultWire.formatOf(TagPayload.V1(id))
        assertEquals(PayloadFormat.V1.name, format)
        assertEquals(TagPayload.V1(id), TagResultWire.payloadOf(format, id.value))
    }

    @Test fun everythingElseIsTheNotOursWord() {
        assertEquals(TagResultWire.FORMAT_NONE, TagResultWire.formatOf(TagPayload.Empty))
        assertEquals(TagResultWire.FORMAT_NONE, TagResultWire.formatOf(TagPayload.Foreign("tnf=1 type=U")))
        assertEquals(TagResultWire.FORMAT_NONE, TagResultWire.formatOf(TagPayload.Malformed("short")))
        assertEquals(TagResultWire.FORMAT_NONE, TagResultWire.formatOf(TagPayload.NewerVersion(2)))
    }

    /**
     * The short circuit: an unknown word never becomes a payload, so nothing downstream of it can
     * reach `ResolveTag`. The sheet shows the key as prose instead (arch §5.12).
     */
    @Test fun anUnknownFormatIsNotAPayload() {
        assertNull(TagResultWire.payloadOf(TagResultWire.FORMAT_NONE, "empty tag"))
        assertNull(TagResultWire.payloadOf("LEGACY_MD5", "63b37acf"))
        assertNull(TagResultWire.payloadOf("", ""))
        assertNull(TagResultWire.payloadOf("v1", id.value))   // the enum name, exactly, or nothing
    }

    /** The word a stored row contributes to the pair is that row's enum name, nothing else. */
    @Test fun aStoredRowsWordIsItsEnumName() {
        assertEquals(PayloadFormat.V1.name, TagResultWire.wordFor(PayloadFormat.V1))
    }

    /** A V1 word with a key that is not a tag id is still not a payload. */
    @Test fun aV1WordNeedsACanonicalId() {
        assertNull(TagResultWire.payloadOf(PayloadFormat.V1.name, "not-a-uuid"))
    }
}
