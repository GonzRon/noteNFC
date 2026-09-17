package com.loosecannon.servicetag.core.nfc

import kotlin.test.Test
import kotlin.test.assertFailsWith

/**
 * The identity's own guards. `TagIdentity` is the only product knowledge the codec holds, so a
 * caller that hands it something that could never match the bytes on a tag has to fail at
 * construction, not quietly write a record nothing will ever decode (arch §2.9).
 */
class TagIdentityTest {

    @Test fun aBlankDomainIsRefused() {
        assertFailsWith<IllegalArgumentException> { TagIdentity("", "tag") }
    }

    @Test fun aBlankTypeNameIsRefused() {
        assertFailsWith<IllegalArgumentException> { TagIdentity("com.example.app", "   ") }
    }

    /**
     * `NdefRecord.createExternal` lower-cases both halves before joining, so an upper-case letter
     * here would name a type that is not the one on the tag.
     */
    @Test fun anUpperCaseExternalTypeIsRefused() {
        assertFailsWith<IllegalArgumentException> { TagIdentity("com.example.App", "tag") }
        assertFailsWith<IllegalArgumentException> { TagIdentity("com.example.app", "Tag") }
    }
}
