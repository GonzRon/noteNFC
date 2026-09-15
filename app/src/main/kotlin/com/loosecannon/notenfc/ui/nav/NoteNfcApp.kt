package com.loosecannon.notenfc.ui.nav

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.loosecannon.notenfc.di.AppGraph
import com.loosecannon.notenfc.ui.asset.AssetDetailScreen
import com.loosecannon.notenfc.ui.asset.AssetEditScreen
import com.loosecannon.notenfc.ui.asset.AssetsScreen
import com.loosecannon.notenfc.ui.backup.BackupScreen
import com.loosecannon.notenfc.ui.dashboard.DashboardScreen
import com.loosecannon.notenfc.ui.journal.EventDetailScreen
import com.loosecannon.notenfc.ui.journal.EventEntryScreen
import com.loosecannon.notenfc.ui.links.LinkDetailScreen
import com.loosecannon.notenfc.ui.links.LinksScreen
import com.loosecannon.notenfc.ui.scan.ScanScreen
import com.loosecannon.notenfc.ui.scan.TagResultSheet
import com.loosecannon.notenfc.ui.scan.WriteTagScreen
import com.loosecannon.notenfc.ui.settings.SettingsScreen
import com.loosecannon.notenfc.ui.setup.AssetSetupScreen
import com.loosecannon.notenfc.ui.setup.DefinitionEditScreen
import com.loosecannon.notenfc.ui.setup.ProfileEditScreen
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
        // NavDisplay decorates entries with a SaveableStateHolder and nothing else by default,
        // which leaves every `viewModel(...)` inside an entry resolving against the *activity's*
        // store: one instance per key for the life of the process. The sheets and forms here
        // compute their state in `init` or hold a `done` flag, so an activity-scoped model shows
        // the previous visit's answer on the next one, and `onCleared` — where the write screen
        // abandons an unwritten tag — never fires until the activity dies. Scoping each entry to
        // its own ViewModelStore restores per-visit models and clears them on pop. Order matters:
        // the ViewModelStore decorator must come after the saveable-state one.
        NavDisplay(
            backStack = backStack,
            onBack = { backStack.removeLastOrNull() },
            modifier = Modifier.padding(padding),
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
            entryProvider = entryProvider {
                entry<Route.Dashboard> {
                    DashboardScreen(
                        graph = graph,
                        onOpenAsset = { backStack.add(Route.AssetDetail(it)) },
                        onOpenLinks = { backStack.add(Route.Links) },
                        onNewAsset = { backStack.add(Route.AssetEdit(null)) },
                        onBackup = { backStack.add(Route.Backup) },
                        onSettings = { backStack.add(Route.Settings) },
                        // Scan is a destination, not a dialog: the empty dashboard sends the user
                        // to the same place the bottom bar does, so one back press leaves it.
                        onScan = { backStack.switchTopLevel(Route.Scan) },
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
                        onSetup = { backStack.add(Route.AssetSetup(it)) },
                        onWriteTag = { backStack.add(Route.WriteTag("asset", it, null)) },
                        onOpenLinks = { backStack.add(Route.Links) },
                        onBackup = { backStack.add(Route.Backup) },
                        onLogEvent = { asset, profile ->
                            backStack.add(Route.EventEntry(asset, profile, null))
                        },
                        onOpenEvent = { backStack.add(Route.EventDetail(it)) },
                    )
                }
                entry<Route.AssetEdit> { key ->
                    AssetEditScreen(
                        graph = graph,
                        assetId = key.id,
                        // A new asset opens on its own detail screen and the form leaves the stack:
                        // backing out of the asset should not land back on the form that made it.
                        onDone = { id ->
                            backStack.removeLastOrNull()
                            if (key.id == null) backStack.add(Route.AssetDetail(id))
                        },
                        onBack = { backStack.removeLastOrNull() },
                    )
                }
                entry<Route.AssetSetup> { key ->
                    AssetSetupScreen(
                        graph = graph,
                        assetId = key.assetId,
                        onBack = { backStack.removeLastOrNull() },
                        onEditDefinition = { asset, definition ->
                            backStack.add(Route.DefinitionEdit(asset, definition))
                        },
                        onEditProfile = { asset, profile ->
                            backStack.add(Route.ProfileEdit(asset, profile))
                        },
                    )
                }
                entry<Route.DefinitionEdit> { key ->
                    DefinitionEditScreen(
                        graph = graph,
                        assetId = key.assetId,
                        definitionId = key.definitionId,
                        // Saved, archived or deleted, the editor is done: the setup screen behind
                        // it is already watching the rows and redraws itself.
                        onDone = { backStack.removeLastOrNull() },
                        onBack = { backStack.removeLastOrNull() },
                    )
                }
                entry<Route.ProfileEdit> { key ->
                    ProfileEditScreen(
                        graph = graph,
                        assetId = key.assetId,
                        profileId = key.profileId,
                        onDone = { backStack.removeLastOrNull() },
                        onBack = { backStack.removeLastOrNull() },
                    )
                }
                entry<Route.EventEntry> { key ->
                    EventEntryScreen(
                        graph = graph,
                        assetId = key.assetId,
                        profileId = key.profileId,
                        eventId = key.eventId,
                        onDone = { backStack.removeLastOrNull() },
                        onBack = { backStack.removeLastOrNull() },
                    )
                }
                entry<Route.EventDetail> { key ->
                    EventDetailScreen(
                        graph = graph,
                        eventId = key.id,
                        // Edit is the same route in edit mode: the event names its own profile, so
                        // the entry screen does not need one handed to it.
                        onEdit = { asset, event -> backStack.add(Route.EventEntry(asset, null, event)) },
                        onBack = { backStack.removeLastOrNull() },
                    )
                }
                entry<Route.Links> {
                    LinksScreen(
                        graph = graph,
                        onOpenLink = { backStack.add(Route.LinkDetail(it)) },
                        onBack = { backStack.removeLastOrNull() },
                    )
                }
                entry<Route.LinkDetail> { key ->
                    LinkDetailScreen(
                        graph = graph,
                        linkId = key.id,
                        onBack = { backStack.removeLastOrNull() },
                        onWriteTag = { backStack.add(it) },
                    )
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
                        // The sheet is done once it has pushed the next thing: a result is never
                        // somewhere to come back to.
                        onWriteTag = { backStack.removeLastOrNull(); backStack.add(it) },
                        onOpenAsset = { backStack.removeLastOrNull(); backStack.add(Route.AssetDetail(it)) },
                        onNewAsset = { backStack.removeLastOrNull(); backStack.add(Route.AssetEdit(null)) },
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
