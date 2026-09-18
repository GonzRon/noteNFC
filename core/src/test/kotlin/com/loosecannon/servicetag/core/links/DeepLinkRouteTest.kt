package com.loosecannon.servicetag.core.links

import com.loosecannon.servicetag.core.model.AssetId
import com.loosecannon.servicetag.core.model.TagId
import com.loosecannon.servicetag.core.nfc.TagPayload
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class DeepLinkRouteTest {
    private val id = "123e4567-e89b-12d3-a456-426614174000"
    @Test fun assetRoute() { assertEquals(DeepLink.Asset(AssetId(id)), DeepLinkRoute.parse("servicetag", "asset", listOf(id))) }
    /** 2.6 — the `link` host is withdrawn; it is now simply not a route this app answers. */
    @Test fun theLinkHostIsNoLongerOurs() {
        assertNull(DeepLinkRoute.parse("servicetag", "link", listOf(id)))
        assertNull(DeepLinkRoute.parse("servicetag", "link", emptyList()))
    }
    @Test fun tagRouteDelegatesToTagRoute() { assertEquals(DeepLink.Tag(TagPayload.V1(TagId(id))), DeepLinkRoute.parse("servicetag", "tag", listOf(id))) }
    @Test fun otherSchemesAndHostsAreNotOurs() {
        assertNull(DeepLinkRoute.parse("https", "asset", listOf(id)))
        assertNull(DeepLinkRoute.parse("servicetag", "health", emptyList()))   // Phase 3 route, not yet
        assertNull(DeepLinkRoute.parse(null, null, emptyList()))
    }
    @Test fun badIdsAreMalformedNeverExceptions() {
        assertIs<DeepLink.Malformed>(DeepLinkRoute.parse("servicetag", "asset", emptyList()))
        assertIs<DeepLink.Malformed>(DeepLinkRoute.parse("servicetag", "asset", listOf("nope")))
        assertIs<DeepLink.Malformed>(DeepLinkRoute.parse("servicetag", "asset", listOf(id, "schedule", id)))  // 1C: schedules not routed yet
        assertIs<DeepLink.Malformed>(DeepLinkRoute.parse("servicetag", "tag", listOf("nope")))
    }
}
