package com.loosecannon.notenfc.backup

import android.content.ContentResolver
import android.net.Uri
import com.loosecannon.notenfc.core.ports.BackupIO
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The Storage Access Framework side of [BackupIO]: one document the user picked, read on
 * [Dispatchers.IO]. A data archive is small enough that holding it in one [ByteArray] is the
 * simplest thing that is also atomic.
 *
 * [openStream] is the exception, and the reason the port has it: an artifacts archive is read
 * entry by entry and must never be held whole.
 */
class SafBackupIO(
    private val resolver: ContentResolver,
    private val uri: Uri,
) : BackupIO {

    override suspend fun read(): ByteArray = withContext(Dispatchers.IO) {
        resolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("cannot open $uri for reading")
    }

    override suspend fun openStream(): InputStream = withContext(Dispatchers.IO) {
        resolver.openInputStream(uri) ?: error("cannot open $uri for reading")
    }
}
