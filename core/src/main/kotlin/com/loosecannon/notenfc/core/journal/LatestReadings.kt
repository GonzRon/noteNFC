package com.loosecannon.notenfc.core.journal

import com.loosecannon.notenfc.core.model.AssetEvent
import com.loosecannon.notenfc.core.model.Measurement
import com.loosecannon.notenfc.core.model.MeasurementDefinition
import com.loosecannon.notenfc.core.model.ValueType

data class Reading(
    val definition: MeasurementDefinition,
    val measurement: Measurement?,
    val occurredOn: String?,
    val occurredTime: String?,
    /** null when there is no reading or the definition is not NUMBER. */
    val state: RangeState?,
)

/** Derived on every call from the event list; never cached (spec §4.2). */
object LatestReadings {
    fun of(definitions: List<MeasurementDefinition>, events: List<AssetEvent>): List<Reading> {
        val newestFirst = events.sortedWith(EventChronology.reversed())
        return definitions
            .filter { it.archivedAt == null }
            .sortedBy { it.sortOrder }
            .map { def ->
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
}
