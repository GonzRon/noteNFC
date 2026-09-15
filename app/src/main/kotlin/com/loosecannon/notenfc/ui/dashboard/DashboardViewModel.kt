package com.loosecannon.notenfc.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loosecannon.notenfc.core.model.Asset
import com.loosecannon.notenfc.core.model.AssetStatus
import com.loosecannon.notenfc.core.ports.AssetRepository
import com.loosecannon.notenfc.di.AppGraph
import com.loosecannon.notenfc.prefs.AppPrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/** How long the repository flow stays hot after the last collector leaves (a rotation, typically). */
private const val SUBSCRIPTION_GRACE_MS = 5_000L

/**
 * What the dashboard draws. [needsBackup] is deliberately not `lastBackupAt == null` at the call
 * site: the screen should never have to work out what the absence of an instant means.
 */
data class DashboardState(
    val assets: List<Asset> = emptyList(),
    val needsBackup: Boolean = false,
    val lastBackupAt: Long? = null,
)

/**
 * The landing screen's state: the assets that are in service, and whether a backup has ever been
 * taken. Two different kinds of fact, so they arrive two different ways — the assets from a live
 * repository flow, the backup instant from preferences, which nothing observes.
 *
 * [refresh] is what closes that gap. The screen calls it when it comes back into composition, so
 * an export that happened while the user was on the backup screen puts the nudge out on the next
 * emission rather than on the next process start.
 *
 * The initial state says `needsBackup = false`: a nudge that flashes up before the preferences
 * have been read and then disappears is worse than a nudge that arrives a frame late.
 */
class DashboardViewModel(
    assets: AssetRepository,
    private val prefs: AppPrefs,
) : ViewModel() {

    constructor(graph: AppGraph) : this(graph.assets, graph.prefs)

    private val refreshes = MutableStateFlow(0)

    val state: StateFlow<DashboardState> =
        combine(assets.observeAll(), refreshes) { rows, _ ->
            val last = prefs.lastBackupAt
            DashboardState(
                // CURRENT is the section for assets in service; archived ones are not in it.
                assets = rows.filter { it.status == AssetStatus.ACTIVE },
                needsBackup = last == null,
                lastBackupAt = last,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_GRACE_MS), DashboardState())

    /** Re-read the preferences and emit. Cheap: it is one `SharedPreferences` lookup. */
    fun refresh() = refreshes.update { it + 1 }
}
