package com.loosecannon.notenfc.attachments

import androidx.documentfile.provider.DocumentFile
import androidx.test.core.app.ApplicationProvider
import com.loosecannon.notenfc.core.ports.ByteSource
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The same seven claims `AttachmentStoreContractTest` makes about the JVM fake, made about the real
 * `DocumentFile` store — over `DocumentFile.fromFile` on an app-external directory, because an
 * instrumented test cannot drive the SAF picker. The list is duplicated on purpose: JVM test
 * fixtures cannot be shared with an `androidTest` variant, so the two suites are written to read
 * the same way, and a drift between them is meant to be visible in review.
 *
 * Two claims the JVM contract cannot make are here as well, because only a real provider can be
 * asked them: a document the provider renamed is still found by its id (spec §5.2), and a source
 * that dies mid-copy leaves no document behind.
 */
class SafTreeAttachmentStoreContractTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private lateinit var root: File
    private lateinit var store: SafTreeAttachmentStore

    /** Big enough that the store's 64 KiB copy loop runs more than once. */
    private val payload = ByteArray(200_000) { (it % 251).toByte() }

    @Before fun freshTree() {
        root = File(context.getExternalFilesDir(null), "attachments-contract").also {
            it.deleteRecursively()
            it.mkdirs()
        }
        store = SafTreeAttachmentStore(DocumentFile.fromFile(root), context.contentResolver)
    }

    @Test fun putThenOpenRoundTripsTheBytes() = runBlocking {
        store.put("assets/a1/att-1.pdf", ByteSource { payload.inputStream() })
        assertArrayEquals(payload, store.open("assets/a1/att-1.pdf")!!.use { it.readBytes() })
    }

    @Test fun putReturnsTheShaAndSizeTheStoreItselfSaw() = runBlocking {
        val stored = store.put("assets/a1/att-1.pdf", ByteSource { payload.inputStream() })
        assertEquals(payload.size.toLong(), stored.sizeBytes)
        assertEquals(sha256Hex(payload), stored.sha256)
        assertEquals(64, stored.sha256.length)
        assertEquals(stored.sha256.lowercase(), stored.sha256)
    }

    @Test fun existsAnswersForBothCases() = runBlocking {
        assertFalse(store.exists("assets/a1/att-1.pdf"))
        store.put("assets/a1/att-1.pdf", ByteSource { payload.inputStream() })
        assertTrue(store.exists("assets/a1/att-1.pdf"))
    }

    @Test fun openOfAnAbsentLocatorIsNull() = runBlocking {
        assertNull(store.open("assets/a1/nothing.pdf"))
    }

    @Test fun deleteOfAnAbsentLocatorIsSilent() = runBlocking {
        store.delete("assets/a1/nothing.pdf")
        store.put("assets/a1/att-1.pdf", ByteSource { payload.inputStream() })
        store.delete("assets/a1/att-1.pdf")
        assertFalse(store.exists("assets/a1/att-1.pdf"))
    }

    @Test fun aLocatorWithTwoDirectoryLevelsIsCreated() = runBlocking {
        store.put("events/e1/att-2.jpg", ByteSource { payload.inputStream() })
        assertTrue(File(root, "events/e1").isDirectory)
        assertTrue(store.exists("events/e1/att-2.jpg"))
    }

    /**
     * A second `put` at one locator replaces the document rather than sitting beside it — the
     * store's stale-document branch, which only a real provider can reach, and the path a restore
     * takes when it lands over bytes that are already there (spec §5.2, §6).
     */
    @Test fun puttingTwiceAtOneLocatorReplacesTheDocument() = runBlocking {
        val second = ByteArray(1_000) { (it % 97).toByte() }
        store.put("assets/a1/att-1.pdf", ByteSource { payload.inputStream() })
        val stored = store.put("assets/a1/att-1.pdf", ByteSource { second.inputStream() })

        assertEquals(1, File(root, "assets/a1").listFiles()!!.size)
        assertEquals(second.size.toLong(), stored.sizeBytes)
        assertEquals(sha256Hex(second), stored.sha256)
        assertArrayEquals(second, store.open("assets/a1/att-1.pdf")!!.use { it.readBytes() })
    }

    /**
     * The provider-renamed case: a locator still resolves by its `<id>.` prefix (spec §5.2).
     *
     * This provider renames on its own — `RawDocumentFile.createFile` appends the extension it
     * derives from the mime type, so asking for `att-1.pdf` with `application/pdf` leaves
     * `att-1.pdf.pdf` on disk. The quirk the fallback exists for is therefore not simulated here,
     * it is observed; the rename that follows is a second, differently normalised name, to show
     * that nothing but the `<id>.` prefix is being relied on.
     */
    @Test fun aDocumentTheProviderRenamedIsStillFoundByItsId() = runBlocking {
        store.put("assets/a1/att-1.pdf", ByteSource { payload.inputStream() })
        val onDisk = File(root, "assets/a1").listFiles()!!.single()
        assertTrue("stored as ${onDisk.name}", onDisk.name.startsWith("att-1."))
        assertTrue(store.exists("assets/a1/att-1.pdf"))

        assertTrue(onDisk.renameTo(File(root, "assets/a1/att-1.PDF-1")))
        assertTrue(store.exists("assets/a1/att-1.pdf"))
        assertArrayEquals(payload, store.open("assets/a1/att-1.pdf")!!.use { it.readBytes() })
    }

    /** A failed write leaves nothing behind for a row to point at. */
    @Test fun aSourceThatThrowsMidCopyLeavesNoDocument() = runBlocking {
        val boom = runCatching {
            store.put("assets/a1/att-1.pdf", ByteSource { ThrowingStream(payload, after = 1024) })
        }
        assertTrue(boom.isFailure)
        assertFalse(store.exists("assets/a1/att-1.pdf"))
        // Nothing at all, not even an empty document: the directory the copy created is empty.
        assertEquals(emptyList<String>(), File(root, "assets/a1").list()!!.toList())
    }

    /**
     * The `:core` contract's claim, made about the real provider: a put that fails over an
     * existing document leaves the locator empty. `put` deletes the stale document before it
     * creates the new one, so there is nothing left to fall back to — which is exactly why
     * `RestoreArtifacts` checks the local digest before it writes anything.
     */
    @Test fun aPutThatFailsOverAnExistingDocumentLeavesTheLocatorEmpty() = runBlocking {
        store.put("assets/a1/att-1.pdf", ByteSource { payload.inputStream() })

        val boom = runCatching {
            store.put("assets/a1/att-1.pdf", ByteSource { ThrowingStream(payload, after = 1024) })
        }

        assertTrue(boom.isFailure)
        assertFalse(store.exists("assets/a1/att-1.pdf"))
        assertNull(store.open("assets/a1/att-1.pdf"))
        assertEquals(emptyList<String>(), File(root, "assets/a1").list()!!.toList())
    }

    private fun sha256Hex(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { b -> "%02x".format(b) }

    /** Reads [after] bytes and then fails, the way a revoked provider grant does. */
    private class ThrowingStream(bytes: ByteArray, private val after: Int) : java.io.InputStream() {
        private val source = bytes.inputStream()
        private var read = 0
        override fun read(): Int {
            if (read++ >= after) throw java.io.IOException("rigged mid-copy failure")
            return source.read()
        }
    }
}
