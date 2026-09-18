package com.loosecannon.servicetag.ui.nfc

import android.app.Activity
import android.util.Log
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.loosecannon.nfc.tagcore.android.NfcReaderModeSession
import com.loosecannon.nfc.tagcore.android.NfcTagHandle
import com.loosecannon.nfc.tagcore.android.TagHandle
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference

/**
 * The reader-mode switch this app needs, as an interface, so the policy below can be proved without
 * an Activity and on a device with no NFC. [SessionControl] is the only production implementation.
 */
interface ReaderModeControl {
    val available: Boolean
    val enabled: Boolean
    fun start()
    fun stop()
}

/**
 * The activity's one reader-mode session, and the routing in front of it (2.7 — issue #37 and
 * runbook R1).
 *
 * Reader mode is an activity-wide switch: `NfcAdapter.enableReaderMode` takes an `Activity` and
 * `disableReaderMode` turns off whatever is on. Two screens that each owned a session therefore
 * shared one switch, and on a nav transition that overlapped them the leaving screen's `stop()`
 * could run after the arriving screen's `start()` and leave the survivor displayed with NFC handed
 * back to the system (R1). There is exactly one session here instead, built once per activity, and
 * a screen says *where tags go* rather than whether NFC is on: it installs a sink while it is
 * resumed and takes it away again when it is not.
 *
 * [hold] is the other half, and it is #37. The library's session takes its callback at construction
 * and offers no "the tag has left the field" signal, so a read cannot honestly be made one-shot and
 * the hold has to be scoped to something a screen can see. It is scoped to the screens that read
 * tags (`Route.readsTags`): while one of them is on top of the back stack the switch stays on, and
 * a move from one to the other makes no platform call at all. Releasing it while a tag was still
 * against the phone is what let the platform re-discover that tag and dispatch it to another app
 * from inside ServiceTag's own inspect screen.
 *
 * @param controlFor builds the control around the tag callback. `null` when there is no Activity to
 *   own reader mode, which is the "needs the app's own window" line the two screens draw.
 */
class ReaderMode(controlFor: ((TagHandle) -> Unit) -> ReaderModeControl?) {

    /**
     * Declared before [control], because the lambda handed to [controlFor] reads it. The callback
     * arrives on a binder thread — the library's own documented contract — so this is
     * copy-on-write: [deliver] reads the list without locking it, and what it then calls reads an
     * `AtomicReference` ([TagSinkEffect]) rather than a Compose snapshot state, which is a
     * main-thread object.
     */
    private val sinks = CopyOnWriteArrayList<(TagHandle) -> Unit>()

    private val control: ReaderModeControl? = controlFor { tag -> deliver(tag) }

    /** Main-thread only: the one caller is the nav shell's `LifecycleResumeEffect`. */
    private var on = false

    /** Whether an Activity owns reader mode at all. */
    val present: Boolean get() = control != null

    val available: Boolean get() = control?.available == true
    val enabled: Boolean get() = control?.enabled == true

    /**
     * Whether the app is *holding* reader mode — it has asked for it and has not given it back.
     * Not the same as the radio being on: on a phone with no NFC hardware `start()` reaches an
     * adapter that is null and this is still true. Read by the tests, and by nothing else.
     */
    val holding: Boolean get() = on

    /** How many screens are asking for tags: one normally, two for the length of a transition. */
    val sinkCount: Int get() = sinks.size

    /**
     * Turns reader mode on or off. Idempotent, which is the point: a move within the tag flow is
     * not a hand-over, so it must not become a `stop()` and a `start()` with a tag in between.
     */
    fun hold(wanted: Boolean) {
        val switch = control ?: return
        if (wanted == on) return
        on = wanted
        if (wanted) switch.start() else switch.stop()
    }

    fun install(sink: (TagHandle) -> Unit) { sinks.add(sink) }

    fun uninstall(sink: (TagHandle) -> Unit) { sinks.remove(sink) }

    /**
     * Hands [tag] to the newest installed sink — the screen on top. With none installed the tag is
     * dropped, which is the safe answer: dropping it costs the user one more hold, while handing
     * reader mode back to the platform is the dispatch #37 is about.
     *
     * The drop is logged. It is the one lossy path in the design, it lasts a few frames of a nav
     * transition, and a hold that produced nothing is otherwise indistinguishable from a tag the
     * phone never saw — which is the hardest thing to tell apart during a physical run.
     */
    fun deliver(tag: TagHandle) {
        val sink = sinks.lastOrNull()
        if (sink == null) {
            Log.w(TAG, "a tag arrived with no screen listening; dropped")
            return
        }
        sink(tag)
    }

    private companion object {
        const val TAG = "ReaderMode"
    }
}

/**
 * `NfcReaderModeSession` as a [ReaderModeControl]. The library is pinned at `nfc-tag-core-v0.1.0`
 * and unchanged: it takes its callback at construction, which is exactly why the app builds one
 * session per activity and routes tags itself.
 */
internal class SessionControl(activity: Activity, onTag: (TagHandle) -> Unit) : ReaderModeControl {

    private val session = NfcReaderModeSession(activity) { tag -> onTag(NfcTagHandle(tag)) }

    override val available: Boolean get() = session.available
    override val enabled: Boolean get() = session.enabled
    override fun start() = session.start()
    override fun stop() = session.stop()
}

/** The activity's [ReaderMode], built once. No Activity means no session and no reader mode. */
@Composable
fun rememberReaderMode(): ReaderMode {
    val activity = LocalActivity.current
    return remember(activity) {
        ReaderMode { deliver -> activity?.let { host -> SessionControl(host, deliver) } }
    }
}

/**
 * Sends this screen's tags to [onTag] while it is resumed, and stops on pause or dispose. A screen
 * never starts or stops reader mode: the nav shell holds it for the whole tag flow, so a screen
 * that leaves during a transition takes only its own sink with it (#37, R1).
 */
@Composable
fun TagSinkEffect(readerMode: ReaderMode, onTag: (TagHandle) -> Unit) {
    // The sink is invoked on a platform binder thread, so the callback it reads is an
    // `AtomicReference` written from a `SideEffect` — not a `rememberUpdatedState` delegate.
    // Reading a Compose snapshot state resolves against the global snapshot and can take the
    // snapshot lock; those objects are documented for the main thread, and a sink that is
    // installed for as long as the screen is resumed can be called at any moment in between.
    val latest = remember { AtomicReference(onTag) }
    SideEffect { latest.set(onTag) }
    LifecycleResumeEffect(readerMode) {
        val sink: (TagHandle) -> Unit = { tag -> latest.get()(tag) }
        readerMode.install(sink)
        onPauseOrDispose { readerMode.uninstall(sink) }
    }
}
