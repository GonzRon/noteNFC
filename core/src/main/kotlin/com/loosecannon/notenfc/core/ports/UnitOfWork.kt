package com.loosecannon.notenfc.core.ports

interface UnitOfWork {
    /**
     * Runs [block] as one write transaction: everything it does commits together or not at all.
     */
    suspend fun <T> write(block: suspend () -> T): T

    /**
     * Runs [block] as one read transaction. Every repository read inside [block] observes the
     * same snapshot of the database, so a multi-table read (a backup export, say) is one
     * consistent point in time and cannot straddle a concurrent write.
     *
     * Writing inside [block] is illegal; an implementation is free to refuse it.
     */
    suspend fun <T> read(block: suspend () -> T): T
}
