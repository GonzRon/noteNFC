package com.loosecannon.notenfc.core.usecase

import com.loosecannon.notenfc.core.journal.SeedTemplates
import com.loosecannon.notenfc.core.model.*
import com.loosecannon.notenfc.core.ports.Clock
import com.loosecannon.notenfc.core.ports.IdGenerator
import com.loosecannon.notenfc.core.testing.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class EventUseCasesTest {
    private val assets = InMemoryAssetRepository()
    private val defs = InMemoryDefinitionRepository()
    private val profiles = InMemoryProfileRepository()
    private val events = InMemoryEventRepository()
    private val uow = FakeUnitOfWork(assets, defs, profiles, events)
    private var seq = 0
    private val ids = IdGenerator { "id-${++seq}" }
    private var now = 1_000L
    private val clock = Clock { now }

    private val apply = ApplyTemplate(defs, profiles, assets, uow, ids, clock)
    private val logEvent = LogEvent(events, defs, profiles, assets, uow, ids, clock)
    private val updateEvent = UpdateEvent(events, defs, profiles, uow, clock)
    private val deleteEvent = DeleteEvent(events, uow)

    private suspend fun asset(id: String, name: String): Asset =
        Asset(id = AssetId(id), name = name, createdAt = now, updatedAt = now).also { assets.upsert(it) }

    private suspend fun seedHotTub(): AssetId {
        val a = asset("a1", "Spa")
        apply.run(a.id, SeedTemplates.byKey("hot_tub")!!)
        return a.id
    }

    private suspend fun seedUps(): AssetId {
        val a = asset("a2", "UPS unit")
        apply.run(a.id, SeedTemplates.byKey("ups")!!)
        return a.id
    }

    private suspend fun defId(assetId: AssetId, key: String): DefinitionId =
        defs.forAsset(assetId).first { it.key == key }.id

    private suspend fun profileId(assetId: AssetId, name: String): ProfileId =
        profiles.forAsset(assetId).first { it.name == name }.id

    private fun cmd(
        assetId: AssetId,
        profileId: ProfileId? = null,
        kind: EventKind = EventKind.MEASUREMENT,
        title: String = "Test",
        occurredOn: String = "2026-09-15",
        occurredTime: String? = null,
        tzId: String = "UTC",
        notes: String = "",
        values: Map<DefinitionId, String> = emptyMap(),
        consumables: List<ConsumableInput> = emptyList(),
    ) = EventCommand(assetId, profileId, kind, title, occurredOn, occurredTime, tzId, notes, values, consumables)

    @Test fun logsAWaterTestWithSnapshotUnitsAndConsumables() = runTest {
        val assetId = seedHotTub()
        val waterTest = profileId(assetId, "Water test")
        val phId = defId(assetId, "ph")
        val clId = defId(assetId, "free_chlorine")

        val event = logEvent.run(
            cmd(
                assetId, profileId = waterTest,
                values = mapOf(phId to "7.8", clId to "0.8"),
                consumables = listOf(ConsumableInput("Chlorine", "1", "oz")),
            ),
        )

        assertEquals(2, event.measurements.size)
        val ph = event.measurements.first { it.definitionId == phId }
        val cl = event.measurements.first { it.definitionId == clId }
        assertEquals(7.8, ph.valueNum); assertEquals("", ph.unit)
        assertEquals(0.8, cl.valueNum); assertEquals("ppm", cl.unit)
        assertEquals(1, event.consumables.size)
        assertEquals(1.0, event.consumables[0].quantity)
        assertEquals(EventSource.MANUAL, event.source)
        assertEquals(now, event.createdAt); assertEquals(now, event.updatedAt)
    }

    @Test fun missingRequiredFieldIsReported() = runTest {
        val assetId = seedHotTub()
        val waterTest = profileId(assetId, "Water test")
        val clId = defId(assetId, "free_chlorine")
        val phId = defId(assetId, "ph")

        val ex = assertFailsWith<EventValidation> {
            logEvent.run(cmd(assetId, profileId = waterTest, values = mapOf(clId to "0.8")))
        }
        assertEquals(listOf(FieldProblem.Required(phId)), ex.problems)
        assertTrue(events.all().isEmpty())
    }

    @Test fun notANumberAndBadDateAreCollectedTogether() = runTest {
        val assetId = seedHotTub()
        val waterTest = profileId(assetId, "Water test")
        val phId = defId(assetId, "ph")
        val clId = defId(assetId, "free_chlorine")

        val ex = assertFailsWith<EventValidation> {
            logEvent.run(
                cmd(
                    assetId, profileId = waterTest, occurredOn = "15/09/2026",
                    values = mapOf(phId to "abc", clId to "1.0"),
                ),
            )
        }
        assertEquals(setOf<FieldProblem>(FieldProblem.NotANumber(phId), FieldProblem.BadDate()), ex.problems.toSet())
        assertEquals(2, ex.problems.size)
        assertTrue(events.all().isEmpty())
    }

    @Test fun booleanParsesToZeroOrOne() = runTest {
        val assetId = seedUps()
        val loadTest = profileId(assetId, "Load test")
        val passedId = defId(assetId, "test_passed")

        val trueEvent = logEvent.run(cmd(assetId, profileId = loadTest, values = mapOf(passedId to "true")))
        assertEquals(1.0, trueEvent.measurements.single().valueNum)

        val falseEvent = logEvent.run(cmd(assetId, profileId = loadTest, values = mapOf(passedId to "0")))
        assertEquals(0.0, falseEvent.measurements.single().valueNum)

        val ex = assertFailsWith<EventValidation> {
            logEvent.run(cmd(assetId, profileId = loadTest, values = mapOf(passedId to "maybe")))
        }
        assertEquals(listOf(FieldProblem.NotANumber(passedId)), ex.problems)
    }

    @Test fun blankTitleDefaultsToProfileTitle() = runTest {
        val assetId = seedHotTub()
        val waterTest = profileId(assetId, "Water test")
        val phId = defId(assetId, "ph")
        val clId = defId(assetId, "free_chlorine")

        val event = logEvent.run(
            cmd(assetId, profileId = waterTest, title = "  ", values = mapOf(phId to "7.5", clId to "1.5")),
        )
        assertEquals("Water test", event.title)
    }

    @Test fun updateKeepsIdsAndCreatedAtAndSetsUpdatedAt() = runTest {
        val assetId = seedHotTub()
        val waterTest = profileId(assetId, "Water test")
        val phId = defId(assetId, "ph")
        val clId = defId(assetId, "free_chlorine")

        val logged = logEvent.run(
            cmd(assetId, profileId = waterTest, values = mapOf(phId to "7.4", clId to "1.2")),
        )
        val originalPhMeasurementId = logged.measurements.first { it.definitionId == phId }.id

        now = 2_000L
        val updated = updateEvent.run(
            logged.id,
            cmd(assetId, profileId = waterTest, values = mapOf(phId to "7.5", clId to "1.2")),
        )

        val updatedPh = updated.measurements.first { it.definitionId == phId }
        assertEquals(originalPhMeasurementId, updatedPh.id)
        assertEquals(7.5, updatedPh.valueNum)
        assertEquals(1_000L, updated.createdAt)
        assertEquals(2_000L, updated.updatedAt)
    }

    @Test fun updateUnknownEventFails() = runTest {
        val assetId = seedHotTub()
        assertFailsWith<NoSuchEvent> {
            updateEvent.run(EventId("nope"), cmd(assetId, occurredOn = "2026-09-15"))
        }
    }

    @Test fun deleteRemovesTheAggregate() = runTest {
        val assetId = seedHotTub()
        val waterTest = profileId(assetId, "Water test")
        val phId = defId(assetId, "ph")
        val clId = defId(assetId, "free_chlorine")
        val logged = logEvent.run(cmd(assetId, profileId = waterTest, values = mapOf(phId to "7.4", clId to "1.2")))

        deleteEvent.run(logged.id)

        assertNull(events.get(logged.id))
    }

    @Test fun logAgainstUnknownAssetFails() = runTest {
        assertFailsWith<NoSuchAsset> {
            logEvent.run(cmd(AssetId("nope")))
        }
    }

    @Test fun updateCannotMoveAnEventToAnotherAsset() = runTest {
        val hotTubId = seedHotTub()
        val upsId = seedUps()
        val waterTest = profileId(hotTubId, "Water test")
        val phId = defId(hotTubId, "ph")
        val clId = defId(hotTubId, "free_chlorine")
        val logged = logEvent.run(
            cmd(hotTubId, profileId = waterTest, values = mapOf(phId to "7.4", clId to "1.2")),
        )

        assertFailsWith<EventOwnership> {
            updateEvent.run(logged.id, cmd(upsId, values = mapOf(phId to "7.6", clId to "1.2")))
        }
        assertEquals(logged, events.get(logged.id))
    }

    @Test fun profileMustBelongToTheCommandAsset() = runTest {
        val hotTubId = seedHotTub()
        val upsId = seedUps()
        val upsProfile = profileId(upsId, "Load test")

        assertFailsWith<EventOwnership> {
            logEvent.run(cmd(hotTubId, profileId = upsProfile))
        }
        assertTrue(events.all().isEmpty())
    }

    @Test fun definitionMustBelongToTheCommandAsset() = runTest {
        val hotTubId = seedHotTub()
        val upsId = seedUps()
        val batteryId = defId(upsId, "battery_voltage")

        assertFailsWith<EventOwnership> {
            logEvent.run(cmd(hotTubId, values = mapOf(batteryId to "12.5")))
        }
        assertTrue(events.all().isEmpty())
    }
}
