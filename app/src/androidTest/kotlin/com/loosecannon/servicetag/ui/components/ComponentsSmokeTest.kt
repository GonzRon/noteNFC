package com.loosecannon.servicetag.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.loosecannon.servicetag.ui.theme.ServiceTagLightSemanticColors
import com.loosecannon.servicetag.ui.theme.ServiceTagTheme
import org.junit.Rule
import org.junit.Test

/** Compiled in Task 3, executed on the phone in Task 8. */
class ComponentsSmokeTest {

    @get:Rule val rule = createComposeRule()

    @Test fun identityPlateShowsDashForBlankValues() {
        rule.setContent {
            ServiceTagTheme {
                IdentityPlate(
                    category = "Battery / power",
                    model = "Rack UPS",
                    name = null,
                    cells = listOf(
                        "Serial" to PlateValue("", mono = true),
                        "NFC tag" to PlateValue("41c11b73 · v1", mono = true),
                    ),
                    icon = Icons.Outlined.Info,
                )
            }
        }
        rule.onNodeWithText("—").assertIsDisplayed()
        rule.onNodeWithText("41c11b73 · v1").assertIsDisplayed()
    }

    @Test fun statusBadgeExposesItsLabelToAccessibility() {
        rule.setContent {
            ServiceTagTheme {
                StatusBadge(label = "Overdue", colors = ServiceTagLightSemanticColors.overdue, icon = Icons.Outlined.Warning)
            }
        }
        rule.onNodeWithContentDescription("Overdue").assertExists()
    }

    @Test fun ledgerEntryShowsItsDateAndTitle() {
        rule.setContent {
            ServiceTagTheme {
                LedgerEntry(
                    day = "10",
                    month = "Jun",
                    year = "2024",
                    title = "Battery replaced",
                    detail = "CyberPower RB1290X2",
                )
            }
        }
        rule.onNodeWithText("10 JUN").assertIsDisplayed()
        rule.onNodeWithText("2024").assertIsDisplayed()
        rule.onNodeWithText("Battery replaced").assertIsDisplayed()
    }

    @Test fun sectionHeaderShowsItsTitle() {
        rule.setContent {
            ServiceTagTheme { SectionHeader(title = "Service record") }
        }
        rule.onNodeWithText("Service record", ignoreCase = true, useUnmergedTree = true).assertIsDisplayed()
    }
}
