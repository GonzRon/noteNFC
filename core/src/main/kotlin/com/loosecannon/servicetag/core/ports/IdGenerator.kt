package com.loosecannon.servicetag.core.ports

fun interface IdGenerator {
    fun newId(): String
}

object UuidGenerator : IdGenerator {
    override fun newId(): String = java.util.UUID.randomUUID().toString()
}
