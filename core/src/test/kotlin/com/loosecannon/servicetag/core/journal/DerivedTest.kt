package com.loosecannon.servicetag.core.journal

import com.loosecannon.servicetag.core.model.*
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DerivedTest {
    private val assetId = AssetId("a1")

    private fun entered(key: String, id: String = "d-$key", valueType: ValueType = ValueType.NUMBER,
                         asset: AssetId = assetId, archivedAt: Long? = null) = MeasurementDefinition(
        id = DefinitionId(id), assetId = asset, key = key, label = key, unit = "",
        valueType = valueType, decimals = 0, rangeLow = null, rangeHigh = null, isMeter = false,
        sortOrder = 0, archivedAt = archivedAt, createdAt = 0L, updatedAt = 0L,
    )

    private val sourceA = entered("tds_prefilter")
    private val sourceB = entered("tds_post_membrane")
    private val sources = mapOf(sourceA.id to sourceA, sourceB.id to sourceB)

    private val rejection = MeasurementDefinition(
        id = DefinitionId("d-rejection"), assetId = assetId, key = "rejection_percent", label = "Rejection",
        unit = "%", valueType = ValueType.NUMBER, decimals = 1, rangeLow = null, rangeHigh = null, isMeter = false,
        sortOrder = 1, archivedAt = null, createdAt = 0L, updatedAt = 0L,
        kind = DefinitionKind.DERIVED, derived = DerivedSpec(DerivedFormula.PERCENT_DROP, sourceA.id, sourceB.id),
    )

    private fun event(id: String, on: String = "2026-09-15", vararg values: Pair<DefinitionId, Double>) = AssetEvent(
        id = EventId(id), assetId = assetId, kind = EventKind.MEASUREMENT, title = "TDS test",
        profileId = null, occurredOn = on, occurredTime = null, tzId = "UTC", notes = "",
        source = EventSource.MANUAL, sourceRef = null, createdAt = 1L, updatedAt = 1L,
        measurements = values.mapIndexed { i, (d, v) -> Measurement("m-$id-$i", d, v, null, "", i) },
        consumables = emptyList(),
    )

    @Test fun computesFromOneEvent() {
        val e = event("e1", values = arrayOf(sourceA.id to 310.0, sourceB.id to 18.0))
        val v = Derived.compute(rejection, e, sources)
        assertTrue(v != null && abs(v - 94.1935) < 0.001)
    }

    @Test fun missingSourceIsNull() {
        val e = event("e1", values = arrayOf(sourceA.id to 310.0))   // no B on this event
        assertNull(Derived.compute(rejection, e, sources))
    }

    @Test fun zeroFeedIsNull() {
        val e = event("e1", values = arrayOf(sourceA.id to 0.0, sourceB.id to 18.0))
        assertNull(Derived.compute(rejection, e, sources))
    }

    @Test fun archivedSourceIsNull() {
        val archivedA = sourceA.copy(archivedAt = 5L)
        val e = event("e1", values = arrayOf(sourceA.id to 310.0, sourceB.id to 18.0))
        assertNull(Derived.compute(rejection, e, mapOf(archivedA.id to archivedA, sourceB.id to sourceB)))
    }

    @Test fun enteredDefinitionIsNull() {
        val e = event("e1", values = arrayOf(sourceA.id to 310.0, sourceB.id to 18.0))
        assertNull(Derived.compute(sourceA, e, sources))   // sourceA is ENTERED, not DERIVED
    }

    @Test fun sourcesFromDifferentEventsAreNeverCombined() {
        val e1 = event("e1", "2026-09-10", sourceA.id to 310.0)         // A only
        val e2 = event("e2", "2026-09-15", sourceB.id to 18.0)          // newer, B only
        assertNull(Derived.compute(rejection, e1, sources))
        assertNull(Derived.compute(rejection, e2, sources))
        val readings = LatestReadings.of(listOf(sourceA, sourceB, rejection), listOf(e1, e2))
        val row = readings.first { it.definition.key == "rejection_percent" }
        assertNull(row.derivedValue)
        assertNull(row.state)
    }

    // --- derivedProblems -------------------------------------------------------------------

    @Test fun specOnEnteredIsAProblem() {
        val bad = sourceA.copy(derived = DerivedSpec(DerivedFormula.PERCENT_DROP, sourceA.id, sourceB.id))
        assertEquals(listOf(DerivedProblem.SpecOnEntered), bad.derivedProblems(sources))
        assertFalse(bad.derivedSpecValid(sources))
    }

    @Test fun missingSpecIsAProblem() {
        val bad = rejection.copy(derived = null)
        assertEquals(listOf(DerivedProblem.MissingSpec), bad.derivedProblems(sources))
    }

    @Test fun sameSourceIsAProblem() {
        val bad = rejection.copy(derived = DerivedSpec(DerivedFormula.PERCENT_DROP, sourceA.id, sourceA.id))
        assertTrue(DerivedProblem.SameSource in bad.derivedProblems(sources))
    }

    @Test fun sourceFromAnotherAssetIsAProblem() {
        val otherAssetSource = sourceB.copy(assetId = AssetId("a2"))
        val bad = rejection.copy(derived = DerivedSpec(DerivedFormula.PERCENT_DROP, sourceA.id, otherAssetSource.id))
        val problems = bad.derivedProblems(mapOf(sourceA.id to sourceA, otherAssetSource.id to otherAssetSource))
        assertTrue(DerivedProblem.SourceOtherAsset(otherAssetSource.id) in problems)
    }

    @Test fun sourceThatIsItselfDerivedIsAProblem() {
        val derivedSource = rejection.copy(id = DefinitionId("d-other-derived"), key = "other_derived")
        val bad = rejection.copy(derived = DerivedSpec(DerivedFormula.PERCENT_DROP, sourceA.id, derivedSource.id))
        val problems = bad.derivedProblems(mapOf(sourceA.id to sourceA, derivedSource.id to derivedSource))
        assertTrue(DerivedProblem.SourceNotEntered(derivedSource.id) in problems)
    }

    @Test fun sourceThatIsTextIsAProblem() {
        val textSource = entered("notes", id = "d-notes", valueType = ValueType.TEXT)
        val bad = rejection.copy(derived = DerivedSpec(DerivedFormula.PERCENT_DROP, sourceA.id, textSource.id))
        val problems = bad.derivedProblems(mapOf(sourceA.id to sourceA, textSource.id to textSource))
        assertTrue(DerivedProblem.SourceNotNumber(textSource.id) in problems)
    }

    @Test fun sourceThatIsAMeterIsAProblem() {
        val meter = entered("engine_hours", id = "d-hours").copy(isMeter = true)
        val bad = rejection.copy(derived = DerivedSpec(DerivedFormula.PERCENT_DROP, meter.id, sourceB.id))
        val problems = bad.derivedProblems(mapOf(meter.id to meter, sourceB.id to sourceB))
        assertEquals(listOf(DerivedProblem.SourceIsMeter(meter.id)), problems)
        assertFalse(bad.derivedSpecValid(mapOf(meter.id to meter, sourceB.id to sourceB)))
    }

    @Test fun meterOnADerivedDefinitionIsAProblem() {
        val bad = rejection.copy(isMeter = true)
        assertTrue(DerivedProblem.IsMeter in bad.derivedProblems(sources))
    }

    @Test fun validDerivedSpecHasNoProblems() {
        assertTrue(rejection.derivedProblems(sources).isEmpty())
        assertTrue(rejection.derivedSpecValid(sources))
    }
}
