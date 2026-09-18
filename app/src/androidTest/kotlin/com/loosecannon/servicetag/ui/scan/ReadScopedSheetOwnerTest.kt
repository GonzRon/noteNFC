package com.loosecannon.servicetag.ui.scan

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 2.7 (#37) — one `ViewModelStore` per read, and it dies with the read.
 *
 * `TagResultSheet` is drawn on the scan screen now instead of being pushed as its own entry, and it
 * resolves its `TagResultViewModel` once, in `init`. Left to the scan entry's store that model
 * would outlive the read and answer the next one: bind an unregistered tag, come back, read it
 * again, and the sheet would still call it unassigned. `rememberReadScopedOwner` is what the pushed
 * entry used to provide, so this pins both halves of it — a distinct store per read, and the
 * previous read's model actually cleared.
 *
 * The probe resolves through `viewModel { … }` and `LocalViewModelStoreOwner`, the same two things
 * the sheet resolves through, which is why this reaches the wiring and not just the helper. What it
 * cannot reach is the sheet itself: driving a real read needs a `TagIo` seam on the screen (it takes
 * only `AppGraph`, and `RealTagIo` refuses any handle reader mode did not deliver), and the emulator
 * has no NFC. `ScanViewModels.kt` is untouched; `TagResultSheet.kt` gains only 2.7.1's `inspecting`
 * flag (#41), which this probe does not reach.
 *
 * Emulator only.
 */
@RunWith(AndroidJUnit4::class)
class ReadScopedSheetOwnerTest {

    @get:Rule val rule = createComposeRule()

    private val owners = mutableListOf<ViewModelStoreOwner>()
    private val models = mutableListOf<ProbeViewModel>()

    @Test fun eachReadResolvesAgainAndThePreviousReadsModelIsCleared() {
        var readId by mutableStateOf(0)
        var showing by mutableStateOf(true)

        rule.setContent {
            // Exactly the shape `ScanScreen` draws the sheet in.
            if (showing) {
                val owner = rememberReadScopedOwner(readId)
                CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
                    Probe()
                }
            }
        }

        rule.waitForIdle()
        assertEquals("the first read", 1, owners.size)
        assertEquals(1, models.size)
        assertFalse(models[0].cleared)

        // A second delivery of the same tag: same format and key, a different read.
        rule.runOnIdle { readId++ }
        rule.waitForIdle()
        assertEquals("a second read is a second store", 2, owners.size)
        assertNotSame(owners[0], owners[1])
        assertNotSame(owners[0].viewModelStore, owners[1].viewModelStore)
        assertEquals("and resolves its own model", 2, models.size)
        assertNotSame(models[0], models[1])
        assertTrue("the first read's model is gone, not cached", models[0].cleared)
        assertFalse(models[1].cleared)

        // Dismissal ends the last read too — nothing is left to answer a later one.
        rule.runOnIdle { showing = false }
        rule.waitForIdle()
        assertTrue(models[1].cleared)
    }

    /** Resolves a view model the way the result sheet does, and records what it got. */
    @Composable
    private fun Probe() {
        val owner = LocalViewModelStoreOwner.current
        val model: ProbeViewModel = viewModel { ProbeViewModel() }
        remember(owner) {
            owners += requireNotNull(owner)
            models += model
            model
        }
    }

    /** A `TagResultViewModel` stand-in: all this needs to say is when it was cleared. */
    private class ProbeViewModel : ViewModel() {
        var cleared = false
            private set

        override fun onCleared() {
            cleared = true
        }
    }
}
