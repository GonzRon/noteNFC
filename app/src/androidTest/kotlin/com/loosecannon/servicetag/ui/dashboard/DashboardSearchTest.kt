package com.loosecannon.servicetag.ui.dashboard

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.loosecannon.servicetag.core.usecase.AssetCommand
import com.loosecannon.servicetag.ui.app
import com.loosecannon.servicetag.ui.awaitText
import com.loosecannon.servicetag.ui.clearInstall
import com.loosecannon.servicetag.ui.theme.ServiceTagTheme
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 2.7 (#39) — the dashboard lists systems, and the box is how a part of one is reached from here.
 * The JVM cases pin every rule; what only a device can show is that the field takes a keystroke,
 * that the list redraws from it, and that the clear glyph puts it all back.
 *
 * Emulator only — the suite wipes app data.
 */
@RunWith(AndroidJUnit4::class)
class DashboardSearchTest {

    @get:Rule val rule = createComposeRule()

    @Before fun freshInstall() = clearInstall()

    @Test fun aComponentIsHiddenUntilItIsSearchedForAndThenNamesItsSystem() {
        val graph = app.graph
        runBlocking {
            val tub = graph.createAsset.run(AssetCommand(name = "Hot tub", category = "Water"))
            graph.createAsset.run(AssetCommand(name = "Circulation pump", parentAssetId = tub.id))
        }

        rule.setContent {
            ServiceTagTheme {
                DashboardScreen(
                    graph = graph,
                    onOpenAsset = {},
                    onNewAsset = {},
                    onBackup = {},
                    onSettings = {},
                    onScan = {},
                )
            }
        }

        // The system is listed; its pump is not, and the screen says where it is instead.
        rule.awaitText("Hot tub")
        rule.onAllNodesWithText("Circulation pump").assertCountEquals(0)
        rule.awaitText("Components are listed on the asset they belong to. Search to find one.")

        // One keystroke away. The hit names its system, and the system itself drops out because it
        // does not match — which is what proves the list is filtered and not merely extended.
        rule.onNode(hasSetTextAction()).performTextInput("circ")
        rule.awaitText("Circulation pump")
        rule.awaitText("Part of Hot tub")
        rule.onAllNodesWithText("Hot tub").assertCountEquals(0)

        rule.onNodeWithContentDescription("Clear search").performClick()
        rule.awaitText("Hot tub")
        rule.onAllNodesWithText("Circulation pump").assertCountEquals(0)
    }
}
