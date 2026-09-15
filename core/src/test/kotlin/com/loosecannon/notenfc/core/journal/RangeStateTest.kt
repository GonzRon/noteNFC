package com.loosecannon.notenfc.core.journal

import kotlin.test.Test
import kotlin.test.assertEquals

class RangeStateTest {
    @Test fun noBoundsIsNoTarget() = assertEquals(RangeState.NO_TARGET, classify(7.8, null, null))
    @Test fun boundsAreInclusive() {
        assertEquals(RangeState.IN_RANGE, classify(7.2, 7.2, 7.8))
        assertEquals(RangeState.IN_RANGE, classify(7.8, 7.2, 7.8))
    }
    @Test fun belowLowIsLow() = assertEquals(RangeState.LOW, classify(7.1, 7.2, 7.8))
    @Test fun aboveHighIsHigh() = assertEquals(RangeState.HIGH, classify(7.9, 7.2, 7.8))
    @Test fun oneSidedRanges() {
        assertEquals(RangeState.IN_RANGE, classify(500.0, 80.0, null))
        assertEquals(RangeState.LOW, classify(10.0, 80.0, null))
        assertEquals(RangeState.HIGH, classify(500.0, null, 120.0))
        assertEquals(RangeState.IN_RANGE, classify(0.0, null, 120.0))
    }
}
