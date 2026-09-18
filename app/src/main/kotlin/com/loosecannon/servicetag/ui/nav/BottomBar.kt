package com.loosecannon.servicetag.ui.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Dashboard · Assets and nothing else (2B-2, D12 §16 correction): two destinations, no FAB, no
 * overflow. Backup and Settings are reached from the Dashboard, and Read / inspect tag from
 * Settings, not from a third tab.
 */
@Composable
fun BottomBar(current: Route, onSelect: (Route) -> Unit) {
    NavigationBar {
        TopLevelRoutes.forEach { route ->
            NavigationBarItem(
                selected = route == current,
                onClick = { onSelect(route) },
                icon = { Icon(iconFor(route), contentDescription = null) },
                label = { Text(labelFor(route)) },
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                ),
            )
        }
    }
}

@Composable
private fun iconFor(route: Route): ImageVector = when (route) {
    Route.Assets -> Icons.AutoMirrored.Outlined.List
    else -> Icons.Outlined.Home
}

private fun labelFor(route: Route): String = when (route) {
    Route.Assets -> "Assets"
    else -> "Dashboard"
}
