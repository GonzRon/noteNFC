package com.loosecannon.notenfc.ui.backup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loosecannon.notenfc.backup.BackupSetNames
import com.loosecannon.notenfc.core.backup.ArtifactsCodec
import com.loosecannon.notenfc.core.backup.ArtifactsSetMismatch
import com.loosecannon.notenfc.core.backup.ArtifactsWriteFailed
import com.loosecannon.notenfc.core.backup.ArtifactsWritten
import com.loosecannon.notenfc.core.backup.BackupSetIncomplete
import com.loosecannon.notenfc.core.ports.AttachmentStorage
import com.loosecannon.notenfc.core.ports.BackupIO
import com.loosecannon.notenfc.core.ports.Clock
import com.loosecannon.notenfc.core.ports.StoreIoException
import com.loosecannon.notenfc.core.usecase.ArtifactsReport
import com.loosecannon.notenfc.core.usecase.ExportBackupSet
import com.loosecannon.notenfc.core.usecase.ImportBackupReplace
import com.loosecannon.notenfc.core.usecase.ImportReport
import com.loosecannon.notenfc.core.usecase.RestoreArtifacts
import com.loosecannon.notenfc.di.AppGraph
import com.loosecannon.notenfc.prefs.AppPrefs
import java.io.OutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A destination for one export: two named files, and a way to take one back. */
interface BackupSetSink {
    /** Returns a handle the caller can pass to [delete] — in the app, the document's URI. */
    suspend fun write(name: String, body: suspend (OutputStream) -> Unit): String
    suspend fun delete(handle: String)
}

/** When the last export landed, whether one is in flight, and which set was restored here. */
data class BackupState(
    val lastBackupAt: Long? = null,
    val busy: Boolean = false,
    /** The set id of the last data archive restored here, which the files archive must match. */
    val lastRestoredBackupSetId: String? = null,
)

/**
 * The backup *set* and the two-step restore, over ports the caller chose — in the app a folder and
 * SAF documents, in a test a map of byte arrays. Nothing here knows what a `ContentResolver` is,
 * and the one rule that matters can therefore be tested at all:
 *
 * the preferences are marked **only after** both files have landed and the second one covers the
 * plan. A backup nudge is a promise that a *restorable set* exists; a data archive with no
 * artifacts archive beside it is not one, so a failed export moves nothing.
 */
class BackupViewModel(
    private val exportBackupSet: ExportBackupSet,
    private val importBackupReplace: ImportBackupReplace,
    private val restoreArtifacts: RestoreArtifacts,
    private val storage: AttachmentStorage,
    private val prefs: AppPrefs,
    private val clock: Clock,
) : ViewModel() {

    constructor(graph: AppGraph) : this(
        graph.exportBackupSet, graph.importBackupReplace, graph.restoreArtifacts,
        graph.attachmentStorage, graph.prefs, graph.clock,
    )

    private val _state = MutableStateFlow(
        BackupState(
            lastBackupAt = prefs.lastBackupAt,
            lastRestoredBackupSetId = prefs.lastRestoredBackupSetId,
        ),
    )
    val state: StateFlow<BackupState> = _state.asStateFlow()

    /**
     * One line per finished operation. No replay and a buffer of one: the screen that started the
     * work is told how it went, and a screen that arrives later is not told again.
     */
    private val _messages = MutableSharedFlow<String>(replay = 0, extraBufferCapacity = 1)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    /**
     * Writes both archives and marks the export **only when both landed** (spec §7.3): the nudge
     * is a promise that a restorable set exists, and a data file with no artifacts beside it is
     * not one. A failed artifacts write deletes the data file this export already wrote.
     *
     * Completeness rule (owner's ruling, spec §7.3): the set is a backup only if every managed
     * row's bytes landed in the artifacts archive with the planned size. A missing or drifted
     * file fails the whole export, both files are removed, and `lastBackupAt` does not move.
     */
    suspend fun exportSet(sink: BackupSetSink): Result<String> = runCatching {
        val set = exportBackupSet.run()
        val stamp = BackupSetNames.stamp(set.plan.createdAt)
        val dataHandle = sink.write(BackupSetNames.data(stamp)) { out -> out.write(set.data) }
        var artifactsHandle: String? = null
        try {
            lateinit var written: ArtifactsWritten
            artifactsHandle = sink.write(BackupSetNames.artifacts(stamp)) { out ->
                // With zero attachments this is a manifest-only archive: a set is always two files.
                written =
                    ArtifactsCodec.write(out, set.plan) { locator -> storage.store()?.open(locator) }
            }
            if (!written.covers(set.plan)) {
                throw BackupSetIncomplete(written.missing, written.mismatched)
            }
        } catch (t: Throwable) {
            // [NonCancellable] because the failure being handled is often a cancellation, and a
            // sink's `delete` suspends: on an already-cancelled coroutine it would throw before
            // doing anything and leave the data archive behind — the one thing the invariant
            // forbids. Each delete stands alone, so one that fails cannot stop the other.
            //
            // The SAF sink already removed a partial artifacts document; deleting a handle it
            // never returned cannot happen, and deleting one it did is harmless.
            withContext(NonCancellable) {
                artifactsHandle?.let { handle -> runCatching { sink.delete(handle) } }
                runCatching { sink.delete(dataHandle) }
            }
            // Past the data write, the owner's news is the same whatever failed down here: the
            // second half did not happen and the first half is gone again. The completeness
            // ruling keeps its own wording, and a cancellation is not a failure at all.
            when (t) {
                is BackupSetIncomplete, is CancellationException, is ArtifactsWriteFailed -> throw t
                else -> throw ArtifactsWriteFailed("the files archive could not be written", t)
            }
        }
        prefs.markBackupExported(clock.nowMillis())
        _state.update { it.copy(lastBackupAt = prefs.lastBackupAt) }
        BackupSetNames.data(stamp) + " + " + BackupSetNames.artifacts(stamp)
    }.rethrowCancellation()

    /** Wipes and loads the data archive, and remembers the set id the files step must match. */
    suspend fun restoreData(io: BackupIO): Result<ImportReport> = runCatching {
        val report = importBackupReplace.run(io.read())
        prefs.lastRestoredBackupSetId = report.lastRestoredBackupSetId
        _state.update { it.copy(lastRestoredBackupSetId = prefs.lastRestoredBackupSetId) }
        report
    }.rethrowCancellation()

    /** Adds the bytes the data archive only listed. It deletes nothing, so there is no dialog. */
    suspend fun restoreFiles(io: BackupIO): Result<ArtifactsReport> = runCatching {
        io.openStream().use { restoreArtifacts.run(it, prefs.lastRestoredBackupSetId) }
    }.rethrowCancellation()

    /** What the screen calls once the owner has picked a folder. */
    fun exportSetTo(sink: BackupSetSink) = once {
        exportSet(sink).fold({ "Exported as $it" }, ::exportReason)
    }

    fun restoreDataFrom(io: BackupIO) = once {
        restoreData(io).fold(::restoredLine, ::reason)
    }

    fun restoreFilesFrom(io: BackupIO) = once {
        restoreFiles(io).fold(::restoredFilesLine, ::filesReason)
    }

    /**
     * Runs [block] in `viewModelScope`, not in the composition's scope: a rotation halfway through
     * an export must not abandon a half-written document. The guard is set before the first
     * suspension, so two taps inside one frame start one operation, not two.
     */
    private fun once(block: suspend () -> String) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            val message = block()
            _state.update { it.copy(busy = false) }
            _messages.tryEmit(message)
        }
    }

    /**
     * The counts, and — when the file listed attachments — what is still to do. A data-only
     * restore leaves every attachment row saying it is not on this device; that is a first-class
     * outcome rather than an error, so it is said plainly instead of being hidden.
     */
    private fun restoredLine(report: ImportReport): String =
        "Imported: ${report.assets} assets, ${report.tags} tags, ${report.links} links" +
            if (report.attachments > 0) {
                " · ${report.attachments} attachments listed; " +
                    "restore the files archive to get their contents"
            } else {
                ""
            }

    /**
     * Restored and skipped, and then the archive's own damage if it had any: entries the manifest
     * promised and the file did not carry (those rows are still without bytes), and bytes the
     * manifest never named. Reporting only the first two numbers would call a damaged archive a
     * clean restore.
     */
    private fun restoredFilesLine(report: ArtifactsReport): String = buildString {
        append("Restored ${report.restored} files, skipped ${report.skipped}")
        if (report.missingEntries.isNotEmpty()) {
            append("; ${report.missingEntries.size} listed files were not in the archive")
        }
        if (report.unexpectedEntries.isNotEmpty()) {
            append("; ${report.unexpectedEntries.size} files in the archive were not listed")
        }
    }

    private fun exportReason(error: Throwable): String = when (error) {
        is BackupSetIncomplete -> "Backup not saved: " + error.wording()
        is ArtifactsWriteFailed ->
            "Export failed: the files archive could not be written. Nothing was saved."
        else -> "Export failed: ${reason(error)}. Nothing was saved."
    }

    private fun filesReason(error: Throwable): String = when (error) {
        is ArtifactsSetMismatch ->
            "Those files belong to backup set ${error.found.take(SHORT_ID)}, " +
                "not ${error.expected.take(SHORT_ID)}"
        is StoreIoException -> "Choose an attachment folder in Settings first"
        else -> reason(error)
    }

    private fun reason(error: Throwable): String = error.message ?: error.javaClass.simpleName

    private companion object {
        /** Enough of a set id to tell two apart by eye, and short enough to read in a snackbar. */
        const val SHORT_ID = 8
    }
}

/**
 * "1 attachment file is missing", "2 attachment files have changed since they were added".
 *
 * The fallback is for the count-and-total half of `covers`: a write can fall short of the plan
 * with both lists empty, and "Backup not saved: " with nothing after it is not a sentence.
 */
private fun BackupSetIncomplete.wording(): String = listOfNotNull(
    missing.size.takeIf { it > 0 }?.let { n ->
        "$n attachment ${if (n == 1) "file is" else "files are"} missing"
    },
    mismatched.size.takeIf { it > 0 }?.let { n ->
        "$n attachment ${if (n == 1) "file has" else "files have"} changed since " +
            if (n == 1) "it was added" else "they were added"
    },
).joinToString(" and ").ifEmpty { "the file archive did not cover every attachment" }

/**
 * `runCatching` catches everything, including the cancellation a cleared ViewModel throws at its
 * own coroutines. Being cancelled is not a backup that failed — nobody should be told "export
 * failed: Job was cancelled" — so it goes back out the way it came.
 */
private fun <T> Result<T>.rethrowCancellation(): Result<T> =
    onFailure { if (it is CancellationException) throw it }
