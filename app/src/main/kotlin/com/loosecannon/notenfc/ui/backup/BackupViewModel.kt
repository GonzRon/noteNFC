package com.loosecannon.notenfc.ui.backup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loosecannon.notenfc.core.ports.BackupIO
import com.loosecannon.notenfc.core.ports.Clock
import com.loosecannon.notenfc.core.usecase.ExportBackup
import com.loosecannon.notenfc.core.usecase.ImportBackupReplace
import com.loosecannon.notenfc.core.usecase.ImportReport
import com.loosecannon.notenfc.di.AppGraph
import com.loosecannon.notenfc.prefs.AppPrefs
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** When the last export landed, and whether one is in flight. */
data class BackupState(val lastBackupAt: Long? = null, val busy: Boolean = false)

/**
 * Export and replace-import, over a [BackupIO] the caller chose — in the app a SAF document, in a
 * test a byte array. The port is a parameter rather than a `Uri` so nothing here has to know what
 * a `ContentResolver` is, and so the one rule that matters can be tested at all:
 *
 * the preferences are marked **only after** the destination has taken the bytes. A backup nudge is
 * a promise that a file exists; a failed write made no file, so the nudge stays.
 */
class BackupViewModel(
    private val exportBackup: ExportBackup,
    private val importBackupReplace: ImportBackupReplace,
    private val prefs: AppPrefs,
    private val clock: Clock,
) : ViewModel() {

    constructor(graph: AppGraph) :
        this(graph.exportBackup, graph.importBackupReplace, graph.prefs, graph.clock)

    private val _state = MutableStateFlow(BackupState(lastBackupAt = prefs.lastBackupAt))
    val state: StateFlow<BackupState> = _state.asStateFlow()

    /**
     * One line per finished operation. No replay and a buffer of one: the screen that started the
     * work is told how it went, and a screen that arrives later is not told again.
     */
    private val _messages = MutableSharedFlow<String>(replay = 0, extraBufferCapacity = 1)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    /** Writes a v1 backup to [io] and returns how many bytes it took. */
    suspend fun export(io: BackupIO): Result<Int> = runCatching {
        val bytes = exportBackup.run()
        io.write(bytes)
        // Only here: the document has the bytes.
        prefs.markBackupExported(clock.nowMillis())
        _state.update { it.copy(lastBackupAt = prefs.lastBackupAt) }
        bytes.size
    }

    /** Wipes and loads everything from [io]. The use case does it in one transaction or not at all. */
    suspend fun importReplace(io: BackupIO): Result<ImportReport> = runCatching {
        importBackupReplace.run(io.read())
    }

    /** What the screen calls once the user has picked a document. */
    fun exportTo(io: BackupIO) = once {
        export(io).fold({ "Exported $it bytes" }, ::reason)
    }

    fun importReplaceFrom(io: BackupIO) = once {
        importReplace(io).fold(
            { "Imported: ${it.assets} assets, ${it.tags} tags, ${it.links} links" },
            ::reason,
        )
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

    private fun reason(error: Throwable): String = error.message ?: error.javaClass.simpleName
}
