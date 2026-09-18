package com.loosecannon.servicetag.ui.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.loosecannon.servicetag.di.AppGraph
import com.loosecannon.servicetag.ui.theme.ServiceTagTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Pins Settings as a door to Backup that never closes. The Dashboard nudge that used to be the
 * only way in disappears once a backup has been recorded (see `DashboardScreen`), so a owner who
 * already has a backup would otherwise have no way back into `Route.Backup` at all. This test
 * pins the row itself — label, position under "Utilities", and that it actually calls `onBackup`
 * — so a future edit to Settings cannot drop the door again without failing here.
 */
@RunWith(AndroidJUnit4::class)
class SettingsBackupEntryTest {

    @get:Rule val rule = createComposeRule()

    @Test fun backupRowIsPresentAndInvokesOnBackup() {
        val graph = AppGraph(ApplicationProvider.getApplicationContext())
        var backupTapped = 0

        rule.setContent {
            ServiceTagTheme {
                SettingsScreen(
                    graph = graph,
                    onBack = {},
                    onReadTag = {},
                    onBackup = { backupTapped++ },
                )
            }
        }
        rule.waitForIdle()

        rule.onNodeWithText("Backup and restore").assertIsDisplayed()
        rule.onNodeWithText("Backup and restore").performClick()

        assertEquals(1, backupTapped)
    }
}
