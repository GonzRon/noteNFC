package com.loosecannon.servicetag.core.journal

enum class RangeState { LOW, IN_RANGE, HIGH, NO_TARGET }

/** Bounds are inclusive. */
fun classify(value: Double, low: Double?, high: Double?): RangeState =
    when {
        low == null && high == null -> RangeState.NO_TARGET
        low != null && value < low -> RangeState.LOW
        high != null && value > high -> RangeState.HIGH
        else -> RangeState.IN_RANGE
    }
