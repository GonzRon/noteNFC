package com.loosecannon.notenfc.ui.journal

import com.loosecannon.notenfc.core.journal.RangeState
import com.loosecannon.notenfc.core.model.Asset
import com.loosecannon.notenfc.core.model.AssetId
import com.loosecannon.notenfc.core.model.EventId
import com.loosecannon.notenfc.core.model.EventKind
import com.loosecannon.notenfc.core.model.EventProfile
import com.loosecannon.notenfc.core.model.MeasurementDefinition
import com.loosecannon.notenfc.core.model.ProfileId
import com.loosecannon.notenfc.core.usecase.EventCommand
import com.loosecannon.notenfc.core.usecase.FieldProblem
import com.loosecannon.notenfc.testing.FakeGraph
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
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
import java.time.Instant
import java.time.ZoneId

/**
 * The entry route and the event detail against a Room-backed [FakeGraph], in the shape
 * `AssetViewModelsTest` set: the main dispatcher is a test one for the length of each test, and
 * every assertion waits for a state rather than reading `value` straight after a write.
 *
 * A successful save is asserted on the stored row, not on `saved` read straight after `saving`
 * goes false — the flag is lowered before the emission, so the two are not the same instant. The
 * emission itself is awaited on a deferred that was already collecting before the save started.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EventEntryViewModelTest {

    private lateinit var graph: FakeGraph

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        graph = FakeGraph()
    }

    @After fun tearDown() {
        graph.close()
        Dispatchers.resetMain()
    }

    /** One hot tub with its five definitions and the Water test profile, as the seed makes them. */
    private class Spa(
        val asset: Asset,
        val definitions: Map<String, MeasurementDefinition>,
        val profile: EventProfile,
    ) {
        val id: AssetId get() = asset.id
        fun def(key: String) = definitions.getValue(key)
    }

    private suspend fun spa(): Spa {
        val asset = graph.createAsset.run("Spa", "Water", templateKey = "hot_tub")
        return Spa(
            asset = asset,
            definitions = graph.definitions.forAsset(asset.id).associateBy(MeasurementDefinition::key),
            profile = graph.profiles.forAsset(asset.id).first { it.name == "Water test" },
        )
    }

    private fun entryModel(assetId: AssetId, profileId: ProfileId?, eventId: EventId?) =
        EventEntryViewModel(
            graph.assets, graph.definitions, graph.profiles, graph.events,
            graph.logEvent, graph.updateEvent, graph.clock,
            assetId, profileId, eventId,
        )

    private fun detailModel(id: EventId) =
        EventDetailViewModel(graph.events, graph.definitions, graph.assets, graph.deleteEvent, id)

    private fun today(): String =
        Instant.ofEpochMilli(graph.now).atZone(ZoneId.systemDefault()).toLocalDate().toString()

    @Test fun newEntryLoadsProfileRowsInOrderWithDefaults() = runTest {
        graph.now = 1_789_000_000_000L
        val spa = spa()

        val vm = entryModel(spa.id, spa.profile.id, null)
        val state = vm.state.first { it.loaded }

        assertEquals("Spa", state.assetName)
        assertEquals("Water test", state.profileName)
        assertEquals("Water test", state.title)
        assertFalse(state.editing)
        assertEquals(
            listOf("pH", "Free chlorine", "Alkalinity", "Calcium hardness", "Water temperature"),
            state.fields.map { it.definition.label },
        )
        assertEquals(listOf(true, true, false, false, false), state.fields.map { it.required })
        assertTrue(state.fields.all { it.text.isEmpty() && it.problem == null })
        // The profile's four chemicals are offered, not added: a suggestion is a tap away, not used.
        assertEquals(4, state.suggestions.size)
        assertTrue(state.consumables.isEmpty())
        assertEquals(today(), state.occurredOn)
    }

    @Test fun saveWithMissingRequiredMarksRowAndDoesNotEmit() = runTest {
        val spa = spa()
        val vm = entryModel(spa.id, spa.profile.id, null)
        vm.state.first { it.loaded }

        val saved = mutableListOf<EventId>()
        backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) { vm.saved.collect { saved += it } }

        vm.save()
        val refused = vm.state.first { !it.saving && it.firstProblem != null }

        assertEquals("pH is required", refused.firstProblem)
        assertTrue(refused.fields.first().problem is FieldProblem.Required)
        assertTrue(graph.events.forAsset(spa.id).isEmpty())
        assertTrue(saved.isEmpty())
    }

    @Test fun liveStateClassifiesWhileTyping() = runTest {
        val spa = spa()
        val vm = entryModel(spa.id, spa.profile.id, null)
        vm.state.first { it.loaded }

        // 7.9 is past the top of pH's 7.2-7.8, and the badge says so before anything is saved.
        vm.onValue(spa.def("ph").id, "7.9")
        assertEquals(RangeState.HIGH, vm.state.value.fields.first().liveState)

        vm.onValue(spa.def("ph").id, "abc")
        assertNull(vm.state.value.fields.first().liveState)
    }

    @Test fun saveLogsEventAndEmits() = runTest {
        val spa = spa()
        val vm = entryModel(spa.id, spa.profile.id, null)
        val loaded = vm.state.first { it.loaded }

        // Undispatched: the collector must be registered before the save runs, because a shared
        // flow with no subscriber and no replay drops what it is given.
        val saved = CompletableDeferred<EventId>()
        backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) { saved.complete(vm.saved.first()) }

        vm.onValue(spa.def("ph").id, "7.4")
        vm.onValue(spa.def("free_chlorine").id, "2.0")
        vm.addSuggested(loaded.suggestions.first())
        vm.onConsumable(0, quantity = "1")

        vm.save()
        vm.state.first { !it.saving }

        val stored = graph.events.forAsset(spa.id).single()
        assertEquals("Water test", stored.title)
        assertEquals(2, stored.measurements.size)
        // The unit is a snapshot of the definition's at entry (§4): pH has none, chlorine has ppm.
        assertEquals(listOf("", "ppm"), stored.measurements.map { it.unit })
        assertEquals(7.4, stored.measurements.first().valueNum!!, 1e-9)
        val used = stored.consumables.single()
        assertEquals("Chlorine", used.name)
        assertEquals(1.0, used.quantity, 1e-9)
        assertEquals("oz", used.unit)

        assertEquals(stored.id, saved.await())
    }

    @Test fun saveIsGuardedWhileSaving() = runTest {
        val spa = spa()
        val vm = entryModel(spa.id, spa.profile.id, null)
        vm.state.first { it.loaded }
        vm.onValue(spa.def("ph").id, "7.4")
        vm.onValue(spa.def("free_chlorine").id, "2.0")

        // Both taps land in the same frame: the in-flight guard drops the second.
        vm.save()
        vm.save()
        vm.state.first { !it.saving }

        assertEquals(1, graph.events.forAsset(spa.id).size)
    }

    @Test fun editModePrefillsAndPreservesIds() = runTest {
        val spa = spa()
        val ph = spa.def("ph")
        val logged = graph.logEvent.run(
            EventCommand(
                assetId = spa.id,
                profileId = spa.profile.id,
                kind = EventKind.MEASUREMENT,
                title = "Water test",
                occurredOn = "2026-09-15",
                occurredTime = "14:42",
                tzId = "UTC",
                notes = "cloudy",
                values = mapOf(ph.id to "7.8", spa.def("free_chlorine").id to "2.0"),
                consumables = emptyList(),
            ),
        )

        val vm = entryModel(spa.id, null, logged.id)
        val state = vm.state.first { it.loaded }

        assertTrue(state.editing)
        assertEquals("Water test", state.profileName)
        assertEquals("2026-09-15", state.occurredOn)
        assertEquals("14:42", state.occurredTime)
        assertEquals("cloudy", state.notes)
        assertEquals("7.8", state.fields.first { it.definition.key == "ph" }.text)
        assertEquals("2.0", state.fields.first { it.definition.key == "free_chlorine" }.text)

        vm.onValue(ph.id, "7.5")
        vm.save()
        vm.state.first { !it.saving }

        val reloaded = graph.events.forAsset(spa.id).single()
        assertEquals(logged.id, reloaded.id)
        assertEquals(logged.createdAt, reloaded.createdAt)
        val before = logged.measurements.first { it.definitionId == ph.id }
        val after = reloaded.measurements.first { it.definitionId == ph.id }
        // An edit that keeps a field keeps that field's id, so history is edited, not replaced.
        assertEquals(before.id, after.id)
        assertEquals(7.5, after.valueNum!!, 1e-9)
    }

    @Test fun editKeepsMeasurementsNotInTheProfile() = runTest {
        val spa = spa()
        val alkalinity = spa.def("alkalinity")
        // Treatment's fields are pH and free chlorine only, so the alkalinity reading is an extra
        // the profile does not name — the shape an import, or a profile edited since, leaves behind.
        val treatment = graph.profiles.forAsset(spa.id).first { it.name == "Treatment" }
        val logged = graph.logEvent.run(
            EventCommand(
                assetId = spa.id,
                profileId = treatment.id,
                kind = EventKind.TREATMENT,
                title = "Treatment",
                occurredOn = "2026-09-15",
                occurredTime = null,
                tzId = "UTC",
                notes = "",
                values = mapOf(spa.def("ph").id to "7.4", alkalinity.id to "110"),
                consumables = emptyList(),
            ),
        )

        val vm = entryModel(spa.id, null, logged.id)
        val state = vm.state.first { it.loaded }

        // The carried row is shown, after the profile's own, so the value is not invisible.
        assertEquals(listOf("ph", "free_chlorine", "alkalinity"), state.fields.map { it.definition.key })
        assertEquals("110", state.fields.last().text)
        assertFalse(state.fields.last().required)

        vm.save()
        vm.state.first { !it.saving }

        val reloaded = graph.events.forAsset(spa.id).single()
        val before = logged.measurements.first { it.definitionId == alkalinity.id }
        val after = reloaded.measurements.first { it.definitionId == alkalinity.id }
        assertEquals(before.id, after.id)
        assertEquals(110.0, after.valueNum!!, 1e-9)
    }

    @Test fun deleteRemovesAndEmits() = runTest {
        val spa = spa()
        val logged = graph.logEvent.run(
            EventCommand(
                assetId = spa.id,
                profileId = spa.profile.id,
                kind = EventKind.MEASUREMENT,
                title = "Water test",
                occurredOn = "2026-09-15",
                occurredTime = null,
                tzId = "UTC",
                notes = "",
                values = mapOf(spa.def("ph").id to "7.4", spa.def("free_chlorine").id to "2.0"),
                consumables = emptyList(),
            ),
        )

        val vm = detailModel(logged.id)
        backgroundScope.launch { vm.state.collect() }
        val state = vm.state.first { it != null }!!

        assertEquals("Water test", state.event.title)
        assertEquals("Spa", state.assetName)
        assertEquals(5, state.definitions.size)

        val deleted = CompletableDeferred<Unit>()
        backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) { deleted.complete(vm.deleted.first()) }
        vm.delete()
        deleted.await()

        assertTrue(graph.events.all().isEmpty())
    }

    @Test fun missingEventFlagsMissing() = runTest {
        val vm = detailModel(EventId("nope"))
        backgroundScope.launch { vm.missing.collect() }

        assertTrue(vm.missing.first { it })
        assertNull(vm.state.value)
    }
}
