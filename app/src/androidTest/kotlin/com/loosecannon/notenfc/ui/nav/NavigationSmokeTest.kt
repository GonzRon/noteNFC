package com.loosecannon.notenfc.ui.nav

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasNoClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import com.loosecannon.notenfc.MainActivity
import org.junit.Rule
import org.junit.Test

/**
 * Compiled in Task 4, executed on the phone in Task 8.
 *
 * "Dashboard" and "Scan" each appear twice on screen — once as a bottom-bar label and once as the
 * screen's title — so every matcher here says which of the two it means: the bar item is the
 * clickable one, the title is not.
 */
class NavigationSmokeTest {

    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun dashboardIsTheStartDestination() {
        rule.onNode(hasText("Dashboard") and hasNoClickAction()).assertIsDisplayed()
    }

    @Test fun bottomBarSwitchesToScan() {
        rule.onNode(hasText("Scan") and hasClickAction()).performClick()
        rule.onNode(hasText("Scan") and hasNoClickAction()).assertIsDisplayed()
    }
}
