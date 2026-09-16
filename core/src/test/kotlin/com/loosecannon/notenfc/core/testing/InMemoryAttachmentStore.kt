package com.loosecannon.notenfc.core.testing

import com.loosecannon.notenfc.core.ports.AttachmentStore
import com.loosecannon.notenfc.core.ports.AttachmentStorage
import com.loosecannon.notenfc.core.ports.ByteSource
import com.loosecannon.notenfc.core.ports.StoreIoException
import com.loosecannon.notenfc.core.ports.StoreState
import com.loosecannon.notenfc.core.ports.StoredBytes
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.security.MessageDigest

/**
 * The JVM half of the store contract: a map keyed by locator. `put` hashes while it copies, in
 * one place, exactly as `SafTreeAttachmentStore` does, so a use-case test can assert on a sha256
 * without an emulator. `failOnPut` lets a test force the mid-write failure `AddAttachment` has
 * to clean up after.
 */
class InMemoryAttachmentStore : AttachmentStore {
    val files = LinkedHashMap<String, ByteArray>()
    var failOnPut: String? = null
    var deletes = 0
        private set

    override suspend fun put(locator: String, source: ByteSource): StoredBytes {
        if (locator == failOnPut) throw StoreIoException("rigged put failure at $locator")
        val bytes = source.open().use { it.readBytes() }
        files[locator] = bytes
        return StoredBytes(sha256Hex(bytes), bytes.size.toLong())
    }

    override suspend fun open(locator: String): InputStream? =
        files[locator]?.let { ByteArrayInputStream(it) }

    override suspend fun exists(locator: String): Boolean = locator in files

    override suspend fun delete(locator: String) {
        deletes += 1
        files.remove(locator)
    }

    companion object {
        fun sha256Hex(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes)
                .joinToString("") { b -> "%02x".format(b) }
    }
}

/** An [AttachmentStorage] a test drives by hand: the state is a `var`, the store is the fake. */
class FakeAttachmentStorage(
    val store: InMemoryAttachmentStore = InMemoryAttachmentStore(),
    var state: StoreState = StoreState.Ready("Attachments", "com.example.provider"),
) : AttachmentStorage {
    override fun state(): StoreState = state
    override fun store(): AttachmentStore? = store.takeIf { state is StoreState.Ready }
}
