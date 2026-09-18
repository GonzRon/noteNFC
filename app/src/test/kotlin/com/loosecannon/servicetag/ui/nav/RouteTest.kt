package com.loosecannon.servicetag.ui.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `TopLevelRoutes` is what the bottom bar iterates over (2B-2, D12 §16 correction): Scan is gone
 * from the list, so a regression that re-adds it — or reorders Dashboard/Assets — shows up here
 * on the JVM without a device.
 */
class RouteTest {

    @Test fun topLevelRoutesIsDashboardThenAssetsOnly() {
        assertEquals(listOf(Route.Dashboard, Route.Assets), TopLevelRoutes)
    }

    /**
     * 2.6 — the write route still carries a target *kind* as a string, because a serialised back
     * stack can hold one written by 2.5. "link" is no longer a kind this app writes, and it is
     * refused here, at the boundary, rather than inside a screen that would have to decide what a
     * link write means.
     */
    @Test fun aLinkKindedWriteRouteIsNotSupported() {
        assertFalse(Route.WriteTag("link", "l1", null).isSupported())
        assertTrue(Route.WriteTag("asset", "a1", null).isSupported())
        assertTrue(Route.WriteTag("none", null, null).isSupported())
    }
}
