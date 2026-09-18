package com.loosecannon.servicetag.core.journal

import com.loosecannon.servicetag.core.model.AssetEvent
import com.loosecannon.servicetag.core.model.DefinitionId
import com.loosecannon.servicetag.core.model.DefinitionKind
import com.loosecannon.servicetag.core.model.Measurement
import com.loosecannon.servicetag.core.model.MeasurementDefinition
import com.loosecannon.servicetag.core.model.ValueType

data class Reading(
    val definition: MeasurementDefinition,
    val measurement: Measurement?,
    val occurredOn: String?,
    val occurredTime: String?,
    /** null when there is no reading or the definition is not NUMBER. */
    val state: RangeState?,
    /** DERIVED rows only; [measurement] is always null for those (spec §5). Nothing here is stored. */
    val derivedValue: Double? = null,
)

/** Derived on every call from the event list; never cached (spec §4.2). */
object LatestReadings {
    fun of(definitions: List<MeasurementDefinition>, events: List<AssetEvent>): List<Reading> {
        val newestFirst = events.sortedWith(EventChronology.reversed())
        val byId = definitions.associateBy { it.id }
        return definitions
            .filter { it.archivedAt == null }
            .sortedBy { it.sortOrder }
            .map { def ->
                if (def.kind == DefinitionKind.DERIVED) return@map derivedReading(def, newestFirst, byId)
                var found: Pair<AssetEvent, Measurement>? = null
                for (e in newestFirst) {
                    val m = e.measurements.firstOrNull { it.definitionId == def.id }
                    if (m != null) { found = e to m; break }
                }
                val (event, m) = found ?: return@map Reading(def, null, null, null, null)
                val state = if (def.valueType == ValueType.NUMBER && m.valueNum != null)
                    classify(m.valueNum, def.rangeLow, def.rangeHigh) else null
                Reading(def, m, event.occurredOn, event.occurredTime, state)
            }
    }

    /** The newest event (by [EventChronology]) for which [Derived.compute] is non-null; never combines two events. */
    private fun derivedReading(
        def: MeasurementDefinition,
        newestFirst: List<AssetEvent>,
        sources: Map<DefinitionId, MeasurementDefinition>,
    ): Reading {
        for (e in newestFirst) {
            val value = Derived.compute(def, e, sources) ?: continue
            val state = classify(value, def.rangeLow, def.rangeHigh)
            return Reading(def, null, e.occurredOn, e.occurredTime, state, derivedValue = value)
        }
        return Reading(def, null, null, null, null, derivedValue = null)
    }
}
