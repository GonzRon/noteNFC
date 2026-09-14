package com.loosecannon.notenfc.core.ports

/**
 * One backup destination or source, already chosen by the caller — in `:app` that is a SAF URI.
 * The port deals in whole byte arrays: a backup is small and must be written or read atomically.
 */
interface BackupIO {
    suspend fun write(bytes: ByteArray)
    suspend fun read(): ByteArray
}
