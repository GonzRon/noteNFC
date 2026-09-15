package com.loosecannon.notenfc.ui.backup

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.loosecannon.notenfc.backup.SafBackupIO
import com.loosecannon.notenfc.di.AppGraph
import com.loosecannon.notenfc.ui.components.LabelValue
import com.loosecannon.notenfc.ui.components.QuietLine
import com.loosecannon.notenfc.ui.components.SectionHeader
import com.loosecannon.notenfc.ui.theme.ControlShape
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** The word the user has to type before an import runs (R-9). Not localised: it is a password. */
private const val REPLACE_WORD = "REPLACE"

/** What an import will accept back. Providers hand zips back as octet-stream often enough to matter. */
private val IMPORT_TYPES = arrayOf("application/zip", "application/octet-stream")

/**
 * Export a v1 backup to a Storage Access Framework document, or replace everything from one.
 *
 * Two asymmetric operations on one screen: export is filled and safe, import is outlined and asks
 * the user to type [REPLACE_WORD] first, because it deletes everything that is not in the file
 * (R-9). There is no wipe here — that is the debug harness's job, not the product's.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    graph: AppGraph,
    onBack: () -> Unit,
) {
    val model: BackupViewModel = viewModel(key = "backup") { BackupViewModel(graph) }
    val state by model.state.collectAsStateWithLifecycle()
    val resolver = LocalContext.current.contentResolver
    val snackbars = remember { SnackbarHostState() }
    var confirming by remember { mutableStateOf<Uri?>(null) }
    var typed by remember { mutableStateOf("") }

    LaunchedEffect(model) { model.messages.collect { snackbars.showSnackbar(it) } }

    val exportTo = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri -> uri?.let { model.exportTo(SafBackupIO(resolver, it)) } }

    val importFrom = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        // Picking the file is not agreeing to lose what is here: the dialog is the agreement.
        typed = ""
        confirming = uri
    }

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
            modifier = Modifier.padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LabelValue(label = "Last backup", value = lastBackupLine(state.lastBackupAt))
            QuietLine("A backup holds every asset, tag and link. It is the only copy off this phone.")

            Button(
                onClick = { exportTo.launch(backupFileName()) },
                enabled = !state.busy,
                shape = ControlShape,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Export backup")
            }

            SectionHeader(title = "Restore")
            QuietLine("Importing deletes everything on this phone first, then loads the file.")
            OutlinedButton(
                onClick = { importFrom.launch(IMPORT_TYPES) },
                enabled = !state.busy,
                shape = ControlShape,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Import (replace everything)")
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
                        model.importReplaceFrom(SafBackupIO(resolver, uri))
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

/** A name the user can find again: sorted by date wherever their file manager sorts by name. */
private fun backupFileName(): String =
    "notenfc-backup-" + SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date()) + ".zip"
