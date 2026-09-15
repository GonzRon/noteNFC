package com.loosecannon.notenfc.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class MoneyTest {

    @Test fun parsesAndFormatsTwoDigitCurrency() {
        val minor = Money.parse("1,234.5", "USD")
        assertEquals(123450L, minor)
        assertEquals("1234.50 USD", Money.format(minor!!, "USD"))
    }

    @Test fun zeroDigitCurrency() {
        assertEquals(5000L, Money.parse("5000", "JPY"))
        assertNull(Money.parse("50.5", "JPY"))
    }

    @Test fun tooManyFractionDigitsIsNull() {
        assertNull(Money.parse("12.345", "USD"))
    }

    @Test fun unresolvableCodeIsNull() {
        assertNull(Money.fractionDigits("ZZZ"))
        assertNull(Money.parse("12.34", "ZZZ"))
    }

    @Test fun leadingDotIsNull() {
        // ".5" is not a number a person typed on purpose; refuse it rather than guess "0.50".
        assertNull(Money.parse(".5", "USD"))
        assertNull(Money.parse(".", "USD"))
        assertNull(Money.parse(".", "JPY"))
    }

    @Test fun trailingDotIsNull() {
        // The mirror of the leading dot: "123." is a half-typed amount, not 123.00.
        assertNull(Money.parse("123.", "USD"))
        assertNull(Money.parse("1,234.", "USD"))
        assertNull(Money.parse("5000.", "JPY"))
    }

    @Test fun negativeMinorIsRefused() {
        // The domain never stores a negative price (AssetProblem.NegativePrice guards the edge),
        // so format does not have to invent a rendering for one.
        assertFailsWith<IllegalArgumentException> { Money.format(-1L, "USD") }
        assertFailsWith<IllegalArgumentException> { Money.format(-150L, "USD") }
        assertFailsWith<IllegalArgumentException> { Money.format(-1L, "JPY") }
        assertEquals("0.00 USD", Money.format(0L, "USD"))
    }

    @Test fun isCodeChecksShapeOnly() {
        // Three upper-case letters — a real currency is not required, and neither is it refused.
        assertEquals(true, Money.isCode("USD"))
        assertEquals(true, Money.isCode("ZZZ"))   // shaped right, unresolvable — that's fractionDigits' call
        assertEquals(false, Money.isCode("usd"))
        assertEquals(false, Money.isCode("US"))
        assertEquals(false, Money.isCode("USDD"))
        assertEquals(false, Money.isCode(""))
    }
}
