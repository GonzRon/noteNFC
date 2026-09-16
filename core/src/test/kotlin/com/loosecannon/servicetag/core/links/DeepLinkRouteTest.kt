package com.loosecannon.servicetag.core.links

import com.loosecannon.servicetag.core.model.AssetId
import com.loosecannon.servicetag.core.model.LinkId
import com.loosecannon.servicetag.core.model.TagId
import com.loosecannon.servicetag.core.nfc.TagPayload
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class DeepLinkRouteTest {
    private val id = "123e4567-e89b-12d3-a456-426614174000"
    @Test fun assetRoute() { assertEquals(DeepLink.Asset(AssetId(id)), DeepLinkRoute.parse("notenfc", "asset", listOf(id))) }
    @Test fun linkRoute() { assertEquals(DeepLink.Link(LinkId(id)), DeepLinkRoute.parse("notenfc", "link", listOf(id))) }
    @Test fun tagRouteDelegatesToTagRoute() { assertEquals(DeepLink.Tag(TagPayload.V1(TagId(id))), DeepLinkRoute.parse("notenfc", "tag", listOf(id))) }
    @Test fun otherSchemesAndHostsAreNotOurs() {
        assertNull(DeepLinkRoute.parse("https", "asset", listOf(id)))
        assertNull(DeepLinkRoute.parse("notenfc", "health", emptyList()))   // Phase 3 route, not yet
        assertNull(DeepLinkRoute.parse(null, null, emptyList()))
    }
    @Test fun badIdsAreMalformedNeverExceptions() {
        assertIs<DeepLink.Malformed>(DeepLinkRoute.parse("notenfc", "asset", emptyList()))
        assertIs<DeepLink.Malformed>(DeepLinkRoute.parse("notenfc", "asset", listOf("nope")))
        assertIs<DeepLink.Malformed>(DeepLinkRoute.parse("notenfc", "asset", listOf(id, "schedule", id)))  // 1C: schedules not routed yet
        assertIs<DeepLink.Malformed>(DeepLinkRoute.parse("notenfc", "link", listOf(id.uppercase())))
        assertIs<DeepLink.Malformed>(DeepLinkRoute.parse("notenfc", "tag", listOf("nope")))
    }
}
