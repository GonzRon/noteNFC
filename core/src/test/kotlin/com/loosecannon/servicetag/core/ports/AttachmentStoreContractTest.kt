package com.loosecannon.servicetag.core.ports

import com.loosecannon.servicetag.core.testing.InMemoryAttachmentStore
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AttachmentStoreContractTest {

    private val store = InMemoryAttachmentStore()
    private val payload = "spa water chemistry".toByteArray()

    @Test fun putThenOpenRoundTripsTheBytes() = runTest {
        store.put("assets/a1/att-1.pdf", ByteSource { payload.inputStream() })
        assertContentEquals(payload, store.open("assets/a1/att-1.pdf")!!.use { it.readBytes() })
    }

    @Test fun putReturnsTheShaAndSizeTheStoreItselfSaw() = runTest {
        val stored = store.put("assets/a1/att-1.pdf", ByteSource { payload.inputStream() })
        assertEquals(payload.size.toLong(), stored.sizeBytes)
        assertEquals(InMemoryAttachmentStore.sha256Hex(payload), stored.sha256)
        assertEquals(64, stored.sha256.length)
        assertEquals(stored.sha256.lowercase(), stored.sha256)
    }

    @Test fun existsAnswersForBothCases() = runTest {
        assertFalse(store.exists("assets/a1/att-1.pdf"))
        store.put("assets/a1/att-1.pdf", ByteSource { payload.inputStream() })
        assertTrue(store.exists("assets/a1/att-1.pdf"))
    }

    @Test fun openOfAnAbsentLocatorIsNull() = runTest {
        assertNull(store.open("assets/a1/nothing.pdf"))
    }

    @Test fun deleteOfAnAbsentLocatorIsSilent() = runTest {
        store.delete("assets/a1/nothing.pdf")   // no throw
        store.put("assets/a1/att-1.pdf", ByteSource { payload.inputStream() })
        store.delete("assets/a1/att-1.pdf")
        assertFalse(store.exists("assets/a1/att-1.pdf"))
    }

    @Test fun aLocatorWithTwoDirectoryLevelsIsCreated() = runTest {
        store.put("events/e1/att-2.jpg", ByteSource { payload.inputStream() })
        assertTrue(store.exists("events/e1/att-2.jpg"))
    }

    /**
     * A put replaces the document, so a put that fails takes the old bytes with it: the locator is
     * empty afterwards, not still holding what was there before.
     *
     * This is the claim that makes `RestoreArtifacts` check the local digest *before* it writes —
     * a restore that put a damaged entry over good bytes would leave the row with no bytes at all.
     * `SafTreeAttachmentStoreContractTest` makes the same claim about the real `DocumentFile`
     * store, because it is the real store's behaviour this one is copying.
     */
    @Test fun aPutThatFailsOverAnExistingDocumentLeavesTheLocatorEmpty() = runTest {
        store.put("assets/a1/att-1.pdf", ByteSource { payload.inputStream() })

        val boom = runCatching {
            store.put("assets/a1/att-1.pdf", ByteSource { ThrowingStream(payload, after = 4) })
        }

        assertTrue(boom.isFailure)
        assertFalse(store.exists("assets/a1/att-1.pdf"))
        assertNull(store.open("assets/a1/att-1.pdf"))
    }

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
