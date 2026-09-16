package com.loosecannon.servicetag.debug

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.documentfile.provider.DocumentFile
import com.loosecannon.servicetag.NoteNfcApp
import com.loosecannon.servicetag.R
import com.loosecannon.servicetag.backup.SafBackupIO
import com.loosecannon.servicetag.backup.SafBackupSetWriter
import com.loosecannon.servicetag.core.model.Asset
import com.loosecannon.servicetag.core.model.AssetId
import com.loosecannon.servicetag.core.model.AssetStatus
import com.loosecannon.servicetag.core.model.ExternalLink
import com.loosecannon.servicetag.core.model.LinkId
import com.loosecannon.servicetag.core.model.LinkKind
import com.loosecannon.servicetag.core.model.PayloadFormat
import com.loosecannon.servicetag.core.model.TagBinding
import com.loosecannon.servicetag.core.model.TagId
import com.loosecannon.servicetag.core.model.TagStatus
import com.loosecannon.servicetag.core.model.TagTarget
import com.loosecannon.servicetag.di.AppGraph
import com.loosecannon.servicetag.ui.backup.BackupViewModel
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Debug-build-only harness for the Phase 1A backup path: seed a small graph, export it to a
 * Storage Access Framework document, wipe the database, import it back, and watch the counts.
 * Deliberately crude — plain [Activity], plain `android.widget` views, no AppCompat, no Compose —
 * because it is scaffolding for a manual restore test, not product UX (D7 Phase 1A: no new UI).
 */
class DebugBackupActivity : Activity() {

    private val scope = MainScope()
    private lateinit var counts: TextView

    private val graph: AppGraph
        get() = (application as NoteNfcApp).graph

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_debug_backup)
        counts = findViewById(R.id.debug_counts)

        findViewById<Button>(R.id.debug_seed).setOnClickListener { seed() }
        findViewById<Button>(R.id.debug_export).setOnClickListener { pickExportTarget() }
        findViewById<Button>(R.id.debug_import).setOnClickListener { pickImportSource() }
        findViewById<Button>(R.id.debug_wipe).setOnClickListener { wipe() }

        refreshCounts()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    // --- actions ---------------------------------------------------------------------------

    private fun seed() = run("seed") {
        val g = graph
        val now = g.clock.nowMillis()
        val assetIds = List(2) { AssetId(g.ids.newId()) }
        val linkIds = List(2) { LinkId(g.ids.newId()) }

        g.uow.write {
            assetIds.forEachIndexed { i, id ->
                g.assets.upsert(
                    Asset(
                        id = id,
                        name = "Sample asset ${i + 1}",
                        description = "seeded at $now",
                        category = if (i == 0) "HVAC" else "Yard",
                        notes = "",
                        status = AssetStatus.ACTIVE,
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
            }
            // one link belongs to the first asset, one stands alone
            g.links.upsert(
                ExternalLink(
                    id = linkIds[0],
                    assetId = assetIds[0],
                    kind = LinkKind.JOPLIN,
                    label = "Service log",
                    uri = "joplin://x-callback-url/openNote?id=${g.ids.newId()}",
                    createdAt = now,
                    lastOpenedAt = null,
                    updatedAt = now,
                ),
            )
            g.links.upsert(
                ExternalLink(
                    id = linkIds[1],
                    assetId = null,
                    kind = LinkKind.WEB,
                    label = "Manual",
                    uri = "https://example.invalid/${g.ids.newId()}",
                    createdAt = now,
                    lastOpenedAt = null,
                    updatedAt = now,
                ),
            )
            // one tag on an asset, one on the standalone link, one unbound spare
            g.tags.upsert(
                seedTag(
                    id = g.ids.newId(),
                    format = PayloadFormat.LEGACY_MD5,
                    target = TagTarget.AssetTarget(assetIds[0]),
                    status = TagStatus.ACTIVE,
                    label = "on the asset",
                    now = now,
                ),
            )
            g.tags.upsert(
                seedTag(
                    id = g.ids.newId(),
                    format = PayloadFormat.V1,
                    target = TagTarget.LinkTarget(linkIds[1]),
                    status = TagStatus.ACTIVE,
                    label = "on the manual",
                    now = now,
                ),
            )
            g.tags.upsert(
                seedTag(
                    id = g.ids.newId(),
                    format = PayloadFormat.V1,
                    target = TagTarget.None,
                    status = TagStatus.UNBOUND,
                    label = "spare",
                    now = now,
                ),
            )
        }
        "seeded 2 assets, 2 links, 3 tags"
    }

    private fun seedTag(
        id: String,
        format: PayloadFormat,
        target: TagTarget,
        status: TagStatus,
        label: String,
        now: Long,
    ) = TagBinding(
        id = TagId(id),
        payloadFormat = format,
        payloadKey = id, // unique per seed run: (payload_format, payload_key) is a unique index
        target = target,
        status = status,
        label = label,
        physicalUid = null,
        writtenAt = null,
        lastScannedAt = null,
        createdAt = now,
        updatedAt = now,
    )

    private fun wipe() = run("wipe") {
        val g = graph
        g.uow.write {
            g.tags.deleteAll()
            g.links.deleteAll()
            g.assets.deleteAll()
        }
        "wiped"
    }

    /** A folder, not a document: an export is a *set* of two files now (spec §7.3). */
    private fun pickExportTarget() {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), REQUEST_EXPORT)
    }

    private fun pickImportSource() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            // Widened from application/zip: providers hand backups back as octet-stream often enough
            // that a strict filter hides the file the user just wrote.
            .setType("*/*")
            .putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(MIME_ZIP, "application/octet-stream"))
        startActivityForResult(intent, REQUEST_IMPORT)
    }

    @Deprecated("plain Activity has no ActivityResult API; adequate for a debug harness")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri: Uri? = data?.data
        if (resultCode != RESULT_OK || uri == null) {
            if (requestCode == REQUEST_EXPORT || requestCode == REQUEST_IMPORT) report("cancelled")
            return
        }
        when (requestCode) {
            REQUEST_EXPORT -> run("export") {
                // The same code path the Backup screen uses, including the rule that both files
                // land or neither does: a harness that exported differently would prove nothing.
                val tree = DocumentFile.fromTreeUri(applicationContext, uri)
                    ?: error("cannot open the chosen folder")
                val sink = SafBackupSetWriter(applicationContext, contentResolver, tree)
                "exported " + BackupViewModel(graph).exportSet(sink).getOrThrow()
            }
            REQUEST_IMPORT -> run("import") {
                val bytes = SafBackupIO(contentResolver, uri).read()
                val r = graph.importBackupReplace.run(bytes)
                "imported v${r.formatVersion}: ${r.assets} assets, ${r.tags} tags, ${r.links} links"
            }
        }
    }

    // --- plumbing --------------------------------------------------------------------------

    /** Runs [block] off the main thread's critical path, then reports it, then refreshes counts. */
    private fun run(what: String, block: suspend () -> String) {
        scope.launch {
            val message = try {
                block()
            } catch (e: Exception) {
                "$what failed: ${e.javaClass.simpleName}: ${e.message}"
            }
            report(message)
        }
    }

    private fun report(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        refreshCounts(message)
    }

    private fun refreshCounts(note: String? = null) {
        scope.launch {
            val line = try {
                val g = graph
                "assets ${g.assets.all().size} / tags ${g.tags.all().size} / links ${g.links.all().size}"
            } catch (e: Exception) {
                "counts unavailable: ${e.javaClass.simpleName}: ${e.message}"
            }
            counts.text = if (note == null) line else "$line\n\n$note"
        }
    }

    private companion object {
        const val REQUEST_EXPORT = 1
        const val REQUEST_IMPORT = 2
        const val MIME_ZIP = "application/zip"
    }
}
