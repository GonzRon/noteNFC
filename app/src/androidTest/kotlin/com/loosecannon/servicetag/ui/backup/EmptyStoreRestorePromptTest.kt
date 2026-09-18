package com.loosecannon.servicetag.ui.backup

import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.app.ActivityOptionsCompat
import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.loosecannon.servicetag.ui.app
import com.loosecannon.servicetag.ui.awaitText
import com.loosecannon.servicetag.ui.clearInstall
import com.loosecannon.servicetag.ui.theme.ServiceTagTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** How long an assertion waits for a dialog to appear or go away. */
private const val SETTLE_MILLIS = 5_000L

/**
 * 2.8 (#40) — a phone with no records is asked to confirm, and is never asked to type REPLACE.
 *
 * **How the pick is driven without the SAF picker.** `rememberLauncherForActivityResult` resolves
 * its registry through `LocalActivityResultRegistryOwner`, which is the documented seam for exactly
 * this: the registry provided here answers a `launch` by dispatching a document URI straight back,
 * so no picker activity is started and `BackupScreen`'s callback runs precisely as it does after a
 * real pick. No seam is added to production code and no test dependency is added to the build.
 *
 * The URI is never opened. This case asserts which dialog came up and then cancels it; the restore
 * path itself is proved by `BackupViewModelTest`, `RestoreProofTest` and `PreservedSetRestoreTest`.
 *
 * Emulator only — the suite wipes app data.
 */
@RunWith(AndroidJUnit4::class)
class EmptyStoreRestorePromptTest {

    @get:Rule val rule = createComposeRule()

    /** A document that does not exist, because nothing here reads one. */
    private val picked = "content://com.loosecannon.servicetag.test/ServiceTag-data.zip".toUri()

    private val registryOwner = object : ActivityResultRegistryOwner {
        override val activityResultRegistry: ActivityResultRegistry = object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(
                requestCode: Int,
                contract: ActivityResultContract<I, O>,
                input: I,
                options: ActivityOptionsCompat?,
            ) {
                @Suppress("UNCHECKED_CAST")
                dispatchResult(requestCode, picked as O)
            }
        }
    }

    @Before fun freshInstall() = clearInstall()

    @Test fun anEmptyStoreIsAskedToConfirmAndNeverToTypeReplace() {
        rule.setContent {
            ServiceTagTheme {
                CompositionLocalProvider(LocalActivityResultRegistryOwner provides registryOwner) {
                    BackupScreen(graph = app.graph, onBack = {})
                }
            }
        }

        rule.awaitText("Restore data")
        rule.onNodeWithText("Restore data").performClick()

        // The plain confirmation, in the owner's ratified words.
        rule.awaitText("Restore this backup?")
        rule.onNodeWithText("This phone has no records yet, so there is nothing to replace.")
            .assertIsDisplayed()
        // The confirm button, matched by exact text *and* a click action. `SectionHeader` uppercases
        // its title (`SectionHeader.kt:28`), so the "Restore" heading above the two restore buttons
        // is a `RESTORE` node and could not collide anyway — the click action says which node this
        // is about even if that ever changes.
        rule.onNode(hasText("Restore") and hasClickAction()).assertIsDisplayed()
        rule.onNodeWithText("Cancel").assertIsDisplayed()

        // And not the typed one: neither its title nor the field that gates it is anywhere.
        rule.onAllNodesWithText("Replace everything?").assertCountEquals(0)
        rule.onAllNodesWithText("Type REPLACE to confirm").assertCountEquals(0)

        // Cancel closes it and restores nothing.
        rule.onNodeWithText("Cancel").performClick()
        rule.waitUntil(SETTLE_MILLIS) {
            rule.onAllNodesWithText("Restore this backup?").fetchSemanticsNodes().isEmpty()
        }
    }
}
