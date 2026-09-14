package com.loosecannon.notenfc.core.links

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LegacyLinkPolicyTest {
    @Test fun joplinLinkPassesThroughUnchanged() {
        val t = "joplin://x-callback-url/openNote?id=0123456789abcdef0123456789abcdef"
        assertEquals(t, LegacyLinkPolicy.accept(t))
    }
    @Test fun anyTextContainingJoplinPassesVerbatim() {
        val t = "My Note https://example.com not joplin"
        assertEquals(t, LegacyLinkPolicy.accept(t))     // characterises the old substring gate, warts and all
    }
    @Test fun textWithoutJoplinIsRejected() = assertNull(LegacyLinkPolicy.accept("https://example.com"))
    @Test fun nullIsRejected() = assertNull(LegacyLinkPolicy.accept(null))
    @Test fun caseSensitiveLikeTheOriginal() = assertNull(LegacyLinkPolicy.accept("JOPLIN link"))
}
