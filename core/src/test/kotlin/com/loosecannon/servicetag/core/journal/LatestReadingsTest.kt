package com.loosecannon.servicetag.core.journal

import com.loosecannon.servicetag.core.model.*
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LatestReadingsTest {
    private val ph = MeasurementDefinition(
        id = DefinitionId("d-ph"), assetId = AssetId("a1"), key = "ph", label = "pH", unit = "",
        valueType = ValueType.NUMBER, decimals = 1, rangeLow = 7.2, rangeHigh = 7.8, isMeter = false,
        sortOrder = 0, archivedAt = null, createdAt = 0L, updatedAt = 0L,
    )
    private val temp = ph.copy(id = DefinitionId("d-t"), key = "water_temp", label = "Water temperature", unit = "°F", rangeLow = null, rangeHigh = null, sortOrder = 1)
    private val archived = ph.copy(id = DefinitionId("d-x"), key = "old", archivedAt = 1L, sortOrder = 2)

    private val sourceA = ph.copy(id = DefinitionId("d-a"), key = "tds_prefilter", rangeLow = null, rangeHigh = null, sortOrder = 3)
    private val sourceB = ph.copy(id = DefinitionId("d-b"), key = "tds_post_membrane", rangeLow = null, rangeHigh = null, sortOrder = 4)
    private val rejection = MeasurementDefinition(
        id = DefinitionId("d-rejection"), assetId = AssetId("a1"), key = "rejection_percent", label = "Rejection",
        unit = "%", valueType = ValueType.NUMBER, decimals = 1, rangeLow = null, rangeHigh = null, isMeter = false,
        sortOrder = 5, archivedAt = null, createdAt = 0L, updatedAt = 0L,
        kind = DefinitionKind.DERIVED, derived = DerivedSpec(DerivedFormula.PERCENT_DROP, sourceA.id, sourceB.id),
    )

    private fun event(id: String, on: String, created: Long, vararg values: Pair<DefinitionId, Double>) = AssetEvent(
        id = EventId(id), assetId = AssetId("a1"), kind = EventKind.MEASUREMENT, title = "Water test",
        profileId = null, occurredOn = on, occurredTime = null, tzId = "UTC", notes = "",
        source = EventSource.MANUAL, sourceRef = null, createdAt = created, updatedAt = created,
        measurements = values.mapIndexed { i, (d, v) -> Measurement("m-$id-$i", d, v, null, "", i) },
        consumables = emptyList(),
    )

    @Test fun newestByOccurrenceWinsNotByInsertion() {
        val events = listOf(
            event("e1", "2026-09-12", 1L, ph.id to 7.4),
            event("e2", "2026-09-15", 2L, ph.id to 7.8),
            event("e3", "2026-09-10", 3L, ph.id to 7.0),   // backdated, entered last
        )
        val r = LatestReadings.of(listOf(ph, temp, archived), events)
        assertEquals(listOf("ph", "water_temp"), r.map { it.definition.key })   // archived omitted, sortOrder kept
        assertEquals(7.8, r[0].measurement?.valueNum)
        assertEquals("2026-09-15", r[0].occurredOn)
        assertEquals(RangeState.IN_RANGE, r[0].state)   // 7.8 == rangeHigh; bounds are inclusive (spec §4.3, RangeStateTest.boundsAreInclusive)
        assertNull(r[1].measurement); assertNull(r[1].state)                   // no reading yet
    }

    @Test fun deletingNewestFallsBackToPrevious() {
        val e1 = event("e1", "2026-09-12", 1L, ph.id to 7.4)
        val e2 = event("e2", "2026-09-15", 2L, ph.id to 7.8)
        assertEquals(7.8, LatestReadings.of(listOf(ph), listOf(e1, e2))[0].measurement?.valueNum)
        assertEquals(7.4, LatestReadings.of(listOf(ph), listOf(e1))[0].measurement?.valueNum)
    }

    @Test fun editingNewestDateBackwardsChangesWhichIsCurrent() {
        val e1 = event("e1", "2026-09-12", 1L, ph.id to 7.4)
        val e2 = event("e2", "2026-09-15", 2L, ph.id to 7.8)
        val moved = e2.copy(occurredOn = "2026-09-01")
        assertEquals(7.4, LatestReadings.of(listOf(ph), listOf(e1, moved))[0].measurement?.valueNum)
    }

    @Test fun editingValueShowsNewValueAndNoTargetState() {
        val e = event("e", "2026-09-15", 1L, temp.id to 100.0)
        val r = LatestReadings.of(listOf(temp), listOf(e.copy(measurements = listOf(e.measurements[0].copy(valueNum = 102.0)))))
        assertEquals(102.0, r[0].measurement?.valueNum)
        assertEquals(RangeState.NO_TARGET, r[0].state)
    }

    @Test fun eventWithoutThatDefinitionIsSkipped() {
        val e1 = event("e1", "2026-09-12", 1L, ph.id to 7.4)
        val e2 = event("e2", "2026-09-15", 2L, temp.id to 100.0)   // newer but no pH
        assertEquals(7.4, LatestReadings.of(listOf(ph), listOf(e1, e2))[0].measurement?.valueNum)
    }

    @Test fun derivedRowComesFromNewestComputableEvent() {
        val e1 = event("e1", "2026-09-12", 1L, sourceA.id to 310.0, sourceB.id to 18.0)
        val e2 = event("e2", "2026-09-15", 2L, sourceA.id to 305.0)   // newer, only A: not computable
        val r = LatestReadings.of(listOf(sourceA, sourceB, rejection), listOf(e1, e2))
        val row = r.first { it.definition.key == "rejection_percent" }
        assertNull(row.measurement)
        val v = row.derivedValue
        assertTrue(v != null && abs(v - 94.1935) < 0.001)
        assertEquals("2026-09-12", row.occurredOn)   // from event 1, not the newer non-computable event 2
        assertEquals(RangeState.NO_TARGET, row.state)
    }
}
