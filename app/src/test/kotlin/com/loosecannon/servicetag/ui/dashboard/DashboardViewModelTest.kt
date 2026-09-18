package com.loosecannon.servicetag.ui.dashboard

import com.loosecannon.servicetag.core.usecase.AssetCommand
import com.loosecannon.servicetag.testing.FakeGraph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
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

    private val scheduler = TestCoroutineScheduler()
    private lateinit var graph: FakeGraph

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher(scheduler))
        graph = FakeGraph(queryContext = StandardTestDispatcher(scheduler))
    }

    @After fun tearDown() {
        graph.close()
        Dispatchers.resetMain()
    }

    private fun viewModel() = DashboardViewModel(graph.assets, graph.prefs)

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

    /**
     * The nudge is computed, not defaulted. Asserting `state.value` with no collector would only
     * ever read the `stateIn` seed — which is `DashboardState()`, `needsBackup = false` — and would
     * pass even with the production rule inverted. So one collector stays up for the whole case and
     * has to see both answers: no nudge while there is nothing to lose, and the flip the moment
     * there is.
     */
    @Test fun anInstallWithNothingInItIsNotNudged() = runTest {
        val vm = viewModel()
        backgroundScope.launch { vm.state.collect() }

        // Nothing has ever been backed up, and there is nothing to back up: no nudge. A fresh
        // install is offered the empty state, not a chore.
        assertNull(graph.prefs.lastBackupAt)
        assertFalse(vm.state.first { it.assets.isEmpty() }.needsBackup)

        // The same collector goes the other way the moment there is something to lose — which is
        // also what proves the `false` above was computed and not just the initial state.
        graph.createAsset.run("Pool pump", "Water")
        assertTrue(vm.state.first { it.needsBackup }.assets.isNotEmpty())
    }

    @Test fun assetsListedActiveOnly() = runTest {
        val pump = graph.createAsset.run("Pool pump", "Water")
        graph.createAsset.run("Mower", "Yard")
        graph.archiveAsset.run(pump.id)

        val vm = viewModel()
        backgroundScope.launch { vm.state.collect() }

        // CURRENT is where an asset lives while it is in service; archived rows are not there.
        val state = vm.state.first { it.assets.isNotEmpty() }
        assertEquals(listOf("Mower"), state.assets.map { it.asset.name })
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
        assertEquals(listOf("Mower"), state.assets.map { it.asset.name })
        assertTrue(state.needsBackup)
    }

    /**
     * #39 — the dashboard lists systems. A component is part of one of them and is listed there,
     * on its parent's own screen; listing it here again as though it were a system of its own is
     * what the owner asked to stop. It is still counted, so the screen can say where it went.
     */
    @Test fun componentsAreNotListedUntilTheyAreSearchedFor() = runTest {
        val tub = graph.createAsset.run(AssetCommand(name = "Hot tub", category = "Water"))
        graph.createAsset.run(AssetCommand(name = "Circulation pump", parentAssetId = tub.id))

        val vm = viewModel()
        backgroundScope.launch { vm.state.collect() }

        val hidden = vm.state.first { it.assets.isNotEmpty() }
        assertEquals(listOf("Hot tub"), hidden.assets.map { it.asset.name })
        assertEquals(1, hidden.hiddenComponents)
        assertTrue(hidden.anyInService)
        assertNull("a top-level asset is nobody's component", hidden.assets.single().parentName)

        // One keystroke away, and the hit names the system it belongs to.
        vm.onQueryChange("circ")
        val found = vm.state.first { it.query == "circ" && it.assets.isNotEmpty() }
        assertEquals(listOf("Circulation pump"), found.assets.map { it.asset.name })
        assertEquals("Hot tub", found.assets.single().parentName)
    }

    /**
     * #39 — the six fields a query reaches are the ones an owner types: the name, the category the
     * Assets list already shows, and the four they read off the machine itself. The prose fields
     * are deliberately out, because a row showing a name and its parent could not explain a hit
     * buried in a paragraph.
     */
    @Test fun theQueryReachesTheFieldsSomeoneWouldType() = runTest {
        graph.createAsset.run(
            AssetCommand(
                name = "Circulation pump",
                category = "Water",
                manufacturer = "Hayward",
                model = "SP2610X15",
                serialNumber = "A1B2C3",
                location = "Pool shed",
                notes = "the impeller was replaced last spring",
            ),
        )

        val vm = viewModel()
        backgroundScope.launch { vm.state.collect() }
        vm.state.first { it.assets.isNotEmpty() }

        // Case-insensitive substring, on every one of the six.
        for (typed in listOf("circ", "WATER", "hayward", "sp2610", "b2c3", "pool shed")) {
            vm.onQueryChange(typed)
            assertEquals(
                "typing \"$typed\" should find the pump",
                1,
                vm.state.first { it.query == typed }.assets.size,
            )
        }

        // And not on the prose.
        vm.onQueryChange("impeller")
        val none = vm.state.first { it.query == "impeller" }
        assertTrue("notes are not searched", none.assets.isEmpty())
        assertTrue("but the install is not empty either", none.anyInService)
    }

    /** #39 — clearing the box puts the components back out of sight, in one call. */
    @Test fun clearingTheQueryPutsTheComponentsBackOutOfSight() = runTest {
        val tub = graph.createAsset.run(AssetCommand(name = "Hot tub", category = "Water"))
        graph.createAsset.run(AssetCommand(name = "Circulation pump", parentAssetId = tub.id))

        val vm = viewModel()
        backgroundScope.launch { vm.state.collect() }
        vm.state.first { it.assets.isNotEmpty() }

        vm.onQueryChange("circ")
        assertEquals(listOf("Circulation pump"), vm.state.first { it.query == "circ" }.assets.map { it.asset.name })

        vm.clearQuery()
        val cleared = vm.state.first { it.query.isEmpty() && it.assets.isNotEmpty() }
        assertEquals(listOf("Hot tub"), cleared.assets.map { it.asset.name })
    }

    /**
     * #39 — "nothing here yet" and "nothing matches that" are different facts. The screen can only
     * tell them apart if the state says whether there is anything in service at all.
     */
    @Test fun aSearchThatFindsNothingIsNotAFirstRun() = runTest {
        graph.createAsset.run("Mower", "Yard")

        val vm = viewModel()
        backgroundScope.launch { vm.state.collect() }
        vm.state.first { it.assets.isNotEmpty() }

        vm.onQueryChange("zzz")
        val nothing = vm.state.first { it.query == "zzz" }
        assertTrue(nothing.assets.isEmpty())
        assertTrue(nothing.anyInService)
    }
}
