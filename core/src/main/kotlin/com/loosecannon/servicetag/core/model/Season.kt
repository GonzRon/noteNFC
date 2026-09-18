package com.loosecannon.servicetag.core.model

import java.time.DateTimeException
import java.time.LocalDate
import java.time.MonthDay
import java.time.Year

/** Pure season-window logic (spec §6). Both null = year-round; otherwise both required. */
object Season {

    sealed interface Problem {
        data object BothOrNeither : Problem
        data class BadDate(val which: String) : Problem
    }

    private val MMDD = Regex("""^\d{2}-\d{2}$""")

    fun validate(start: String?, end: String?): List<Problem> {
        val problems = mutableListOf<Problem>()
        if ((start == null) != (end == null)) problems += Problem.BothOrNeither
        if (start != null && !isValidMmdd(start)) problems += Problem.BadDate("start")
        if (end != null && !isValidMmdd(end)) problems += Problem.BadDate("end")
        return problems
    }

    /** Inclusive boundaries; `start > end` wraps the year; a non-leap-year 02-29 behaves as 02-28. */
    fun inSeason(start: String?, end: String?, today: LocalDate): Boolean {
        if (start == null && end == null) return true
        requireNotNull(start) { "start and end must both be null or both set" }
        requireNotNull(end) { "start and end must both be null or both set" }
        val resolvedStart = resolveForYear(start, today.year)
        val resolvedEnd = resolveForYear(end, today.year)
        return if (resolvedStart <= resolvedEnd) {
            today in resolvedStart..resolvedEnd
        } else {
            today >= resolvedStart || today <= resolvedEnd
        }
    }

    private fun isValidMmdd(value: String): Boolean {
        if (!MMDD.matches(value)) return false
        val month = value.substring(0, 2).toIntOrNull() ?: return false
        val day = value.substring(3, 5).toIntOrNull() ?: return false
        return try {
            MonthDay.of(month, day)
            true
        } catch (e: DateTimeException) {
            false
        }
    }

    private fun resolveForYear(mmdd: String, year: Int): LocalDate {
        val month = mmdd.substring(0, 2).toInt()
        val day = mmdd.substring(3, 5).toInt()
        val actualDay = if (month == 2 && day == 29 && !Year.isLeap(year.toLong())) 28 else day
        return LocalDate.of(year, month, actualDay)
    }
}
