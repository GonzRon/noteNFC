package com.loosecannon.notenfc.backup

import android.content.ContentResolver
import android.net.Uri
import com.loosecannon.notenfc.core.ports.BackupIO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The Storage Access Framework side of [BackupIO]: one document the user picked, read or written
 * whole. The `"wt"` mode truncates first, so re-exporting over a shorter backup cannot leave the
 * tail of the previous one behind. Everything runs on [Dispatchers.IO]; a backup is small enough
 * that holding it in one [ByteArray] is the simplest thing that is also atomic.
 */
class SafBackupIO(
    private val resolver: ContentResolver,
    private val uri: Uri,
) : BackupIO {

    override suspend fun write(bytes: ByteArray) = withContext(Dispatchers.IO) {
        resolver.openOutputStream(uri, "wt")?.use { it.write(bytes) }
            ?: error("cannot open $uri for writing")
    }

    override suspend fun read(): ByteArray = withContext(Dispatchers.IO) {
        resolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("cannot open $uri for reading")
    }
}
