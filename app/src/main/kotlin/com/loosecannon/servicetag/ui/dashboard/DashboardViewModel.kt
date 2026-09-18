package com.loosecannon.servicetag.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loosecannon.servicetag.core.model.Asset
import com.loosecannon.servicetag.core.model.AssetStatus
import com.loosecannon.servicetag.core.model.isRetired
import com.loosecannon.servicetag.core.ports.AssetRepository
import com.loosecannon.servicetag.core.ports.LinkRepository
import com.loosecannon.servicetag.di.AppGraph
import com.loosecannon.servicetag.prefs.AppPrefs
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
 * taken. Two different kinds of fact, so they arrive two different ways — the rows from live
 * repository flows, the backup instant from preferences, which nothing observes.
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
    links: LinkRepository,
    private val prefs: AppPrefs,
) : ViewModel() {

    constructor(graph: AppGraph) : this(graph.assets, graph.links, graph.prefs)

    private val refreshes = MutableStateFlow(0)

    val state: StateFlow<DashboardState> =
        combine(assets.observeAll(), links.observeAll(), refreshes) { rows, linkRows, _ ->
            val last = prefs.lastBackupAt
            val active = rows.filter { it.status == AssetStatus.ACTIVE }
            val inService = active.filterNot { it.isRetired }
            DashboardState(
                // CURRENT is the section for assets in service. A retired asset is out of service
                // exactly as an archived one is (spec §7), so it is not in it either — but it is
                // still something a backup would lose, which is why the nudge below counts it.
                assets = inService,
                // An empty install has nothing to lose, and a nudge over an empty dashboard is
                // noise: the offer only means something once there is something to survive the
                // phone change. Links count — a standalone link is data the backup carries too.
                needsBackup = last == null && (active.isNotEmpty() || linkRows.isNotEmpty()),
                lastBackupAt = last,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_GRACE_MS), DashboardState())

    /** Re-read the preferences and emit. Cheap: it is one `SharedPreferences` lookup. */
    fun refresh() = refreshes.update { it + 1 }
}
