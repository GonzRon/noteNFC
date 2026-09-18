package com.loosecannon.servicetag.ui.nfc

import com.loosecannon.nfc.tagcore.android.TagHandle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The hold policy and the tag routing, with no Activity and no adapter (2.7). What these cases pin
 * is the number of `enableReaderMode`/`disableReaderMode` calls the app makes and which screen a
 * delivered tag reaches — the two facts #37 and runbook R1 turn on, and the two the emulator
 * cannot show because it has no NFC.
 */
class ReaderModeTest {

    private val control = CountingControl()
    private val readerMode = ReaderMode { _ -> control }

    /**
     * #37 and R1 — the inspect screen and the write screen are both inside the tag flow, so moving
     * from one to the other is not a hand-over: reader mode is never switched off with a tag
     * possibly still against the phone, and nothing has to race anything.
     */
    @Test fun aMoveWithinTheTagFlowIsNotAHandOver() {
        readerMode.hold(true)      // the inspect screen comes up
        readerMode.hold(true)      // ... and the write screen replaces it
        assertEquals(1, control.starts)
        assertEquals(0, control.stops)
        assertTrue(readerMode.holding)

        readerMode.hold(false)     // the flow is left
        assertEquals(1, control.starts)
        assertEquals(1, control.stops)
        assertFalse(readerMode.holding)
    }

    /** Leaving is idempotent too: a pause after a dispose must not stop a stopped session. */
    @Test fun leavingTwiceStopsOnce() {
        readerMode.hold(true)
        readerMode.hold(false)
        readerMode.hold(false)
        assertEquals(1, control.stops)
    }

    /** R1 — the arriving screen installs first, and it is the one that reads. */
    @Test fun theNewestSinkGetsTheTag() {
        val seen = mutableListOf<String>()
        val inspect: (TagHandle) -> Unit = { seen += "inspect" }
        val write: (TagHandle) -> Unit = { seen += "write" }

        readerMode.install(inspect)
        readerMode.install(write)
        assertEquals("both screens are up for the length of the transition", 2, readerMode.sinkCount)
        readerMode.deliver(Handle)

        readerMode.uninstall(inspect)
        assertEquals(1, readerMode.sinkCount)
        readerMode.deliver(Handle)

        assertEquals(listOf("write", "write"), seen)
    }

    /** R1, the other order — the leaving screen goes first, and the survivor still reads. */
    @Test fun theSurvivorReadsWhenTheLeavingScreenGoesFirst() {
        val seen = mutableListOf<String>()
        val inspect: (TagHandle) -> Unit = { seen += "inspect" }
        val write: (TagHandle) -> Unit = { seen += "write" }

        readerMode.install(inspect)
        readerMode.uninstall(inspect)
        readerMode.install(write)
        readerMode.deliver(Handle)

        assertEquals(listOf("write"), seen)
    }

    /**
     * The gap between those two orders. A tag that arrives with nobody listening is dropped, and
     * reader mode stays on: dropping costs the user one more hold, while handing NFC back to the
     * platform — the only other thing the app could do — is the dispatch #37 is about.
     */
    @Test fun aTagWithNoSinkIsDroppedAndReaderModeStaysOn() {
        readerMode.hold(true)
        assertEquals("the precondition: held, with nobody listening", 0, readerMode.sinkCount)
        readerMode.deliver(Handle)
        assertTrue(readerMode.holding)
        assertEquals(0, control.stops)
    }

    /** No Activity, no reader mode — and the three lines the screens draw say so, not throw. */
    @Test fun withNoActivityThereIsNoReaderModeAndNothingThrows() {
        val none = ReaderMode { _ -> null }
        none.hold(true)
        none.install { }
        none.deliver(Handle)
        assertFalse(none.present)
        assertFalse(none.available)
        assertFalse(none.enabled)
        assertFalse(none.holding)
    }

    private object Handle : TagHandle {
        override val uid: String = "04a1"
    }

    private class CountingControl : ReaderModeControl {
        var starts = 0
        var stops = 0
        override val available: Boolean = true
        override val enabled: Boolean = true
        override fun start() { starts++ }
        override fun stop() { stops++ }
    }
}
