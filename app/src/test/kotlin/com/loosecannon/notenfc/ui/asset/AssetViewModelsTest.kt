package com.loosecannon.notenfc.ui.asset

import com.loosecannon.notenfc.core.journal.RangeState
import com.loosecannon.notenfc.core.model.Asset
import com.loosecannon.notenfc.core.model.AssetId
import com.loosecannon.notenfc.core.model.AssetStatus
import com.loosecannon.notenfc.core.model.DefinitionId
import com.loosecannon.notenfc.core.model.EventKind
import com.loosecannon.notenfc.core.model.EventProfile
import com.loosecannon.notenfc.core.model.MeasurementDefinition
import com.loosecannon.notenfc.core.model.PayloadFormat
import com.loosecannon.notenfc.core.model.ProfileId
import com.loosecannon.notenfc.core.model.TagBinding
import com.loosecannon.notenfc.core.model.TagId
import com.loosecannon.notenfc.core.model.TagTarget
import com.loosecannon.notenfc.core.usecase.EventCommand
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The three asset ViewModels against a Room-backed [FakeGraph]. `viewModelScope` dispatches on
 * `Dispatchers.Main`, so the main dispatcher is a test one for the length of each test; the
 * repositories still emit on their own query context, which is why every assertion waits for a
 * state rather than reading `value` straight after a write.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AssetViewModelsTest {

    private lateinit var graph: FakeGraph

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        graph = FakeGraph()
    }

    @After fun tearDown() {
        graph.close()
        Dispatchers.resetMain()
    }

    private fun tag(id: String, assetId: String) = TagBinding(
        id = TagId(id),
        payloadFormat = PayloadFormat.V1,
        payloadKey = "key-$id",
        target = TagTarget.AssetTarget(AssetId(assetId)),
        createdAt = 1L,
        updatedAt = 1L,
    )

    /** The detail model takes nine collaborators; every test wants the same nine off the graph. */
    private fun detailModel(id: AssetId) = AssetDetailViewModel(
        graph.assets, graph.tags, graph.links,
        graph.definitions, graph.profiles, graph.events,
        graph.archiveAsset, graph.applyTemplate, id,
    )

    private fun waterTest(
        assetId: AssetId,
        profileId: ProfileId,
        on: String,
        values: Map<DefinitionId, String>,
    ) = EventCommand(
        assetId = assetId,
        profileId = profileId,
        kind = EventKind.MEASUREMENT,
        title = "Water test",
        occurredOn = on,
        occurredTime = null,
        tzId = "UTC",
        notes = "",
        values = values,
        consumables = emptyList(),
    )

    @Test fun theListEmitsAfterACreateAndHidesArchivedRowsUntilTheChipIsOn() = runTest {
        val vm = AssetsViewModel(graph.assets)
        backgroundScope.launch { vm.state.collect() }

        val pump = graph.createAsset.run("Pool pump", "Water")
        assertEquals(listOf("Pool pump"), vm.state.first { it.items.isNotEmpty() }.items.map(Asset::name))

        graph.archiveAsset.run(pump.id)
        val hidden = vm.state.first { it.items.isEmpty() }
        assertFalse(hidden.showArchived)
        // The empty list still knows why it is empty, so the screen can say so.
        assertEquals(1, hidden.archivedCount)

        vm.toggleArchived()
        val shown = vm.state.first { it.items.isNotEmpty() }
        assertTrue(shown.showArchived)
        assertEquals(AssetStatus.ARCHIVED, shown.items.single().status)
    }

    @Test fun theDetailExposesOnlyTheTagsBoundToThisAsset() = runTest {
        val pump = graph.createAsset.run("Pool pump", "Water")
        val mower = graph.createAsset.run("Mower", "Yard")
        graph.tags.upsert(tag("t-pump", pump.id.value))
        graph.tags.upsert(tag("t-mower", mower.id.value))

        val vm = detailModel(pump.id)
        backgroundScope.launch { vm.state.collect() }

        val state = vm.state.first { it != null && it.tags.isNotEmpty() }!!
        assertEquals("Pool pump", state.asset.name)
        assertEquals(listOf("t-pump"), state.tags.map { it.id.value })
        assertTrue(state.links.isEmpty())

        vm.archive()
        assertEquals(AssetStatus.ARCHIVED, vm.state.first { it?.asset?.status == AssetStatus.ARCHIVED }!!.asset.status)
    }

    @Test fun savingABlankNameFailsAndWritesNothing() = runTest {
        val vm = AssetEditViewModel(graph.assets, graph.createAsset, graph.updateAsset, null)
        val saved = mutableListOf<AssetId>()
        backgroundScope.launch { vm.saved.collect { saved += it } }
        vm.onName("   ")
        vm.onCategory("Water")

        vm.save()
        vm.state.first { !it.saving }
        assertTrue(vm.state.value.nameError)
        assertTrue(graph.assets.all().isEmpty())
        assertTrue(saved.isEmpty())

        vm.onName("Hot tub")
        assertFalse(vm.state.value.nameError)
        vm.save()
        // The second tap lands in the same frame as the first: the in-flight guard drops it, so
        // one asset exists afterwards, not two.
        vm.save()
        vm.state.first { !it.saving }
        assertEquals(listOf("Hot tub"), graph.assets.all().map(Asset::name))
        assertEquals(graph.assets.all().single().id, saved.single())
    }

    @Test fun missingIsTrueForAnUnknownId() = runTest {
        val vm = detailModel(AssetId("nope"))
        backgroundScope.launch { vm.missing.collect() }

        assertTrue(vm.missing.first { it })
        assertEquals(null, vm.state.value)
    }

    @Test fun detailStateDerivesReadingsFromEvents() = runTest {
        val spa = graph.createAsset.run("Spa", "Water", templateKey = "hot_tub")
        val defs = graph.definitions.forAsset(spa.id).associateBy(MeasurementDefinition::key)
        val profile = graph.profiles.forAsset(spa.id).first { it.name == "Water test" }
        val ph = defs.getValue("ph")
        val chlorine = defs.getValue("free_chlorine")

        val vm = detailModel(spa.id)
        backgroundScope.launch { vm.state.collect() }

        graph.logEvent.run(
            waterTest(spa.id, profile.id, "2026-09-12", mapOf(ph.id to "7.4", chlorine.id to "2.0")),
        )
        val newer = graph.logEvent.run(
            waterTest(spa.id, profile.id, "2026-09-15", mapOf(ph.id to "7.8", chlorine.id to "3.4")),
        )

        val both = vm.state.first { it?.events?.size == 2 }!!
        // The newest event by §4.1 supplies the current reading, whatever order it was written in.
        assertEquals("pH", both.readings.first().definition.label)
        assertEquals(7.8, both.readings.first().measurement?.valueNum!!, 1e-9)
        // 7.8 is the top of 7.2-7.8 and the bounds are inclusive (§4.3), so the row is in range;
        // the chlorine reading is the one genuinely past its bound.
        assertEquals(RangeState.IN_RANGE, both.readings.first().state)
        assertEquals(RangeState.HIGH, both.readings.first { it.definition.key == "free_chlorine" }.state)
        // Nothing has been logged for water temperature, so it is a reading with no value.
        assertEquals(null, both.readings.first { it.definition.key == "water_temp" }.measurement)

        graph.deleteEvent.run(newer.id)
        val one = vm.state.first { it?.events?.size == 1 }!!
        assertEquals(7.4, one.readings.first().measurement?.valueNum!!, 1e-9)
    }

    @Test fun detailStateListsUnarchivedProfilesInOrder() = runTest {
        val spa = graph.createAsset.run("Spa", "Water", templateKey = "hot_tub")
        val vm = detailModel(spa.id)
        backgroundScope.launch { vm.state.collect() }

        val ordered = vm.state.first { it?.profiles?.isNotEmpty() == true }!!
        assertEquals(listOf("Water test", "Treatment"), ordered.profiles.map(EventProfile::name))

        val treatment = graph.profiles.forAsset(spa.id).first { it.name == "Treatment" }
        graph.profiles.upsert(treatment.copy(archivedAt = 9_000L))
        // An archived profile keeps its history but stops offering a quick action.
        assertEquals(
            listOf("Water test"),
            vm.state.first { it?.profiles?.size == 1 }!!.profiles.map(EventProfile::name),
        )
    }

    @Test fun setUpFromTemplateOnAPlainAsset() = runTest {
        val ups = graph.createAsset.run("Rack UPS", "Power")
        val vm = detailModel(ups.id)
        backgroundScope.launch { vm.state.collect() }
        assertTrue(vm.state.first { it != null }!!.definitions.isEmpty())

        vm.setUpFromTemplate("ups")

        val ready = vm.state.first { it?.definitions?.size == 4 && it.profiles.size == 2 }!!
        assertEquals("Battery voltage", ready.definitions.first().label)
        assertEquals(listOf("Load test", "Battery replacement"), ready.profiles.map(EventProfile::name))
    }

    @Test fun newAssetFormPassesTemplateKey() = runTest {
        val vm = AssetEditViewModel(graph.assets, graph.createAsset, graph.updateAsset, null)
        vm.onName("Spa")
        vm.onTemplate("hot_tub")
        assertEquals("hot_tub", vm.state.value.templateKey)

        vm.save()
        vm.state.first { !it.saving }

        // The form only names a template; seeding it is the create's own transaction (§7).
        val id = graph.assets.all().single().id
        assertEquals(5, graph.definitions.forAsset(id).size)
        assertEquals(listOf("Water test", "Treatment"), graph.profiles.forAsset(id).map(EventProfile::name))
    }

    @Test fun newAssetDefaultsToNoTemplateAndCanBeSetUpLater() = runTest {
        val form = AssetEditViewModel(graph.assets, graph.createAsset, graph.updateAsset, null)

        // None is the default: a new asset starts bare and is set up when the user knows what it is.
        assertEquals(null, form.state.value.templateKey)
        form.onName("Thing")
        form.save()
        form.state.first { !it.saving }

        val id = graph.assets.all().single { it.name == "Thing" }.id
        assertTrue(graph.definitions.forAsset(id).isEmpty())
        assertTrue(graph.profiles.forAsset(id).isEmpty())

        val detail = detailModel(id)
        backgroundScope.launch { detail.state.collect() }
        detail.setUpFromTemplate("ups")
        assertEquals(4, detail.state.first { it?.definitions?.size == 4 }!!.definitions.size)

        // Generic is a choice of its own, not the default: one profile and no definitions.
        val explicit = AssetEditViewModel(graph.assets, graph.createAsset, graph.updateAsset, null)
        explicit.onName("Ladder")
        explicit.onTemplate("generic")
        explicit.save()
        explicit.state.first { !it.saving }

        val ladder = graph.assets.all().single { it.name == "Ladder" }.id
        assertTrue(graph.definitions.forAsset(ladder).isEmpty())
        assertEquals(listOf("Note"), graph.profiles.forAsset(ladder).map(EventProfile::name))
    }
}
