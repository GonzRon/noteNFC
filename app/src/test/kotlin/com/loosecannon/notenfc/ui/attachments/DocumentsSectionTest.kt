package com.loosecannon.notenfc.ui.attachments

import com.loosecannon.notenfc.core.model.AttachmentKind
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The two bits of wording in the DOCUMENTS row that are arithmetic rather than layout: the size
 * on the quiet line and the kind's own label. Everything else in the section is drawn, and the
 * emulator suite drives that.
 */
class DocumentsSectionTest {

    @Test fun sizesReadAsBytesThenOneDecimalOfKbThenOneDecimalOfMb() {
        assertEquals("0 B", 0L.asFileSize())
        assertEquals("1023 B", 1023L.asFileSize())
        // A kibibyte is the first KB, and the decimal is always there.
        assertEquals("1.0 KB", 1024L.asFileSize())
        assertEquals("1.5 KB", 1536L.asFileSize())
        assertEquals("1024.0 KB", (1024L * 1024L - 1L).asFileSize())
        assertEquals("1.0 MB", (1024L * 1024L).asFileSize())
        assertEquals("2.5 MB", (2L * 1024L * 1024L + 512L * 1024L).asFileSize())
        assertEquals("256.0 MB", (256L * 1024L * 1024L).asFileSize())
    }

    @Test fun everyKindHasASentenceCaseLabel() {
        assertEquals(
            listOf("Photo", "Label photo", "Receipt", "Manual", "Warranty", "Document", "Other"),
            AttachmentKind.entries.map { it.label() },
        )
    }
}
