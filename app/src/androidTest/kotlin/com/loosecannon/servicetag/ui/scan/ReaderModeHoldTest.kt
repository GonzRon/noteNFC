package com.loosecannon.servicetag.ui.scan

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.loosecannon.nfc.tagcore.android.TagHandle
import com.loosecannon.servicetag.ui.app
import com.loosecannon.servicetag.ui.awaitText
import com.loosecannon.servicetag.ui.clearInstall
import com.loosecannon.servicetag.ui.nav.Route
import com.loosecannon.servicetag.ui.nav.ServiceTagRoot
import com.loosecannon.servicetag.ui.nfc.ReaderMode
import com.loosecannon.servicetag.ui.nfc.ReaderModeControl
import com.loosecannon.servicetag.ui.theme.ServiceTagTheme
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** How long a hold assertion waits for a navigation and its effects to settle. */
private const val SETTLE_MILLIS = 5_000L

/**
 * 2.7 (#37, runbook R1) — the nav shell's hold, on a device with no NFC adapter. The session itself
 * cannot be exercised here; what can be, and what regressed, is the *number* of times the app turns
 * reader mode on and off as the back stack moves, so the control is a counter.
 *
 * The three routes arrive through the deep-link flow rather than through taps: the flow is the
 * activity's own way of pushing a destination, and it lets one case walk the whole tag flow without
 * depending on where a row sits on the settings screen.
 *
 * Emulator only — the suite wipes app data.
 */
@RunWith(AndroidJUnit4::class)
class ReaderModeHoldTest {

    @get:Rule val rule = createComposeRule()

    private val control = CountingControl()
    private val readerMode = ReaderMode { _ -> control }
    private val deepLinks = MutableSharedFlow<Route>(replay = 1, extraBufferCapacity = 4)
    private val snackbars = MutableSharedFlow<String>(replay = 1, extraBufferCapacity = 4)

    @Before fun freshInstall() = clearInstall()

    @Test fun theHoldSpansTheTagFlowAndEndsOnlyWhenItIsLeft() {
        rule.setContent {
            ServiceTagTheme {
                ServiceTagRoot(
                    graph = app.graph,
                    deepLinks = deepLinks,
                    snackbars = snackbars,
                    readerMode = readerMode,
                )
            }
        }

        // The dashboard reads no tags: ambient dispatch is what a tap on this screen is for.
        rule.awaitText("ServiceTag")
        assertEquals(0, control.starts)
        assertEquals(0, readerMode.sinkCount)

        // Read / inspect tag.
        rule.runOnIdle { deepLinks.tryEmit(Route.Scan) }
        rule.awaitText("READY TO SCAN")
        rule.waitUntil(SETTLE_MILLIS) { readerMode.sinkCount == 1 }
        assertEquals(1, control.starts)
        assertEquals(0, control.stops)

        // R1 — the write screen on top of the inspect screen. One session, and the hand-over makes
        // no platform call: the survivor's sink is the only one left afterwards.
        rule.runOnIdle { deepLinks.tryEmit(Route.WriteTag("none", null, null)) }
        rule.awaitText("Write a tag")
        rule.waitUntil(SETTLE_MILLIS) { readerMode.sinkCount == 1 }
        assertEquals("the transition is not a start", 1, control.starts)
        assertEquals("and not a stop either", 0, control.stops)

        // Leaving the flow is the one thing that hands NFC back to the system.
        rule.runOnIdle { deepLinks.tryEmit(Route.Settings) }
        rule.awaitText("Read / inspect tag")
        rule.waitUntil(SETTLE_MILLIS) { readerMode.sinkCount == 0 }
        assertEquals(1, control.stops)
        assertEquals(1, control.starts)
    }

    /**
     * 2.7 (#37) — a tag the app cannot read leaves the back stack and the hold alone.
     *
     * Read what this does and does not prove, because the two are easy to confuse. The emulator has
     * no NFC, so the handle is synthetic; `RealTagIo` refuses any handle reader mode did not deliver
     * and throws while the argument is being evaluated, so `ScanViewModel.onTag` catches it, sets
     * its "couldn't read" line and never emits `ScanEvent.Show`. So the delivery here is a *failed*
     * read, and what is pinned is a real but narrower invariant: a delivery that resolves to nothing
     * still does not move the back stack and still does not hand NFC back — the screen stays, its
     * sink stays installed, `stops` stays 0.
     *
     * It is **not** a guard on the transition #37 is named for. A *resolved* read never reaches this
     * case, so reintroducing a `backStack.add(...)` in the `ScanEvent.Show` branch would leave this
     * test green. That the resolved path draws its answer instead of pushing a route rests on
     * inspection of `ScanScreen` — the `Show` branch assigns `format`/`key` and bumps `readId`, and
     * there is no navigation call in it — and on the runbook's physical row. Catching it by test
     * needs a `TagIo` seam reachable from the screen (it takes only `AppGraph`, and `ScanViewModels`
     * and `AppGraph` are untouched this release), which is a follow-up, not something to invent here.
     */
    @Test fun aTagThatCannotBeReadDoesNotMoveTheBackStackOrReleaseTheHold() {
        rule.setContent {
            ServiceTagTheme {
                ServiceTagRoot(
                    graph = app.graph,
                    deepLinks = deepLinks,
                    snackbars = snackbars,
                    readerMode = readerMode,
                )
            }
        }

        rule.runOnIdle { deepLinks.tryEmit(Route.Scan) }
        rule.awaitText("READY TO SCAN")
        rule.waitUntil(SETTLE_MILLIS) { readerMode.sinkCount == 1 }
        assertEquals(1, control.starts)

        rule.runOnIdle { readerMode.deliver(FakeHandle) }
        rule.waitForIdle()

        // The scan screen is still the entry on top: nothing was pushed, so its sink is still the
        // one installed and the session was never handed back.
        rule.onNodeWithText("READY TO SCAN").assertIsDisplayed()
        rule.onAllNodesWithText("Read / inspect tag").assertCountEquals(1)
        assertEquals("a delivery is not a navigation", 1, readerMode.sinkCount)
        assertEquals("and never a stop", 0, control.stops)
        assertEquals(1, control.starts)
    }

    /** Not an `NfcTagHandle`, so `RealTagIo` refuses it — which is all the emulator can offer. */
    private object FakeHandle : TagHandle {
        override val uid: String = "04a1"
    }

    private class CountingControl : ReaderModeControl {
        @Volatile var starts = 0
        @Volatile var stops = 0
        override val available: Boolean = true
        override val enabled: Boolean = true
        override fun start() { starts++ }
        override fun stop() { stops++ }
    }
}
