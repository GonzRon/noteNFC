package com.loosecannon.servicetag.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.loosecannon.servicetag.MainActivity
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * The Phase 2A device smoke test: the journal, end to end, on real hardware.
 *
 * One test, and it is the hot-tub acceptance in miniature — seed an asset from the `hot_tub`
 * template, open its water test, type two readings, watch the live state, save, and find the
 * event in the Service Record with the same number and the same state in Current Readings. Every
 * layer 2A added is on that path: the seed templates, `ApplyTemplate`, `LogEvent`, the Room v2
 * schema, `LatestReadings`, `RangeState` and both journal screens.
 *
 * Like the 1C suite it is deliberately shallow; the depth is in the JVM suites. What it proves is
 * that the pieces assemble on a phone.
 *
 * It takes the cold-start empty-rule shape of [DeepLinkSmokeTest] rather than
 * `createAndroidComposeRule<MainActivity>()` + `startActivity`: `MainActivity` is `singleTask` and
 * answers a second intent through `onNewIntent`/`setIntent`, which makes `ActivityScenario` stop
 * tracking the activity it launched (1C's lesson). So the asset's deep link *is* the launch
 * intent, which is also the path a tag tap takes. The asset therefore has to exist before the
 * activity does, which is why it is created in the test body and not in `@Before`.
 */
class JournalSmokeTest {

    @get:Rule val rule = createEmptyComposeRule()

    /** Destructive, exactly as the 1C suite: no preferences, no rows, no journal. */
    @Before fun freshInstall() = clearInstall()

    @Test fun hotTubWaterTestShowsInRecordAndReadings() {
        val id = runBlocking { app.graph.createAsset.run("Spa", templateKey = "hot_tub").id.value }
        val context = ApplicationProvider.getApplicationContext<Context>()
        // Open the asset through the deep link so the test does not depend on any list copy.
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("servicetag://asset/$id"))
            .setClass(context, MainActivity::class.java)

        ActivityScenario.launch<MainActivity>(intent).use {
            // The template's two profiles become the first two quick actions on the asset.
            rule.awaitText("Log water test")
            rule.onNodeWithText("Log water test").performClick()

            // The entry screen names itself with the profile and the asset, both shouted.
            rule.awaitText("WATER TEST · SPA")
            rule.onNodeWithTag("value-ph").performTextInput("7.9")
            rule.onNodeWithTag("value-free_chlorine").performTextInput("2.0")
            // pH 7.9 is above the template's 7.2–7.8, and the row says so while it is being typed.
            rule.awaitText("HIGH")
            rule.onNodeWithText("HIGH").assertIsDisplayed()

            // The app bar's Save is the one that commits; the button at the foot of the form reads
            // "Record entry", so this is unambiguous — `onFirst` only guards against a future twin.
            rule.onAllNodesWithText("Save").onFirst().performClick()

            // Back on the asset. `SectionHeader` renders its title uppercase, so that is what the
            // semantics tree carries.
            rule.awaitText("SERVICE RECORD")
            // Readings first, because they are the top of the screen: the stored measurement,
            // formatted to the definition's one decimal, and its badge. HIGH is on screen twice
            // now — the current-readings badge and the ledger entry's — so take the first.
            rule.onNodeWithText("7.9").performScrollTo().assertIsDisplayed()
            rule.onAllNodesWithText("HIGH").onFirst().performScrollTo().assertIsDisplayed()
            // The ledger entry takes its title from the event, which took it from the profile. The
            // asset screen is one `verticalScroll` Column and the Service Record sits below the
            // fold on a phone, so scroll to it rather than asserting a node that merely exists.
            rule.onNodeWithText("Water test").performScrollTo().assertIsDisplayed()
        }
    }
}
