package com.loosecannon.servicetag.core.ports

/**
 * One backup source, already chosen by the caller — in `:app` that is a SAF document. Writing a
 * backup goes through `BackupSetSink` instead: a backup is a *set* of two files in one folder, and
 * the folder is the thing the owner picks.
 *
 * A data archive is small, so [read] hands it over whole. The artifacts archive is not — it
 * carries every attachment's bytes and can be hundreds of megabytes — so it is read through
 * [openStream] and never materialised. One method added to this port rather than a second port
 * beside it: the caller has already picked the same kind of document, and only the size of what
 * is in it differs.
 */
interface BackupIO {
    suspend fun read(): ByteArray

    /** The source's bytes as a stream. The caller closes it. */
    suspend fun openStream(): java.io.InputStream
}
