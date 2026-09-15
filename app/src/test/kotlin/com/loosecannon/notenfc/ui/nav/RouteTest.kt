package com.loosecannon.notenfc.ui.nav

import org.junit.Assert.assertEquals
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
}
