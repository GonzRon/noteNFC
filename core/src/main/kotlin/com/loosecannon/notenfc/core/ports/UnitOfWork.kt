package com.loosecannon.notenfc.core.ports

interface UnitOfWork {
    suspend fun <T> write(block: suspend () -> T): T
}
