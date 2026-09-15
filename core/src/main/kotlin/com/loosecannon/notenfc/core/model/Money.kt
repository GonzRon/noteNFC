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

    /**
     * Grouping stripped, `.` decimal; null when malformed or too many fraction digits. A decimal
     * point needs digits on both sides of it, so a half-typed `".5"` and a half-typed `"123."` are
     * refused the same way rather than one of them quietly becoming `0.50` or `123.00`.
     */
    fun parse(text: String, code: String): Long? {
        val digits = fractionDigits(code) ?: return null
        val cleaned = text.trim().replace(",", "")
        if (cleaned.isEmpty()) return null
        val parts = cleaned.split(".")
        if (parts.size > 2) return null
        val wholePart = parts[0]
        val fracPart = if (parts.size == 2) parts[1] else ""
        if (wholePart.isEmpty() || !wholePart.all(Char::isDigit)) return null
        if (parts.size == 2 && fracPart.isEmpty()) return null   // a trailing "." — see ".5" above
        if (fracPart.length > digits) return null
        if (fracPart.isNotEmpty() && !fracPart.all(Char::isDigit)) return null
        val combined = wholePart + fracPart.padEnd(digits, '0')
        return combined.toLongOrNull()
    }

    /**
     * `"123.45 USD"`, `"5000 JPY"` — exactly [fractionDigits] digits. A negative [minor] is a
     * programming error, not a renderable amount: nothing in the domain stores one (the asset
     * editor refuses it as `NegativePrice`), and splitting a negative into whole and fractional
     * parts produces nonsense like `"-1.-50"`.
     */
    fun format(minor: Long, code: String): String {
        require(minor >= 0) { "a price cannot be negative: $minor" }
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

/** Three upper-case letters — the shape of an ISO 4217 code, not whether one exists (that's [Money.fractionDigits]). */
fun Money.isCode(code: String): Boolean = CURRENCY_SHAPE.matches(code)

private val CURRENCY_SHAPE = Regex("""^[A-Z]{3}$""")
