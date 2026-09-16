package com.loosecannon.notenfc.attachments

import android.content.ContentResolver
import androidx.documentfile.provider.DocumentFile
import com.loosecannon.notenfc.core.model.MimeTypes
import com.loosecannon.notenfc.core.ports.AttachmentStore
import com.loosecannon.notenfc.core.ports.ByteSource
import com.loosecannon.notenfc.core.ports.StoreIoException
import com.loosecannon.notenfc.core.ports.StoredBytes
import java.io.IOException
import java.io.InputStream
import java.security.DigestInputStream
import java.security.MessageDigest
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The managed store: ordinary documents in a folder the owner picked (spec §5.2). Directories are
 * created on demand by walking the locator's segments with `findFile` then `createDirectory`.
 *
 * Two provider quirks shape the lookup. Some providers append their own extension when the mime
 * type and the requested name disagree, and some normalise the name; so the store records nothing
 * about the created document's name, keeps the locator it asked for, and resolves a locator by
 * `findFile` per segment, **falling back to the first child whose name starts with `<id>.`**. The
 * attachment id is unique, so that match is unambiguous.
 *
 * `put` hashes while it streams (64 KiB buffer) and is the only place an attachment's sha256 is
 * ever computed (spec §11.10); on any failure it deletes the partial document, so a half-written
 * file never survives to back a row.
 */
class SafTreeAttachmentStore(
    private val tree: DocumentFile,
    private val resolver: ContentResolver,
) : AttachmentStore {

    override suspend fun put(locator: String, source: ByteSource): StoredBytes =
        withContext(Dispatchers.IO) {
            val segments = locator.split('/')
            val directory = directoryFor(segments.dropLast(1))
            val fileName = segments.last()
            // A stale document at the same locator is replaced, not appended to — and a provider
            // that refuses the delete must not end up with two documents for one locator.
            documentIn(directory, fileName)?.let { stale ->
                if (!stale.delete()) throw StoreIoException("cannot replace $locator")
            }
            val document = directory.createFile(mimeFor(fileName), fileName)
                ?: throw StoreIoException("cannot create $locator in ${tree.uri.authority}")
            try {
                val digest = MessageDigest.getInstance("SHA-256")
                var size = 0L
                resolver.openOutputStream(document.uri, "wt")?.use { out ->
                    DigestInputStream(source.open(), digest).use { input ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            out.write(buffer, 0, read)
                            size += read
                        }
                    }
                } ?: throw StoreIoException("cannot open $locator for writing")
                StoredBytes(
                    sha256 = digest.digest().joinToString("") { b -> "%02x".format(b) },
                    sizeBytes = size,
                )
            } catch (e: IOException) {
                // A broken destination, and ours to name: the partial document goes, and the
                // failure leaves as the store's own type.
                deleteQuietly(document)
                throw if (e is StoreIoException) e else StoreIoException("writing $locator failed", e)
            } catch (e: Throwable) {
                // Deliberately *not* translated. The source being copied here can be an entry of
                // an artifacts archive, and `ArtifactsCodec` raises `BackupCorrupt` — not an
                // `IOException` — from inside the read; calling that a store failure would hide
                // the damage the restore has to report. The partial document is still ours.
                deleteQuietly(document)
                throw e
            }
        }

    override suspend fun open(locator: String): InputStream? = withContext(Dispatchers.IO) {
        documentFor(locator)?.let { resolver.openInputStream(it.uri) }
    }

    override suspend fun exists(locator: String): Boolean =
        withContext(Dispatchers.IO) { documentFor(locator) != null }

    override suspend fun delete(locator: String) {
        withContext(Dispatchers.IO) { documentFor(locator)?.delete() }
    }

    /** The document at [locator], by exact name then by the `<id>.` prefix. App layer only. */
    fun documentFor(locator: String): DocumentFile? {
        val segments = locator.split('/')
        var directory: DocumentFile = tree
        segments.dropLast(1).forEach { segment ->
            directory = directory.findFile(segment)?.takeIf { it.isDirectory } ?: return null
        }
        return documentIn(directory, segments.last())
    }

    private fun documentIn(directory: DocumentFile, fileName: String): DocumentFile? =
        directory.findFile(fileName)
            ?: directory.listFiles().firstOrNull { child ->
                child.isFile && child.name?.startsWith(fileName.substringBefore('.') + ".") == true
            }

    private fun directoryFor(segments: List<String>): DocumentFile {
        var directory: DocumentFile = tree
        segments.forEach { segment ->
            directory = directory.findFile(segment)?.takeIf { it.isDirectory }
                ?: directory.createDirectory(segment)
                ?: throw StoreIoException("cannot create directory $segment")
        }
        return directory
    }

    /**
     * Cleanup must not replace the failure that caused it, whatever the provider does here — this
     * is a delete, not a copy loop, so the narrow-catch rule does not apply. Cancellation still
     * travels: it is not a provider failure.
     */
    private fun deleteQuietly(document: DocumentFile) {
        try {
            document.delete()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // nothing left to do: an orphan for 4B's sweep, not a second exception
        }
    }

    /** The provider wants a mime type to create a file; the extension is all we have here. */
    private fun mimeFor(fileName: String): String =
        MimeTypes.mimeForExtension(fileName.substringAfterLast('.', ""))
}
