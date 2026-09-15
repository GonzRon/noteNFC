package com.loosecannon.notenfc.ui.dashboard

import com.loosecannon.notenfc.core.model.Asset
import com.loosecannon.notenfc.testing.FakeGraph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The dashboard's state against a Room-backed [FakeGraph]. The nudge is the interesting part: it
 * is a fact about the preferences, not about the assets, so the test proves both that it starts
 * true and that a marked export puts it out on the next emission.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {

    private lateinit var graph: FakeGraph

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        graph = FakeGraph()
    }

    @After fun tearDown() {
        graph.close()
        Dispatchers.resetMain()
    }

    @Test fun needsBackupIsTrueUntilPrefsSayOtherwise() = runTest {
        val vm = DashboardViewModel(graph.assets, graph.prefs)
        backgroundScope.launch { vm.state.collect() }

        graph.createAsset.run("Pool pump", "Water")
        val before = vm.state.first { it.assets.isNotEmpty() }
        assertTrue(before.needsBackup)
        assertNull(before.lastBackupAt)

        // What the backup screen does after `SafBackupIO.write` returns.
        graph.prefs.markBackupExported(9_000L)
        vm.refresh()

        val after = vm.state.first { !it.needsBackup }
        assertEquals(9_000L, after.lastBackupAt)
    }

    @Test fun assetsListedActiveOnly() = runTest {
        val pump = graph.createAsset.run("Pool pump", "Water")
        graph.createAsset.run("Mower", "Yard")
        graph.archiveAsset.run(pump.id)

        val vm = DashboardViewModel(graph.assets, graph.prefs)
        backgroundScope.launch { vm.state.collect() }

        // CURRENT is where an asset lives while it is in service; archived rows are not there.
        val state = vm.state.first { it.assets.isNotEmpty() }
        assertEquals(listOf("Mower"), state.assets.map(Asset::name))
    }
}
