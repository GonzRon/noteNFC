package com.loosecannon.servicetag.core.ports

fun interface Clock {
    fun nowMillis(): Long
}
