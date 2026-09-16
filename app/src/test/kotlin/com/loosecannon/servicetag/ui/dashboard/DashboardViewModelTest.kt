package com.loosecannon.servicetag.ui.dashboard

import com.loosecannon.servicetag.core.model.Asset
import com.loosecannon.servicetag.core.model.ExternalLink
import com.loosecannon.servicetag.core.model.LinkId
import com.loosecannon.servicetag.core.model.LinkKind
import com.loosecannon.servicetag.testing.FakeGraph
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The dashboard's state against a Room-backed [FakeGraph]. The nudge is the interesting part: it
 * is a fact about the preferences *and* about whether there is anything to lose, so these tests
 * pin all three of its states — true with data and no backup, out after an export is marked, and
 * never on an install with nothing in it.
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

    private fun viewModel() = DashboardViewModel(graph.assets, graph.links, graph.prefs)

    /** A link with no asset behind it: data the backup carries, and the only data on this install. */
    private fun standaloneLink() = ExternalLink(
        id = LinkId("link-1"),
        assetId = null,
        kind = LinkKind.WEB,
        label = "Manual",
        uri = "https://example.invalid/manual",
        createdAt = 1L,
        lastOpenedAt = null,
        updatedAt = 1L,
    )

    @Test fun needsBackupIsTrueUntilPrefsSayOtherwise() = runTest {
        val vm = viewModel()
        backgroundScope.launch { vm.state.collect() }

        graph.createAsset.run("Pool pump", "Water")
        val before = vm.state.first { it.assets.isNotEmpty() }
        assertTrue(before.needsBackup)
        assertNull(before.lastBackupAt)

        // What the backup screen does once both files of a set have landed.
        graph.prefs.markBackupExported(9_000L)
        vm.refresh()

        val after = vm.state.first { !it.needsBackup }
        assertEquals(9_000L, after.lastBackupAt)
    }

    @Test fun emptyStoreNeverNeedsABackup() = runTest {
        val vm = viewModel()
        backgroundScope.launch { vm.state.collect() }

        // Nothing has ever been backed up, and there is nothing to back up: no nudge. A fresh
        // install is offered the empty state, not a chore.
        assertNull(graph.prefs.lastBackupAt)
        assertFalse(vm.state.first { it.assets.isEmpty() }.needsBackup)

        // The same collector goes the other way the moment there is something to lose — which is
        // also what proves the `false` above was computed and not just the initial state.
        graph.links.upsert(standaloneLink())
        assertTrue(vm.state.first { it.needsBackup }.assets.isEmpty())
    }

    @Test fun assetsListedActiveOnly() = runTest {
        val pump = graph.createAsset.run("Pool pump", "Water")
        graph.createAsset.run("Mower", "Yard")
        graph.archiveAsset.run(pump.id)

        val vm = viewModel()
        backgroundScope.launch { vm.state.collect() }

        // CURRENT is where an asset lives while it is in service; archived rows are not there.
        val state = vm.state.first { it.assets.isNotEmpty() }
        assertEquals(listOf("Mower"), state.assets.map(Asset::name))
    }

    /**
     * A retired asset is out of service exactly as an archived one is (spec §7), so CURRENT does
     * not list it — but it is still data a phone change would lose, so the nudge rule is unchanged.
     */
    @Test fun dashboardExcludesRetired() = runTest {
        val pump = graph.createAsset.run("Pool pump", "Water")
        graph.createAsset.run("Mower", "Yard")
        graph.retireAsset.retire(pump.id, "2026-04-02")

        val vm = viewModel()
        backgroundScope.launch { vm.state.collect() }

        val state = vm.state.first { it.assets.isNotEmpty() }
        assertEquals(listOf("Mower"), state.assets.map(Asset::name))
        assertTrue(state.needsBackup)
    }
}
