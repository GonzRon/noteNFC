package com.loosecannon.servicetag.ui.setup

import com.loosecannon.servicetag.core.model.AssetId
import com.loosecannon.servicetag.core.model.DefinitionKind
import com.loosecannon.servicetag.core.model.EventKind
import com.loosecannon.servicetag.core.model.EventProfile
import com.loosecannon.servicetag.core.model.MeasurementDefinition
import com.loosecannon.servicetag.core.model.ValueType
import com.loosecannon.servicetag.core.usecase.DefinitionCommand
import com.loosecannon.servicetag.core.usecase.EventCommand
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The Readings & actions screen's state against a Room-backed [FakeGraph]: the two lists it draws,
 * the overflow's move/archive/delete, and the one refusal it has to render as a dialog rather than
 * swallow. Same shape as `AssetViewModelsTest` — a test dispatcher for `viewModelScope`, and every
 * assertion waits for a state instead of reading `value` straight after a write.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AssetSetupViewModelTest {

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

    private fun model(id: AssetId) = AssetSetupViewModel(
        graph.assets, graph.definitions, graph.profiles,
        graph.archiveDefinition, graph.deleteDefinition, graph.reorderDefinitions,
        graph.archiveProfile, graph.deleteProfile, graph.reorderProfiles,
        id,
    )

    @Test fun listsEveryReadingAndActionInSortOrder() = runTest {
        val ro = graph.createAsset.run("RO unit", "Water", templateKey = "ro_water")
        val vm = model(ro.id)
        backgroundScope.launch { vm.state.collect() }

        val state = vm.state.first { it?.definitions?.size == 4 }!!
        assertEquals("RO unit", state.assetName)
        assertEquals(
            listOf("Pre-filter TDS", "Post-membrane TDS", "Output TDS", "Rejection"),
            state.definitions.map(MeasurementDefinition::label),
        )
        assertEquals(listOf("TDS test"), state.profiles.map(EventProfile::name))
        // The derived row's sources are resolved through the same map the screen draws with.
        val rejection = state.definitions.single { it.kind == DefinitionKind.DERIVED }
        assertEquals("Pre-filter TDS", state.sourcesById.getValue(rejection.derived!!.sourceA).label)
    }

    @Test fun movingAReadingDownRewritesTheOrder() = runTest {
        val ro = graph.createAsset.run("RO unit", "Water", templateKey = "ro_water")
        val vm = model(ro.id)
        backgroundScope.launch { vm.state.collect() }
        val before = vm.state.first { it?.definitions?.size == 4 }!!.definitions

        vm.moveDefinition(before.first().id, up = false)

        val after = vm.state.first { it?.definitions?.firstOrNull()?.label == "Post-membrane TDS" }!!
        assertEquals(
            listOf("Post-membrane TDS", "Pre-filter TDS", "Output TDS", "Rejection"),
            after.definitions.map(MeasurementDefinition::label),
        )
        // The top row cannot go further up, and nothing is written when it tries.
        vm.moveDefinition(after.definitions.first().id, up = true)
        assertEquals(
            listOf("Post-membrane TDS", "Pre-filter TDS", "Output TDS", "Rejection"),
            vm.state.value!!.definitions.map(MeasurementDefinition::label),
        )
    }

    @Test fun archiveTogglesAReadingAndAnAction() = runTest {
        val ro = graph.createAsset.run("RO unit", "Water", templateKey = "ro_water")
        val vm = model(ro.id)
        backgroundScope.launch { vm.state.collect() }
        val loaded = vm.state.first { it?.definitions?.size == 4 }!!
        val output = loaded.definitions.single { it.key == "tds_output" }
        val profile = loaded.profiles.single()

        vm.archiveDefinition(output.id, archived = true)
        vm.archiveProfile(profile.id, archived = true)

        val archived = vm.state.first { s ->
            s?.definitions?.any { it.id == output.id && it.archivedAt != null } == true &&
                s.profiles.all { it.archivedAt != null }
        }!!
        // Archived rows stay on the setup screen — that is where the badge and Unarchive live.
        assertEquals(4, archived.definitions.size)

        vm.archiveDefinition(output.id, archived = false)
        assertNull(
            vm.state.first { s -> s?.definitions?.single { it.id == output.id }?.archivedAt == null }!!
                .definitions.single { it.id == output.id }.archivedAt,
        )
    }

    @Test fun deletingAReferencedReadingSurfacesTheRefusalWithItsCounts() = runTest {
        val ro = graph.createAsset.run("RO unit", "Water", templateKey = "ro_water")
        val defs = graph.definitions.forAsset(ro.id).associateBy(MeasurementDefinition::key)
        val profile = graph.profiles.forAsset(ro.id).single()
        graph.logEvent.run(
            EventCommand(
                assetId = ro.id,
                profileId = profile.id,
                kind = EventKind.MEASUREMENT,
                title = "TDS test",
                occurredOn = "2026-09-15",
                occurredTime = null,
                tzId = "UTC",
                notes = "",
                values = mapOf(
                    defs.getValue("tds_prefilter").id to "310",
                    defs.getValue("tds_post_membrane").id to "18",
                    defs.getValue("tds_output").id to "16",
                ),
                consumables = emptyList(),
            ),
        )

        val vm = model(ro.id)
        backgroundScope.launch { vm.state.collect() }
        backgroundScope.launch { vm.refusal.collect() }
        vm.state.first { it?.definitions?.size == 4 }

        vm.deleteDefinition(defs.getValue("tds_prefilter").id)

        val refusal = vm.refusal.first { it != null }!!
        assertEquals(1, refusal.measurements)
        assertEquals(listOf(defs.getValue("rejection_percent").id), refusal.derivedBy)
        assertEquals(listOf(profile.id), refusal.profiles)
        // Refused means nothing was written: the reading is still there to archive instead.
        assertEquals(4, graph.definitions.forAsset(ro.id).size)

        vm.dismissRefusal()
        assertNull(vm.refusal.value)
    }

    @Test fun deletingAnUnreferencedReadingRemovesIt() = runTest {
        val thing = graph.createAsset.run("Thing", "Misc")
        val spare = graph.saveDefinition.run(
            null,
            DefinitionCommand(
                assetId = thing.id,
                key = "",
                label = "Spare",
                unit = "",
                kind = DefinitionKind.ENTERED,
                valueType = ValueType.NUMBER,
                decimals = 1,
                rangeLow = null,
                rangeHigh = null,
                isMeter = false,
                formula = null,
                sourceA = null,
                sourceB = null,
            ),
        )
        val vm = model(thing.id)
        backgroundScope.launch { vm.state.collect() }
        backgroundScope.launch { vm.refusal.collect() }
        assertNotNull(vm.state.first { it?.definitions?.size == 1 })

        vm.deleteDefinition(spare.id)

        assertTrue(vm.state.first { it?.definitions?.isEmpty() == true }!!.definitions.isEmpty())
        assertNull(vm.refusal.value)
    }

    @Test fun deletingAnActionKeepsTheAssetAndClearsTheRow() = runTest {
        val ro = graph.createAsset.run("RO unit", "Water", templateKey = "ro_water")
        val vm = model(ro.id)
        backgroundScope.launch { vm.state.collect() }
        val profile = vm.state.first { it?.profiles?.size == 1 }!!.profiles.single()

        vm.deleteProfile(profile.id)

        // A profile is a shortcut, not data (spec §6): deleting it is always allowed.
        assertTrue(vm.state.first { it?.profiles?.isEmpty() == true }!!.profiles.isEmpty())
        assertEquals(4, vm.state.value!!.definitions.size)
    }
}
