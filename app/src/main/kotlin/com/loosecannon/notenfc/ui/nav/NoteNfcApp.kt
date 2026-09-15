package com.loosecannon.notenfc.ui.nav

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.loosecannon.notenfc.di.AppGraph
import com.loosecannon.notenfc.ui.asset.AssetDetailScreen
import com.loosecannon.notenfc.ui.asset.AssetEditScreen
import com.loosecannon.notenfc.ui.asset.AssetsScreen
import com.loosecannon.notenfc.ui.backup.BackupScreen
import com.loosecannon.notenfc.ui.dashboard.DashboardScreen
import com.loosecannon.notenfc.ui.links.LinkDetailScreen
import com.loosecannon.notenfc.ui.links.LinksScreen
import com.loosecannon.notenfc.ui.scan.ScanScreen
import com.loosecannon.notenfc.ui.scan.TagResultSheet
import com.loosecannon.notenfc.ui.scan.WriteTagScreen
import com.loosecannon.notenfc.ui.settings.SettingsScreen
import kotlinx.coroutines.flow.SharedFlow

/**
 * The whole app below the theme: one back stack, one `NavDisplay`, one bottom bar (D12 §3).
 * The activity owns the two flows because only the activity sees intents; everything else about
 * navigation lives here, so no screen ever has to know what an `Intent` is.
 */
@Composable
fun NoteNfcApp(graph: AppGraph, deepLinks: SharedFlow<Route>, snackbars: SharedFlow<String>) {
    val backStack = rememberNavBackStack(Route.Dashboard)
    val snackbarHost = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { deepLinks.collect { backStack.add(it) } }
    LaunchedEffect(Unit) { snackbars.collect { snackbarHost.showSnackbar(it) } }

    val current = backStack.lastOrNull()
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHost) },
        bottomBar = {
            if (current is Route && current in TopLevelRoutes) {
                BottomBar(current = current, onSelect = { backStack.switchTopLevel(it) })
            }
        },
    ) { padding ->
        NavDisplay(
            backStack = backStack,
            onBack = { backStack.removeLastOrNull() },
            modifier = Modifier.padding(padding),
            entryProvider = entryProvider {
                entry<Route.Dashboard> {
                    DashboardScreen(
                        graph = graph,
                        onOpenAsset = { backStack.add(Route.AssetDetail(it)) },
                        onOpenLinks = { backStack.add(Route.Links) },
                        onNewAsset = { backStack.add(Route.AssetEdit(null)) },
                        onBackup = { backStack.add(Route.Backup) },
                        onSettings = { backStack.add(Route.Settings) },
                    )
                }
                entry<Route.Assets> {
                    AssetsScreen(
                        graph = graph,
                        onOpenAsset = { backStack.add(Route.AssetDetail(it)) },
                        onNewAsset = { backStack.add(Route.AssetEdit(null)) },
                    )
                }
                entry<Route.AssetDetail> { key ->
                    AssetDetailScreen(
                        graph = graph,
                        assetId = key.id,
                        onBack = { backStack.removeLastOrNull() },
                        onEdit = { backStack.add(Route.AssetEdit(it)) },
                        onWriteTag = { backStack.add(Route.WriteTag("asset", it, null)) },
                    )
                }
                entry<Route.AssetEdit> { key ->
                    AssetEditScreen(graph = graph, assetId = key.id, onDone = { backStack.removeLastOrNull() })
                }
                entry<Route.Links> {
                    LinksScreen(
                        graph = graph,
                        onOpenLink = { backStack.add(Route.LinkDetail(it)) },
                        onBack = { backStack.removeLastOrNull() },
                    )
                }
                entry<Route.LinkDetail> { key ->
                    LinkDetailScreen(graph = graph, linkId = key.id, onBack = { backStack.removeLastOrNull() })
                }
                entry<Route.Scan> {
                    ScanScreen(graph = graph, onResolved = { backStack.add(it) })
                }
                entry<Route.TagResult> { key ->
                    TagResultSheet(
                        graph = graph,
                        format = key.format,
                        key = key.key,
                        onDismiss = { backStack.removeLastOrNull() },
                        onWriteTag = { backStack.add(it) },
                    )
                }
                entry<Route.WriteTag> { key ->
                    WriteTagScreen(graph = graph, key = key, onDone = { backStack.removeLastOrNull() })
                }
                entry<Route.Backup> {
                    BackupScreen(graph = graph, onBack = { backStack.removeLastOrNull() })
                }
                entry<Route.Settings> {
                    SettingsScreen(graph = graph, onBack = { backStack.removeLastOrNull() })
                }
            },
        )
    }
}

/**
 * Top-level switch keeps one entry per destination at the root: tapping Assets from three screens
 * deep inside Dashboard lands on Assets, not on Assets stacked on that history. `add` follows
 * `clear` unconditionally, so the stack is never left empty.
 */
private fun MutableList<NavKey>.switchTopLevel(route: Route) {
    clear()
    add(route)
}
