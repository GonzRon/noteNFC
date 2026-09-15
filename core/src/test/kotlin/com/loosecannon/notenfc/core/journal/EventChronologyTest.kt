package com.loosecannon.notenfc.core.journal

import com.loosecannon.notenfc.core.model.*
import kotlin.test.Test
import kotlin.test.assertEquals

class EventChronologyTest {
    private fun event(id: String, on: String, time: String? = null, created: Long = 0L) = AssetEvent(
        id = EventId(id), assetId = AssetId("a1"), kind = EventKind.NOTE, title = id,
        profileId = null, occurredOn = on, occurredTime = time, tzId = "UTC", notes = "",
        source = EventSource.MANUAL, sourceRef = null, createdAt = created, updatedAt = created,
        measurements = emptyList(), consumables = emptyList(),
    )

    @Test fun laterDateIsNewerRegardlessOfCreation() {
        val backdated = event("old", "2026-09-10", created = 2_000L)   // entered later
        val newer = event("new", "2026-09-15", created = 1_000L)
        assertEquals(listOf(backdated, newer), listOf(newer, backdated).sortedWith(EventChronology))
    }

    @Test fun timedEntrySortsAfterUntimedOnSameDay() {
        val untimed = event("u", "2026-09-15", null, created = 9_000L)
        val timed = event("t", "2026-09-15", "00:00", created = 1L)
        // "00:00" ties with the null default, so created_at decides: t (1L) before u (9000L)
        assertEquals(listOf(timed, untimed), listOf(untimed, timed).sortedWith(EventChronology))
        val afternoon = event("p", "2026-09-15", "14:30", created = 1L)
        assertEquals(listOf(untimed, afternoon), listOf(afternoon, untimed).sortedWith(EventChronology))
    }

    @Test fun sameDayNoTimeOrdersByCreatedAtThenId() {
        val a = event("b", "2026-09-15", created = 5L)
        val b = event("a", "2026-09-15", created = 5L)
        val c = event("c", "2026-09-15", created = 4L)
        assertEquals(listOf(c, b, a), listOf(a, b, c).sortedWith(EventChronology))
    }

    @Test fun newestIsMaxWith() {
        val events = listOf(event("x", "2026-01-01"), event("y", "2026-03-01"), event("z", "2026-02-01"))
        assertEquals("y", events.maxWith(EventChronology).id.value)
    }
}
