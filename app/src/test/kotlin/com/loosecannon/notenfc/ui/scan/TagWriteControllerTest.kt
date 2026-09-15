package com.loosecannon.notenfc.ui.scan

import com.loosecannon.notenfc.core.model.TagTarget
import com.loosecannon.notenfc.core.nfc.NdefCodec
import com.loosecannon.notenfc.core.nfc.NdefRecordData
import com.loosecannon.notenfc.core.nfc.TagPayload
import com.loosecannon.notenfc.nfc.TagInspection
import com.loosecannon.notenfc.nfc.WriteResult
import com.loosecannon.notenfc.testing.FakeGraph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The 1B `WriteTagActivity` decision logic, now a controller and therefore testable on the JVM:
 * the [TagIo] seam stands in for the tag, the Room-backed [FakeGraph] supplies the real
 * `ProvisionTag`, so what a test asserts about the row is what the database actually holds.
 *
 * Every wait is a `state.first { … }` rather than a read of `value`: the controller does its work
 * in its own scope and the repositories answer on their own query context.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TagWriteControllerTest {

    private lateinit var graph: FakeGraph
    private lateinit var io: FakeTagIo

    @Before fun setUp() {
        graph = FakeGraph()
        io = FakeTagIo()
    }

    @After fun tearDown() = graph.close()

    private fun TestScope.controller(
        target: TagTarget = TagTarget.None,
        label: String? = null,
    ): TagWriteController {
        val scope = CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler))
        return TagWriteController(
            provisionTag = graph.provisionTag,
            appScope = scope,
            io = io,
            target = target,
            label = label,
            scope = scope,
            ioDispatcher = UnconfinedTestDispatcher(testScheduler),
        )
    }

    /** The one row the controller provisions on its first tap. */
    private suspend fun theRow() = graph.tags.all().single()

    @Test fun emptyTagIsWrittenWithoutAsking() = runTest {
        io.inspection = inspection(TagPayload.Empty)
        io.result = written(verified = true)
        val c = controller()

        c.onTag(FakeTag)

        val settled = c.state.first { it is WriteState.Written || it is WriteState.Confirm }
        assertTrue("an empty tag must not raise a question: $settled", settled is WriteState.Written)
        assertEquals(1, io.writes.size)
        assertNotNull(theRow().writtenAt)
    }

    @Test fun foreignContentAsksFirstAndKeepItWritesNothing() = runTest {
        io.inspection = inspection(TagPayload.Foreign("tnf=1 type=T"))
        val c = controller()

        c.onTag(FakeTag)

        val asked = c.state.first { it is WriteState.Confirm } as WriteState.Confirm
        assertTrue("the question names what is on the tag: ${asked.reason}", "foreign" in asked.reason)
        assertEquals(0, io.writes.size)

        c.keepIt()
        c.state.first { it is WriteState.Idle }
        assertEquals(0, io.writes.size)
        // The row stays provisioned for the next tap; it is only abandoned when the screen closes.
        assertNull(theRow().writtenAt)
    }

    @Test fun confirmedOverwriteIsHonouredOnTheNextTapWithoutAskingAgain() = runTest {
        io.inspection = inspection(TagPayload.Foreign("tnf=1 type=T"))
        val c = controller()

        c.onTag(FakeTag)
        c.state.first { it is WriteState.Confirm }

        // The handle captured before the sheet went up has gone stale, exactly as on the phone.
        io.result = WriteResult.Failed("Tag is out of date")
        c.confirmOverwrite()
        val lost = c.state.first { it is WriteState.Error } as WriteState.Error
        assertTrue("the message keeps the consent in view: ${lost.message}", "Overwrite confirmed" in lost.message)

        // Same tag, same content, fresh handle: the consent carries over and nothing is asked.
        io.result = written(verified = true)
        c.onTag(FakeTag)
        val settled = c.state.first { it is WriteState.Written || it is WriteState.Confirm }
        assertTrue("a confirmed overwrite must not ask twice: $settled", settled is WriteState.Written)
        assertEquals(2, io.writes.size)
    }

    @Test fun readBackMismatchDoesNotCompleteTheRow() = runTest {
        io.inspection = inspection(TagPayload.Empty)
        io.result = WriteResult.VerifyMismatch(emptyList())
        val c = controller()

        c.onTag(FakeTag)

        c.state.first { it is WriteState.Error }
        assertNull("nothing may be recorded for a tag that read back differently", theRow().writtenAt)
    }

    @Test fun formatPathLocksOnlyAfterSecondTapVerifies() = runTest {
        io.inspection = inspection(TagPayload.Empty, needsFormat = true, maxSize = -1)
        io.result = written(verified = false)
        val c = controller()
        c.setLock(true)

        c.onTag(FakeTag)

        c.state.first { it is WriteState.Verifying }
        assertEquals("a formatted tag is never locked blind", 0, io.lockCalls)

        val intended = NdefCodec.encodeV1(theRow().id)
        io.inspection = inspection(TagPayload.V1(theRow().id), records = intended)
        c.onTag(FakeTag)

        val done = c.state.first { it is WriteState.Written } as WriteState.Written
        assertEquals(1, io.lockCalls)
        assertTrue(done.locked)
        assertEquals("the second tap verifies, it does not write again", 1, io.writes.size)
        assertEquals(FakeTag.uid, theRow().physicalUid)
    }

    @Test fun abandonDeletesOnlyAnUnwrittenRow() = runTest {
        io.inspection = null // not an NDEF tag at all: the row is provisioned, nothing is written
        val unwritten = controller()
        unwritten.onTag(FakeTag)
        unwritten.state.first { it is WriteState.Error }
        assertEquals(1, graph.tags.all().size)

        unwritten.abandonIfUnwritten()?.join()
        assertEquals("a row whose tag was never written leaves nothing behind", 0, graph.tags.all().size)

        io.inspection = inspection(TagPayload.Empty)
        io.result = written(verified = true)
        val done = controller()
        done.onTag(FakeTag)
        done.state.first { it is WriteState.Written }

        done.abandonIfUnwritten()?.join()
        assertEquals("a written tag's row survives the screen closing", 1, graph.tags.all().size)
    }

    // --- the seam -----------------------------------------------------------------------------

    private object FakeTag : TagHandle {
        override val uid: String = "04a7912c"
    }

    private fun inspection(
        existing: TagPayload,
        records: List<NdefRecordData> = emptyList(),
        maxSize: Int = 137,
        needsFormat: Boolean = false,
    ) = TagInspection(
        uid = FakeTag.uid,
        existing = existing,
        existingRecords = records,
        maxSize = maxSize,
        writable = true,
        needsFormat = needsFormat,
        canLock = true,
    )

    private fun written(verified: Boolean) =
        WriteResult.Written(readBack = emptyList(), bytes = 46, verified = verified, locked = false)

    private class FakeTagIo : TagIo {
        var inspection: TagInspection? = null
        var result: WriteResult = WriteResult.Failed("no result scripted")
        val writes = mutableListOf<Pair<List<NdefRecordData>, Boolean>>()
        var lockCalls = 0

        override fun inspect(tag: TagHandle): TagInspection? = inspection

        override fun write(tag: TagHandle, records: List<NdefRecordData>, lock: Boolean): WriteResult {
            writes += records to lock
            return result
        }

        override fun lock(tag: TagHandle): Boolean {
            lockCalls++
            return true
        }
    }
}
