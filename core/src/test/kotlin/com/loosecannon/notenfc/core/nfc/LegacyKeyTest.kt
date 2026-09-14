package com.loosecannon.notenfc.core.nfc

import kotlin.test.Test
import kotlin.test.assertEquals

class LegacyKeyTest {
    // Vectors computed with `printf '%s' "<text>" | md5sum | cut -c1-8`; they pin the legacy behaviour verbatim.
    @Test fun emptyString() = assertEquals("d41d8cd9", LegacyKey.compute(""))
    @Test fun abc() = assertEquals("90015098", LegacyKey.compute("abc"))
    @Test fun joplinExternalLink() =
        assertEquals("63b37acf", LegacyKey.compute("joplin://x-callback-url/openNote?id=0123456789abcdef0123456789abcdef"))
    @Test fun rawSharedTextWithTitleIsHashedAsAWhole() =
        assertEquals("8fbfdd64", LegacyKey.compute("Note title joplin://x-callback-url/openNote?id=0123456789abcdef0123456789abcdef"))
    @Test fun utf8BytesNotPlatformDefault() = assertEquals("31f448a3", LegacyKey.compute("Ünïcödé joplin"))
    @Test fun alwaysEightLowercaseHexChars() {
        val k = LegacyKey.compute("anything at all")
        assertEquals(8, k.length)
        assert(k.all { it in '0'..'9' || it in 'a'..'f' }) { "not lowercase hex: $k" }
    }
}
