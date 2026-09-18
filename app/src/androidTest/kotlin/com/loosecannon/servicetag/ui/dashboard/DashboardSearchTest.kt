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
import com.loosecannon.servicetag.di.AppGraph
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

    /** A hot tub with a circulation pump under it, and the dashboard drawn over that install. */
    private fun aSystemWithOnePart(retireTheSystem: Boolean = false): AppGraph {
        val graph = app.graph
        runBlocking {
            val tub = graph.createAsset.run(AssetCommand(name = "Hot tub", category = "Water"))
            graph.createAsset.run(AssetCommand(name = "Circulation pump", parentAssetId = tub.id))
            if (retireTheSystem) graph.retireAsset.retire(tub.id, "2026-04-02")
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
        return graph
    }

    @Test fun aComponentIsHiddenUntilItIsSearchedForAndThenNamesItsSystem() {
        aSystemWithOnePart()

        // The system is listed; its pump is not, and the screen says where it is instead. The box
        // itself is named by the ratified placeholder, which only shows while it is empty.
        rule.awaitText("Hot tub")
        rule.onAllNodesWithText("Circulation pump").assertCountEquals(0)
        rule.awaitText("Components are listed on the asset they belong to. Search to find one.")
        rule.awaitText("Search assets and components")

        // One keystroke away. The hit names its system, and the system itself drops out because it
        // does not match — which is what proves the list is filtered and not merely extended.
        rule.onNode(hasSetTextAction()).performTextInput("circ")
        rule.awaitText("Circulation pump")
        rule.awaitText("Part of Hot tub")
        rule.onAllNodesWithText("Hot tub").assertCountEquals(0)

        rule.onNodeWithContentDescription("Clear search").performClick()
        rule.awaitText("Hot tub")
        rule.onAllNodesWithText("Circulation pump").assertCountEquals(0)

        // A query that matches nothing is the one state the fourth sentence is for.
        rule.onNode(hasSetTextAction()).performTextInput("zzz")
        rule.awaitText("Nothing matches that.")
        rule.onAllNodesWithText("Hot tub").assertCountEquals(0)
    }

    /**
     * F1 — an empty list under an empty box, reached the way an owner reaches it: retire the system
     * and its pump stays in service, because retiring a parent does not touch its children. The
     * hint already says where the parts are, so the screen must not also claim a search found
     * nothing when nothing was searched for.
     */
    @Test fun anEmptyListUnderAnEmptyBoxIsNotToldItsSearchFoundNothing() {
        aSystemWithOnePart(retireTheSystem = true)

        rule.awaitText("Components are listed on the asset they belong to. Search to find one.")
        rule.onAllNodesWithText("Hot tub").assertCountEquals(0)
        rule.onAllNodesWithText("Nothing matches that.").assertCountEquals(0)

        // And the sentence is still there for the query that earns it.
        rule.onNode(hasSetTextAction()).performTextInput("zzz")
        rule.awaitText("Nothing matches that.")
    }
}
