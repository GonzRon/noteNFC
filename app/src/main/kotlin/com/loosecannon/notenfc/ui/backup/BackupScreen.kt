package com.loosecannon.notenfc.ui.backup

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.loosecannon.notenfc.backup.SafBackupIO
import com.loosecannon.notenfc.backup.SafBackupSetWriter
import com.loosecannon.notenfc.di.AppGraph
import com.loosecannon.notenfc.ui.components.LabelValue
import com.loosecannon.notenfc.ui.components.QuietLine
import com.loosecannon.notenfc.ui.components.SectionHeader
import com.loosecannon.notenfc.ui.theme.ControlShape
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

/** The word the user has to type before an import runs (R-9). Not localised: it is a password. */
private const val REPLACE_WORD = "REPLACE"

/** What an import will accept back. Providers hand zips back as octet-stream often enough to matter. */
private val IMPORT_TYPES = arrayOf("application/zip", "application/octet-stream")

/**
 * Export a backup *set* into a folder the owner picks, or restore one — in two steps, because a
 * set is two files (spec §7.3).
 *
 * Export is filled and safe. Restore data is outlined and asks the user to type [REPLACE_WORD]
 * first, because it deletes everything that is not in the file (R-9). Restore files is outlined
 * but has no dialog at all: it adds bytes the data archive only listed and deletes nothing, and
 * it refuses an archive belonging to a different set rather than mixing two backups together.
 * There is no wipe here — that is the debug harness's job, not the product's.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    graph: AppGraph,
    onBack: () -> Unit,
) {
    val model: BackupViewModel = viewModel(key = "backup") { BackupViewModel(graph) }
    val state by model.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resolver = context.contentResolver
    val snackbars = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var confirming by remember { mutableStateOf<Uri?>(null) }
    var typed by remember { mutableStateOf("") }

    LaunchedEffect(model) { model.messages.collect { snackbars.showSnackbar(it) } }

    // A folder for this export only: no persistable grant is taken, so nothing accumulates and
    // the destination is not remembered (spec §11.2 — 3R is where a remembered one arrives).
    val exportInto = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        val tree = uri?.let { DocumentFile.fromTreeUri(context.applicationContext, it) }
        when {
            uri == null -> Unit
            tree == null ->
                scope.launch { snackbars.showSnackbar("That folder cannot be written to") }
            else -> model.exportSetTo(SafBackupSetWriter(context.applicationContext, resolver, tree))
        }
    }

    val restoreDataFrom = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        // Picking the file is not agreeing to lose what is here: the dialog is the agreement.
        typed = ""
        confirming = uri
    }

    val restoreFilesFrom = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { model.restoreFilesFrom(SafBackupIO(resolver, it)) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Backup") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbars) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LabelValue(label = "Last backup", value = lastBackupLine(state.lastBackupAt))
            QuietLine(
                "A backup set is two files in the folder you pick: the data, and the attachment " +
                    "files beside it. It is the only copy off this phone.",
            )

            Button(
                onClick = { exportInto.launch(null) },
                enabled = !state.busy,
                shape = ControlShape,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Export backup set")
            }

            SectionHeader(title = "Restore")
            QuietLine("Restoring the data deletes everything on this phone first, then loads the file.")
            OutlinedButton(
                onClick = { restoreDataFrom.launch(IMPORT_TYPES) },
                enabled = !state.busy,
                shape = ControlShape,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Restore data")
            }

            QuietLine(
                "Then restore the files archive of the same set to put the attachment files back. " +
                    "It adds files and deletes nothing.",
            )
            OutlinedButton(
                onClick = { restoreFilesFrom.launch(IMPORT_TYPES) },
                enabled = !state.busy,
                shape = ControlShape,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Restore files")
            }
        }
    }

    confirming?.let { uri ->
        AlertDialog(
            onDismissRequest = { confirming = null },
            title = { Text("Replace everything?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Every asset, tag and link on this phone is deleted and replaced with what " +
                            "is in the file. This cannot be undone.",
                    )
                    OutlinedTextField(
                        value = typed,
                        onValueChange = { typed = it },
                        singleLine = true,
                        label = { Text("Type $REPLACE_WORD to confirm") },
                        shape = ControlShape,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirming = null
                        model.restoreDataFrom(SafBackupIO(resolver, uri))
                    },
                    enabled = typed == REPLACE_WORD,
                ) {
                    Text("Replace")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirming = null }) { Text("Cancel") }
            },
            shape = ControlShape,
        )
    }
}

/** "Never" is a fact worth stating plainly; anything else is the instant, to the minute. */
private fun lastBackupLine(at: Long?): String = at
    ?.let { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(it)) }
    ?: "Never"
