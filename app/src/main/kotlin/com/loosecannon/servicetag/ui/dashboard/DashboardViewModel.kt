package com.loosecannon.servicetag.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loosecannon.servicetag.core.model.Asset
import com.loosecannon.servicetag.core.model.AssetStatus
import com.loosecannon.servicetag.core.model.isRetired
import com.loosecannon.servicetag.core.ports.AssetRepository
import com.loosecannon.servicetag.di.AppGraph
import com.loosecannon.servicetag.prefs.AppPrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/** How long the repository flow stays hot after the last collector leaves (a rotation, typically). */
private const val SUBSCRIPTION_GRACE_MS = 5_000L

/**
 * One row of the dashboard list: the asset, and — for a component a search has surfaced — the name
 * of the asset it is part of. A component row on its own would be a name with no home, and the
 * whole point of letting the search reach components is that the hit can be understood.
 */
data class DashboardRow(val asset: Asset, val parentName: String? = null)

/**
 * What the dashboard draws. [needsBackup] is deliberately not `lastBackupAt == null` at the call
 * site: the screen should never have to work out what the absence of an instant means. [assets] is
 * likewise already filtered — by lifecycle, by [query], and by whether a row is a component of
 * something else — so no rule about what belongs in the list lives on the screen.
 *
 * [anyInService] and [hiddenComponents] exist because an empty list has three different meanings.
 * Nothing in service at all is a first run and gets the empty state; nothing *matching* is a search
 * that found nothing; and a list that is short because the components are on their own systems is
 * neither, and says so once.
 */
data class DashboardState(
    val assets: List<DashboardRow> = emptyList(),
    /** What the search box holds, verbatim. Blank means "the systems, and not their parts". */
    val query: String = "",
    /** Whether anything is in service at all, before [query] is applied. */
    val anyInService: Boolean = false,
    /** How many in-service components there are, whatever the query; read only while it is blank. */
    val hiddenComponents: Int = 0,
    val needsBackup: Boolean = false,
    val lastBackupAt: Long? = null,
)

/**
 * The fields a search reaches, and why these six (#39): the name; the category the Assets list
 * already shows as its own subtitle, so someone who typed "pump" there expects it to work here;
 * and the four an owner reads off the machine itself when they cannot remember what they called it
 * — make, model, serial, and where the thing is. `description`, `notes`, `vendor` and the warranty
 * prose are deliberately out: they are paragraphs, and a row that shows a name and its parent
 * could not explain a hit buried in one.
 */
private val SEARCHED_FIELDS: List<(Asset) -> String> = listOf(
    Asset::name,
    Asset::category,
    Asset::manufacturer,
    Asset::model,
    Asset::serialNumber,
    Asset::location,
)

/** Case-insensitive substring over [SEARCHED_FIELDS]. A blank query matches everything. */
internal fun Asset.matches(query: String): Boolean {
    val needle = query.trim()
    if (needle.isEmpty()) return true
    return SEARCHED_FIELDS.any { field -> field(this).contains(needle, ignoreCase = true) }
}

/**
 * The landing screen's state: the assets that are in service, what the search box holds, and
 * whether a backup has ever been taken. Three different kinds of fact, so they arrive three
 * different ways — the rows from live repository flows, the query from the screen, the backup
 * instant from preferences, which nothing observes.
 *
 * [refresh] is what closes that last gap. The screen calls it when it comes back into composition,
 * so an export that happened while the user was on the backup screen puts the nudge out on the next
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
    private val queries = MutableStateFlow("")

    /**
     * What the search box draws itself from, synchronously (F3). [DashboardState.query] carries the
     * same string, but it arrives through `combine` and `stateIn` — an internal channel and a
     * sharing coroutine — so it is not guaranteed to be back before the next keystroke, which is
     * how characters get dropped and the cursor jumps to the end mid-word. Every other hoisted
     * field in this app reads its own `asStateFlow()` for exactly that reason; the filtering still
     * happens off [DashboardState.query] and nothing about the list's rules moves here.
     */
    val query: StateFlow<String> = queries.asStateFlow()

    val state: StateFlow<DashboardState> =
        combine(assets.observeAll(), refreshes, queries) { rows, _, query ->
            val last = prefs.lastBackupAt
            val active = rows.filter { it.status == AssetStatus.ACTIVE }
            val inService = active.filterNot { it.isRetired }
            // Parent names come from every row, not just the in-service ones: a component of a
            // retired machine is itself in service and still has to say whose component it is.
            val byId = rows.associateBy { it.id }
            val matching = inService.filter { it.matches(query) }
            // The list is the systems. A blank query keeps the parts on the systems they belong to
            // (#39); typing brings them back, because that is the one place a hidden part is asked
            // for by name. The order is whatever the repository delivered, exactly as before.
            val shown = if (query.isBlank()) matching.filter { it.parentAssetId == null } else matching
            DashboardState(
                assets = shown.map { row ->
                    DashboardRow(asset = row, parentName = row.parentAssetId?.let { byId[it]?.name })
                },
                query = query,
                anyInService = inService.isNotEmpty(),
                hiddenComponents = inService.count { it.parentAssetId != null },
                // An empty install has nothing to lose, and a nudge over an empty dashboard is
                // noise: the offer only means something once there is something to survive the
                // phone change.
                // The nudge counts every active asset, retired included; CURRENT above excludes them.
                needsBackup = last == null && active.isNotEmpty(),
                lastBackupAt = last,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_GRACE_MS), DashboardState())

    /** Re-read the preferences and emit. Cheap: it is one `SharedPreferences` lookup. */
    fun refresh() = refreshes.update { it + 1 }

    /**
     * What the search box holds. Filtering is a pass over rows already in hand, so a keystroke runs
     * no query and needs no debounce; the one cost is that the preference lookup above happens
     * again per keystroke, which is the same single lookup [refresh] is built on.
     */
    fun onQueryChange(value: String) { queries.value = value }

    /** The clear action. Separate from `onQueryChange("")` so the screen states its intent. */
    fun clearQuery() { queries.value = "" }
}
