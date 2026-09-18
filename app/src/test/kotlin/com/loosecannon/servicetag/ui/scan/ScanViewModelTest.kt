package com.loosecannon.servicetag.ui.scan

import com.loosecannon.nfc.tagcore.NdefRecordData
import com.loosecannon.nfc.tagcore.android.TagHandle
import com.loosecannon.nfc.tagcore.android.TagInspection
import com.loosecannon.nfc.tagcore.android.TagIo
import com.loosecannon.nfc.tagcore.android.WriteResult
import com.loosecannon.servicetag.core.usecase.OpenLink
import com.loosecannon.servicetag.core.usecase.ResolveTag
import com.loosecannon.servicetag.testing.FakeGraph
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

/**
 * The scanner's failure wording. `viewModelScope` dispatches on `Dispatchers.Main`, so the main
 * dispatcher is a test one for the length of each case and the same dispatcher carries the tag read.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ScanViewModelTest {

    private val scheduler = TestCoroutineScheduler()
    private val dispatcher = UnconfinedTestDispatcher(scheduler)
    private lateinit var graph: FakeGraph

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        graph = FakeGraph(queryContext = StandardTestDispatcher(scheduler))
    }

    @After fun tearDown() {
        graph.close()
        Dispatchers.resetMain()
    }

    /**
     * E2 — a read that throws says one thing the user can act on. The exception's class name used
     * to be interpolated into the sentence; it is diagnosis, so it goes to the log instead.
     */
    @Test fun aTagThatCannotBeReadSaysOneSentenceWithNoClassNameInIt() = runTest(dispatcher) {
        val viewModel = ScanViewModel(
            ResolveTag(graph.tags, graph.assets, graph.links, graph.uow, graph.clock),
            OpenLink(graph.links, graph.uow, graph.clock),
            ThrowingTagIo,
            graph.ndefCodec,
            dispatcher,
        )

        viewModel.onTag(FakeHandle)
        advanceUntilIdle()

        assertEquals(
            "Couldn't read that tag. Hold it still and try again.",
            viewModel.state.value.problem,
        )
        assertFalse("the scanner is not left reading", viewModel.state.value.reading)
    }

    private object FakeHandle : TagHandle {
        override val uid: String = "04a1"
    }

    /** The one operation this case needs: a read that fails the way a tag leaving the field fails. */
    private object ThrowingTagIo : TagIo {
        override fun inspect(tag: TagHandle): TagInspection? = throw IOException("tag left the field")
        override fun format(tag: TagHandle): WriteResult = error("not used")
        override fun write(tag: TagHandle, records: List<NdefRecordData>, lock: Boolean): WriteResult = error("not used")
        override fun lock(tag: TagHandle, expected: List<NdefRecordData>): Boolean = error("not used")
    }
}
