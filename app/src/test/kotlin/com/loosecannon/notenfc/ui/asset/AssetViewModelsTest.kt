package com.loosecannon.notenfc.ui.asset

import com.loosecannon.notenfc.core.model.Asset
import com.loosecannon.notenfc.core.model.AssetId
import com.loosecannon.notenfc.core.model.AssetStatus
import com.loosecannon.notenfc.core.model.PayloadFormat
import com.loosecannon.notenfc.core.model.TagBinding
import com.loosecannon.notenfc.core.model.TagId
import com.loosecannon.notenfc.core.model.TagTarget
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

        val vm = AssetDetailViewModel(graph.assets, graph.tags, graph.links, graph.archiveAsset, pump.id)
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
        val vm = AssetDetailViewModel(
            graph.assets, graph.tags, graph.links, graph.archiveAsset, AssetId("nope"),
        )
        backgroundScope.launch { vm.missing.collect() }

        assertTrue(vm.missing.first { it })
        assertEquals(null, vm.state.value)
    }
}
