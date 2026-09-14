package com.loosecannon.notenfc.core.ports

fun interface IdGenerator {
    fun newId(): String
}

object UuidGenerator : IdGenerator {
    override fun newId(): String = java.util.UUID.randomUUID().toString()
}
