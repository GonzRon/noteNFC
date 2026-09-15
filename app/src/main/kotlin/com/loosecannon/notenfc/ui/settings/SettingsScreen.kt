package com.loosecannon.notenfc.ui.settings

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.loosecannon.notenfc.BuildConfig
import com.loosecannon.notenfc.di.AppGraph
import com.loosecannon.notenfc.links.LinkLauncher
import com.loosecannon.notenfc.prefs.AppearanceMode
import com.loosecannon.notenfc.ui.components.LabelValue
import com.loosecannon.notenfc.ui.components.QuietLine
import com.loosecannon.notenfc.ui.components.SectionHeader
import kotlinx.coroutines.launch

/** Where the app's source lives. The only outbound link the app ships with. */
private const val PROJECT_URL = "https://github.com/GonzRon/noteNFC"

/**
 * The few device-local preferences: appearance first.
 *
 * Mode is written to [com.loosecannon.notenfc.prefs.AppPrefs] and then the activity is recreated,
 * because the theme is read once in `setContent`. Recreation is visible — the screen blinks — and
 * that is the accepted Phase 1C behaviour rather than a reactive theme nobody has asked for yet.
 *
 * The Theme row is informational and has no control at all (D12 §14 "Disabled controls":
 * information must not be hidden merely because its action is unavailable).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    graph: AppGraph,
    onBack: () -> Unit,
) {
    val activity = LocalActivity.current
    val prefs = graph.prefs
    var mode by remember { mutableStateOf(prefs.appearanceMode) }
    val snackbars = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbars) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            SectionHeader(title = "Appearance")
            Column(modifier = Modifier.selectableGroup()) {
                AppearanceMode.entries.forEach { option ->
                    ModeRow(
                        label = modeLabel(option),
                        selected = option == mode,
                        onSelect = {
                            if (option != mode) {
                                mode = option
                                prefs.appearanceMode = option
                                // The theme is read once, in `setContent`; this is what re-reads it.
                                activity?.recreate()
                            }
                        },
                    )
                }
            }

            SectionHeader(title = "Theme")
            Column(
                modifier = Modifier.padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                LabelValue(label = "Palette", value = "Apollo Service Binder")
                QuietLine("Dynamic colour arrives in a later release")
            }

            SectionHeader(title = "About")
            LabelValue(
                label = "Version",
                value = BuildConfig.VERSION_NAME,
                modifier = Modifier.padding(vertical = 4.dp),
            )
            TextButton(
                // A phone with no browser is rare but real (a kiosk, a stripped ROM). The link
                // failing quietly would read as the tap not having registered.
                onClick = {
                    val opened = activity?.let { LinkLauncher.open(it, PROJECT_URL) } ?: false
                    if (!opened) {
                        scope.launch { snackbars.showSnackbar("No browser available for this link") }
                    }
                },
                contentPadding = PaddingValues(0.dp),
            ) {
                Text("Source and issues on GitHub")
            }
            QuietLine(PROJECT_URL, Modifier.padding(bottom = 16.dp))
        }
    }
}

/** One radio row. The whole row is the target, so the label is not a decoration next to a dot. */
@Composable
private fun ModeRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect),
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

private fun modeLabel(mode: AppearanceMode): String = when (mode) {
    AppearanceMode.SYSTEM -> "System"
    AppearanceMode.LIGHT -> "Light"
    AppearanceMode.DARK -> "Dark"
}
