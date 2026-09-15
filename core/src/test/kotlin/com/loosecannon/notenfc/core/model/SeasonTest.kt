package com.loosecannon.notenfc.core.model

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SeasonTest {

    @Test fun yearRoundWhenBothNull() {
        assertTrue(Season.validate(null, null).isEmpty())
        assertTrue(Season.inSeason(null, null, LocalDate.of(2026, 1, 1)))
        assertTrue(Season.inSeason(null, null, LocalDate.of(2026, 7, 4)))
    }

    @Test fun bothOrNeither() {
        assertEquals(listOf(Season.Problem.BothOrNeither), Season.validate("05-01", null))
        assertEquals(listOf(Season.Problem.BothOrNeither), Season.validate(null, "09-30"))
    }

    @Test fun badMonthDay() {
        assertEquals(listOf(Season.Problem.BadDate("start")), Season.validate("13-01", "12-31"))
        assertEquals(listOf(Season.Problem.BadDate("end")), Season.validate("01-01", "02-30"))
        assertEquals(listOf(Season.Problem.BadDate("start")), Season.validate("2-1", "12-31"))
    }

    @Test fun ordinaryWindowInclusive() {
        val start = "05-01"; val end = "09-30"
        assertFalse(Season.inSeason(start, end, LocalDate.of(2026, 4, 30)))
        assertTrue(Season.inSeason(start, end, LocalDate.of(2026, 5, 1)))
        assertTrue(Season.inSeason(start, end, LocalDate.of(2026, 9, 30)))
        assertFalse(Season.inSeason(start, end, LocalDate.of(2026, 10, 1)))
    }

    @Test fun wrappingWindow() {
        val start = "10-15"; val end = "04-15"
        assertTrue(Season.inSeason(start, end, LocalDate.of(2026, 12, 1)))
        assertFalse(Season.inSeason(start, end, LocalDate.of(2026, 7, 1)))
        assertTrue(Season.inSeason(start, end, LocalDate.of(2026, 4, 15)))
        assertFalse(Season.inSeason(start, end, LocalDate.of(2026, 4, 16)))
    }

    @Test fun oneDaySeason() {
        val onlyDay = "07-04"
        assertTrue(Season.inSeason(onlyDay, onlyDay, LocalDate.of(2026, 7, 4)))
        assertFalse(Season.inSeason(onlyDay, onlyDay, LocalDate.of(2026, 7, 3)))
        assertFalse(Season.inSeason(onlyDay, onlyDay, LocalDate.of(2026, 7, 5)))
    }

    @Test fun feb29InLeapYear() {
        // 2028 is a leap year: Feb 29 exists and is the start of the window.
        val start = "02-29"; val end = "03-15"
        assertTrue(Season.inSeason(start, end, LocalDate.of(2028, 2, 29)))
        assertFalse(Season.inSeason(start, end, LocalDate.of(2028, 2, 28)))
    }

    @Test fun feb29InNonLeapYearBehavesAsFeb28() {
        // 2027 is not a leap year: a 02-29 start resolves to 02-28.
        val startBoundary = "02-29"; val laterEnd = "03-15"
        assertTrue(Season.inSeason(startBoundary, laterEnd, LocalDate.of(2027, 2, 28)))
        assertFalse(Season.inSeason(startBoundary, laterEnd, LocalDate.of(2027, 2, 27)))

        // A 02-29 end also resolves to 02-28.
        val earlierStart = "01-01"; val endBoundary = "02-29"
        assertTrue(Season.inSeason(earlierStart, endBoundary, LocalDate.of(2027, 2, 28)))
        assertFalse(Season.inSeason(earlierStart, endBoundary, LocalDate.of(2027, 3, 1)))
    }
}
