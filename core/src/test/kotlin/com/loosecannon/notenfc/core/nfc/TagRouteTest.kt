package com.loosecannon.notenfc.core.nfc

import com.loosecannon.notenfc.core.model.TagId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class TagRouteTest {
    @Test fun tagRouteBecomesAV1Payload() {
        val p = TagRoute.parse("notenfc", "tag", listOf("123e4567-e89b-12d3-a456-426614174000"))
        assertEquals(TagPayload.V1(TagId("123e4567-e89b-12d3-a456-426614174000")), p)
    }
    @Test fun otherSchemesAndHostsAreNotThisRoute() {
        assertNull(TagRoute.parse("https", "tag", listOf("123e4567-e89b-12d3-a456-426614174000")))
        assertNull(TagRoute.parse("notenfc", "asset", listOf("123e4567-e89b-12d3-a456-426614174000")))
        assertNull(TagRoute.parse(null, null, emptyList()))
    }
    @Test fun badIdsAreMalformedNotCrashes() {
        assertIs<TagPayload.Malformed>(TagRoute.parse("notenfc", "tag", emptyList()))
        assertIs<TagPayload.Malformed>(TagRoute.parse("notenfc", "tag", listOf("nope")))
        assertIs<TagPayload.Malformed>(TagRoute.parse("notenfc", "tag", listOf("123E4567-E89B-12D3-A456-426614174000")))
        assertIs<TagPayload.Malformed>(TagRoute.parse("notenfc", "tag", listOf("123e4567-e89b-12d3-a456-426614174000", "extra")))
    }
}
