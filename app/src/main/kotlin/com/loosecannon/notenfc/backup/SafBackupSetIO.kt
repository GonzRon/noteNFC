package com.loosecannon.notenfc.backup

import android.content.ContentResolver
import android.content.Context
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.loosecannon.notenfc.ui.backup.BackupSetSink
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A folder the owner picked for *this* export only — no persisted grant here (spec §11.2; 3R turns
 * it into a remembered destination). Two files go in, both stamped, and the data file is removed
 * again if the artifacts file cannot be written: half a set is worse than none, because a restore
 * would look possible and then not be.
 */
class SafBackupSetWriter(
    private val context: Context,
    private val resolver: ContentResolver,
    private val tree: DocumentFile,          // the screen passes DocumentFile.fromTreeUri(...)
) : BackupSetSink {

    /**
     * Creates the document, streams [body] into it, and — the invariant the owner asked for —
     * deletes that document again if [body] throws, then rethrows. A failed export must leave
     * no file from that attempted set, and the caller only knows the handles of writes that
     * returned.
     */
    override suspend fun write(name: String, body: suspend (OutputStream) -> Unit): String =
        withContext(Dispatchers.IO) {
            val document = tree.createFile("application/zip", name)
                ?: error("cannot create $name in the chosen folder")
            try {
                resolver.openOutputStream(document.uri, "wt")?.use { body(it) }
                    ?: error("cannot open $name for writing")
            } catch (t: Throwable) {
                runCatching { document.delete() }
                throw t
            }
            document.uri.toString()
        }

    override suspend fun delete(handle: String) {
        withContext(Dispatchers.IO) {
            runCatching {
                DocumentFile.fromSingleUri(context.applicationContext, handle.toUri())?.delete()
            }
        }
    }
}

/** `noteNFC-data-<stamp>.zip` and `noteNFC-artifacts-<stamp>.zip`, stamp = local `yyyyMMdd-HHmmss`. */
object BackupSetNames {
    fun stamp(at: Long): String =
        SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date(at))

    fun data(stamp: String): String = "noteNFC-data-$stamp.zip"

    fun artifacts(stamp: String): String = "noteNFC-artifacts-$stamp.zip"
}
