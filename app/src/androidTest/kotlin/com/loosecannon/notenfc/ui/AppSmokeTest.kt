package com.loosecannon.notenfc.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasNoClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.loosecannon.notenfc.MainActivity
import com.loosecannon.notenfc.NoteNfcApp
import com.loosecannon.notenfc.ShareActivity
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** How long a smoke assertion waits for a Room flow or a navigation to land. */
private const val TIMEOUT_MS = 10_000L

/** The `SharedPreferences` file `SharedPrefsStore` owns; cleared before every test. */
private const val PREFS_NAME = "notenfc"

private val app: NoteNfcApp get() = ApplicationProvider.getApplicationContext()

/**
 * Puts the install back to "nothing has happened yet": no preferences, no rows.
 *
 * The rule launches the activity before `@Before` runs, which is exactly why this is safe to do
 * here: every screen under test reads its data from repository flows and re-reads the preferences
 * on each emission, so wiping the store after the activity is up simply produces one more
 * emission — the fresh-install one the test is about to assert on.
 */
private fun clearInstall() {
    ApplicationProvider.getApplicationContext<Context>()
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .clear()
        .commit()
    val graph = app.graph
    runBlocking {
        graph.uow.write {
            graph.tags.deleteAll()
            graph.links.deleteAll()
            graph.assets.deleteAll()
        }
    }
}

/** Waits until at least [count] nodes carrying [text] exist, then returns. */
private fun ComposeTestRule.awaitText(text: String, count: Int = 1) {
    waitUntil(TIMEOUT_MS) {
        onAllNodesWithText(text).fetchSemanticsNodes().size >= count
    }
}

/**
 * The Phase 1C device smoke suite: the shell, end to end, on real hardware.
 *
 * These are deliberately shallow — every one of them is a "does this screen come up and say the
 * true thing" check, not a unit test. The depth lives in `:core` (117 JVM tests) and in `:app`'s
 * Room-backed JVM suite (61); what neither of those can prove is that the Navigation 3 back stack,
 * the activity intents and the Compose tree actually assemble on a phone.
 */
class AppSmokeTest {

    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Before fun freshInstall() = clearInstall()

    /**
     * The nudge is not "no backup yet" alone — Task 7's ruling is that it appears only once there
     * is something a backup would save (an empty install has nothing to lose). So the fresh state
     * here is: preferences cleared, one asset present, no export ever taken.
     */
    @Test fun dashboardShowsTheBackupNudgeOnAFreshInstall() {
        runBlocking { app.graph.createAsset.run(name = "Smoke asset") }
        rule.awaitText("No backup yet")
        rule.onNodeWithText("No backup yet").assertIsDisplayed()
        rule.onNodeWithText("Export now").assertIsDisplayed()
    }

    /**
     * "Scan" is on screen twice once the destination is open — the bar item and the screen title —
     * so both matchers say which one they mean (the bar item is the clickable one).
     */
    @Test fun bottomBarReachesScanAndShowsReadyToScan() {
        rule.onNode(hasText("Scan") and hasClickAction()).performClick()
        rule.onNode(hasText("Scan") and hasNoClickAction()).assertIsDisplayed()
        rule.awaitText("READY TO SCAN")
        rule.onNodeWithText("READY TO SCAN").assertIsDisplayed()
    }

    /**
     * The empty dashboard's first call to action through the form to the asset's own screen. The
     * name is asserted on the detail screen, where it appears twice: the app bar title and the
     * identity plate's model line.
     */
    @Test fun assetCanBeCreatedFromTheDashboardAndOpens() {
        val name = "Well pump"
        rule.awaitText("Add your first asset")
        rule.onNodeWithText("Add your first asset").performClick()

        rule.awaitText("New asset")
        // Four fields in a fixed order (D12 §7): Name is the first one that takes text.
        rule.onAllNodes(hasSetTextAction()).onFirst().performTextInput(name)
        rule.onNodeWithText("Save asset").performClick()

        rule.awaitText(name, count = 2)
        rule.onAllNodesWithText(name).onFirst().assertIsDisplayed()
    }

    /** The production Backup screen, reached the way the dashboard offers it. */
    @Test fun backupScreenRenders() {
        runBlocking { app.graph.createAsset.run(name = "Smoke asset") }
        rule.awaitText("Export now")
        rule.onNodeWithText("Export now").performClick()

        rule.awaitText("Export backup")
        rule.onNodeWithText("Export backup").assertIsDisplayed()
        rule.onNodeWithText("Import (replace everything)").assertIsDisplayed()
        // "Last backup: Never" — the screen agrees with the nudge that sent us here.
        rule.onNodeWithText("Never").assertIsDisplayed()
    }
}

/**
 * The share sheet's own task (D12 §9). `ShareActivity` needs a real `EXTRA_TEXT`, so it is
 * launched from an explicit intent and asserted through an empty Compose rule — the rule finds
 * whatever composition is on screen, whichever activity owns it.
 */
class ShareActivitySmokeTest {

    @get:Rule val rule = createEmptyComposeRule()

    @Before fun freshInstall() = clearInstall()

    @Test fun sharedWebLinkShowsTheCard() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent = Intent(context, ShareActivity::class.java)
            .setAction(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, "Title\nhttps://example.invalid/x")

        ActivityScenario.launch<ShareActivity>(intent).use {
            // LinkKind.WEB reads "Web page"; the card shouts it (D12 §9).
            rule.awaitText("WEB PAGE")
            rule.onNodeWithText("WEB PAGE").assertIsDisplayed()
            rule.onNodeWithText("Title").assertIsDisplayed()
            rule.onNodeWithText("https://example.invalid/x").assertIsDisplayed()
            rule.onNodeWithText("Write to a new tag").assertIsDisplayed()
        }
    }
}

/**
 * A deep link opened while the app is closed: the cold-start path through `MainActivity.onCreate`.
 *
 * `notenfc://asset/nope` is not a canonical UUID, so `DeepLinkRoute` calls it malformed,
 * `routeFrom` pushes nothing and says so in a snackbar, and the dashboard is what comes up.
 *
 * It is its own class, launched from an empty Compose rule, because `MainActivity` is
 * `singleTask` and answers a second intent through `onNewIntent`/`setIntent`; `ActivityScenario`
 * matches lifecycle events against the intent it launched with, so a scenario that saw its
 * activity's intent swapped underneath it stops tracking it and its teardown times out. Making the
 * deep link the launch intent keeps the two in agreement and proves the path a phone actually
 * takes when a link is opened from another app.
 */
class DeepLinkSmokeTest {

    @get:Rule val rule = createEmptyComposeRule()

    @Before fun freshInstall() = clearInstall()

    @Test fun malformedDeepLinkLandsOnDashboard() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("notenfc://asset/nope"))
            .setClass(context, MainActivity::class.java)

        ActivityScenario.launch<MainActivity>(intent).use {
            rule.awaitText("That link doesn't point at anything here.")
            rule.onNode(hasText("noteNFC") and hasNoClickAction()).assertIsDisplayed()
        }
    }
}
