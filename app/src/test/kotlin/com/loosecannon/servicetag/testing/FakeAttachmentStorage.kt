package com.loosecannon.servicetag.testing

import com.loosecannon.servicetag.core.ports.AttachmentStore
import com.loosecannon.servicetag.core.ports.AttachmentStorage
import com.loosecannon.servicetag.core.ports.ByteSource
import com.loosecannon.servicetag.core.ports.StoreIoException
import com.loosecannon.servicetag.core.ports.StoreState
import com.loosecannon.servicetag.core.ports.StoredBytes
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.security.MessageDigest

/**
 * The `:app` twin of `:core`'s `InMemoryAttachmentStore` / `FakeAttachmentStorage` fixtures:
 * `:app`'s test source set cannot see another module's test fixtures, so the pair is written out
 * again here rather than promoted into production code for a test's benefit.
 *
 * `put` hashes while it copies, in one place, exactly as `SafTreeAttachmentStore` does, so a
 * ViewModel test can assert on a sha256 without an emulator.
 */
class InMemoryAttachmentStore : AttachmentStore {
    val files = LinkedHashMap<String, ByteArray>()
    var failOnPut: String? = null
    var deletes = 0
        private set

    override suspend fun put(locator: String, source: ByteSource): StoredBytes {
        if (locator == failOnPut) throw StoreIoException("rigged put failure at $locator")
        // Before the source is read, exactly as the real store does: `SafTreeAttachmentStore`
        // deletes the stale document and then creates the new one, so a source that dies mid-read
        // leaves the locator *empty*, not holding the old bytes. A fake that kept them would let a
        // test pass here while the product lost data on the device.
        files.remove(locator)
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
