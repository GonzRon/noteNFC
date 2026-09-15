package com.loosecannon.notenfc.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
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
}
