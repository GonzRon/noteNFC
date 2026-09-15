package com.loosecannon.notenfc.core.model

import java.util.Currency

/** Pure minor-unit conversion (spec §4). The single owner of currency arithmetic. */
object Money {

    /** `java.util.Currency.getInstance(code).defaultFractionDigits`; null when unresolvable or negative. */
    fun fractionDigits(code: String): Int? {
        val digits = try {
            Currency.getInstance(code).defaultFractionDigits
        } catch (e: IllegalArgumentException) {
            return null
        }
        return if (digits < 0) null else digits
    }

    /** Grouping stripped, `.` decimal; null when malformed or too many fraction digits. */
    fun parse(text: String, code: String): Long? {
        val digits = fractionDigits(code) ?: return null
        val cleaned = text.trim().replace(",", "")
        if (cleaned.isEmpty()) return null
        val parts = cleaned.split(".")
        if (parts.size > 2) return null
        val wholePart = parts[0]
        val fracPart = if (parts.size == 2) parts[1] else ""
        if (wholePart.isEmpty() || !wholePart.all(Char::isDigit)) return null
        if (fracPart.length > digits) return null
        if (fracPart.isNotEmpty() && !fracPart.all(Char::isDigit)) return null
        val combined = wholePart + fracPart.padEnd(digits, '0')
        return combined.toLongOrNull()
    }

    /** `"123.45 USD"`, `"5000 JPY"` — exactly [fractionDigits] digits. */
    fun format(minor: Long, code: String): String {
        val digits = fractionDigits(code) ?: throw IllegalArgumentException("unresolvable currency: $code")
        if (digits == 0) return "$minor $code"
        val scale = tenPow(digits)
        val whole = minor / scale
        val frac = (minor % scale).toString().padStart(digits, '0')
        return "$whole.$frac $code"
    }

    private fun tenPow(digits: Int): Long {
        var result = 1L
        repeat(digits) { result *= 10L }
        return result
    }
}
