package com.loosecannon.notenfc.core.ports

fun interface Clock {
    fun nowMillis(): Long
}
