package com.loosecannon.notenfc.ui.nav

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Every destination the single activity can show (D12 §3). Keys are `@Serializable` so
 * `rememberNavBackStack` can save and restore the stack through process death; they carry ids and
 * never whole domain objects, which is what keeps a restored stack honest after a backup import.
 */
sealed interface Route : NavKey {
    @Serializable data object Dashboard : Route
    @Serializable data object Assets : Route
    @Serializable data class AssetDetail(val id: String) : Route
    /**
     * New when [id] is null. [parentId] is the "Part of" a new asset opens with, which is how
     * "+ Add component" on a parent's screen makes a child (spec §9); it is ignored on an edit,
     * whose stored parent always wins.
     */
    @Serializable data class AssetEdit(val id: String?, val parentId: String? = null) : Route

    /** What this asset measures and what can be logged against it — the editors of spec §9. */
    @Serializable data class AssetSetup(val assetId: String) : Route

    /** New when [definitionId] is null, otherwise that reading of [assetId]. */
    @Serializable data class DefinitionEdit(val assetId: String, val definitionId: String?) : Route

    /** New when [profileId] is null, otherwise that action of [assetId]. */
    @Serializable data class ProfileEdit(val assetId: String, val profileId: String?) : Route

    /**
     * New when [eventId] is null; [profileId] null is a free-form entry with no profile behind it.
     * [kind] presets the kind of such an entry — the retirement follow-on of spec §7 opens
     * REPLACEMENT or NOTE — and is the name of an `EventKind`, never an index.
     */
    @Serializable data class EventEntry(
        val assetId: String,
        val profileId: String?,
        val eventId: String?,
        val kind: String? = null,
    ) : Route
    @Serializable data class EventDetail(val id: String) : Route

    @Serializable data object Links : Route
    @Serializable data class LinkDetail(val id: String) : Route
    @Serializable data object Scan : Route
    @Serializable data class TagResult(val format: String, val key: String) : Route
    @Serializable data class WriteTag(val targetKind: String, val targetId: String?, val label: String?) : Route
    @Serializable data object Backup : Route
    @Serializable data object Settings : Route
}

/**
 * The two roots the bottom bar switches between; nothing else ever shows it. [Route.Scan] is a
 * pushed destination reached from Settings or the dashboard's empty-state action, not a tab
 * (D12 §16 correction, spec §9): normal tag reading is ambient dispatch, so "Scan" does not earn
 * a slot in the primary navigation for something the app never asks the user to open.
 */
val TopLevelRoutes: List<Route> = listOf(Route.Dashboard, Route.Assets)
