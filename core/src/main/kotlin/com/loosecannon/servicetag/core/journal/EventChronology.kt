package com.loosecannon.servicetag.core.journal

import com.loosecannon.servicetag.core.model.AssetEvent

/**
 * The one ordering of events (spec §4.1): calendar date, then time with an untimed entry
 * sorting as "00:00", then creation instant, then id. Total and stable across devices; insertion
 * order and `updatedAt` never participate. "Newest" everywhere means `maxWith(EventChronology)`.
 */
object EventChronology : Comparator<AssetEvent> {
    private const val UNTIMED = "00:00"
    override fun compare(a: AssetEvent, b: AssetEvent): Int =
        compareValuesBy(a, b,
            { it.occurredOn },
            { it.occurredTime ?: UNTIMED },
            { it.createdAt },
            { it.id.value },
        )
}
