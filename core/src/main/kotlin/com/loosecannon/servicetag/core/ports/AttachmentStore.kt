package com.loosecannon.servicetag.core.ports

import java.io.InputStream

/** One byte source, opened when the store is ready to read it. Never a `Uri` in `:core`. */
fun interface ByteSource { fun open(): InputStream }

/** What the store actually saw. The row records this, so bytes and metadata cannot disagree. */
data class StoredBytes(val sha256: String, val sizeBytes: Long)

/** A store that could not do what it was asked. Not a domain refusal — a broken destination. */
class StoreIoException(message: String, cause: Throwable? = null) :
    java.io.IOException(message, cause)

interface AttachmentStore {
    /** Streams [source] into [locator] (creating parents), returns the bytes' sha256 and size. */
    suspend fun put(locator: String, source: ByteSource): StoredBytes
    suspend fun open(locator: String): InputStream?        // null when absent
    suspend fun exists(locator: String): Boolean
    suspend fun delete(locator: String)                    // absent is not an error
}

sealed interface StoreState {
    data object NotConfigured : StoreState
    data class Ready(val displayName: String, val authority: String) : StoreState
    data class AccessLost(val displayName: String) : StoreState
}

/** The app-level resolver that owns the tree preference and hands out a store when it can. */
interface AttachmentStorage {
    fun state(): StoreState
    fun store(): AttachmentStore?       // null unless Ready
}
