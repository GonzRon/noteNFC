package com.loosecannon.notenfc.ui.interim

import android.app.Activity
import android.app.AlertDialog
import android.widget.EditText
import android.widget.Toast
import com.loosecannon.notenfc.core.model.TagTarget
import com.loosecannon.notenfc.di.AppGraph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Interim (Phase 1B) chooser: every asset, every link, "New asset…", optionally "no target".
 * Replaced by the Compose bind flow in Phase 1C; kept deliberately plain.
 */
object TargetPicker {
    fun show(activity: Activity, graph: AppGraph, scope: CoroutineScope, allowNone: Boolean, onPicked: (TagTarget) -> Unit) {
        scope.launch {
            val labels = ArrayList<String>()
            val actions = ArrayList<() -> Unit>()
            try {
                val assets = graph.assets.all().sortedBy { it.name.lowercase() }
                val links = graph.links.all().sortedBy { it.label.lowercase() }
                assets.forEach { a -> labels += "Asset: ${a.name}"; actions += { onPicked(TagTarget.AssetTarget(a.id)) } }
                links.forEach { l -> labels += "Link: ${l.label}"; actions += { onPicked(TagTarget.LinkTarget(l.id)) } }
            } catch (e: Exception) {
                // A repository failure here would otherwise escape the caller's MainScope and take
                // the process with it; the picker is not worth a crash.
                Toast.makeText(activity, "Could not load targets: ${e.message}", Toast.LENGTH_LONG).show()
                return@launch
            }
            labels += "New asset…"; actions += { promptNewAsset(activity, graph, scope, onPicked) }
            if (allowNone) { labels += "No target yet (spare tag)"; actions += { onPicked(TagTarget.None) } }
            if (activity.isFinishing || activity.isDestroyed) return@launch
            AlertDialog.Builder(activity)
                .setTitle("Choose what this tag opens")
                .setItems(labels.toTypedArray()) { _, i -> actions[i]() }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    private fun promptNewAsset(activity: Activity, graph: AppGraph, scope: CoroutineScope, onPicked: (TagTarget) -> Unit) {
        val input = EditText(activity).apply { hint = "Asset name" }
        AlertDialog.Builder(activity)
            .setTitle("New asset")
            .setView(input)
            .setPositiveButton("Create") { _, _ ->
                val name = input.text.toString()
                scope.launch {
                    try {
                        val asset = graph.createAsset.run(name)
                        onPicked(TagTarget.AssetTarget(asset.id))
                    } catch (e: IllegalArgumentException) {
                        Toast.makeText(activity, "Give the asset a name.", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(activity, "Could not create the asset: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
