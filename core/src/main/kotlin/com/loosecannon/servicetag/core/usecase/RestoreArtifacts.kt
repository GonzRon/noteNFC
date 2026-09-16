package com.loosecannon.servicetag.core.usecase

import com.loosecannon.servicetag.core.backup.ArtifactsCodec
import com.loosecannon.servicetag.core.backup.ArtifactsSetMismatch
import com.loosecannon.servicetag.core.model.AttachmentId
import com.loosecannon.servicetag.core.ports.AttachmentRepository
import com.loosecannon.servicetag.core.ports.AttachmentStorage
import com.loosecannon.servicetag.core.ports.ByteSource
import com.loosecannon.servicetag.core.ports.StoreIoException
import java.io.InputStream

/**
 * Restored, skipped, already present, and the ids the archive carried that this install has no
 * row for.
 *
 * `missingEntries`/`unexpectedEntries` are the archive's own damage, as the reader saw it: bytes
 * the manifest promised and did not carry, and bytes the manifest never named.
 *
 * `alreadyPresent` is the restore-over-a-working-install case: the row's bytes are on this device
 * and hash to what the row claims, so the archive's copy of them is not written at all.
 */
data class ArtifactsReport(
    val backupSetId: String,
    val restored: Int,
    val skipped: Int,
    val missingRows: List<String>,
    val missingEntries: List<String> = emptyList(),
    val unexpectedEntries: List<String> = emptyList(),
    val alreadyPresent: Int = 0,
)

/**
 * The second half of a restore. It writes bytes only, never rows: the rows came from the data
 * archive, and an entry that no row claims is skipped rather than invented (spec §6).
 *
 * **It never overwrites bytes that are already right.** A store's `put` replaces the document —
 * `SafTreeAttachmentStore` deletes the old one before it creates the new one — so writing a
 * damaged entry over good local bytes would destroy the only good copy, on a screen that promises
 * the restore "adds files and deletes nothing". So a row whose bytes are on this device and hash
 * to what the row claims is counted as already present and left alone, which also makes the whole
 * operation idempotent: restoring the same archive twice writes nothing the second time.
 *
 * Verification happens twice for the entries it does write, on purpose. The manifest's sha256 is
 * checked against the row before anything is opened, and the store's own digest — `put` hashes
 * while it copies — is checked against the row afterwards; a mismatch deletes what was written,
 * which by then can only ever be bytes this restore put there itself.
 */
class RestoreArtifacts(
    private val attachments: AttachmentRepository,
    private val storage: AttachmentStorage,
) {
    suspend fun run(archive: InputStream, expectedSetId: String?): ArtifactsReport {
        val store = storage.store()
            ?: throw StoreIoException("no attachment folder is configured")
        var setId = ""
        var restored = 0
        var skipped = 0
        var alreadyPresent = 0
        val missingRows = mutableListOf<String>()

        val read = ArtifactsCodec.read(
            source = archive,
            onManifest = { manifest ->
                if (expectedSetId != null && manifest.backupSetId != expectedSetId) {
                    throw ArtifactsSetMismatch(expectedSetId, manifest.backupSetId)
                }
                setId = manifest.backupSetId
            },
            onEntry = { entry, bytes ->
                val row = attachments.get(AttachmentId(entry.attachmentId))
                when {
                    row == null -> {
                        skipped += 1
                        missingRows += entry.attachmentId
                    }
                    row.sha256 != entry.sha256 -> skipped += 1
                    // The bytes are here and they are the right bytes. Opening the entry's stream
                    // is what a `put` would do; not doing it is what keeps them.
                    store.sha256Of(row.storageLocator) == row.sha256 -> alreadyPresent += 1
                    else -> {
                        // A put that dies mid-copy has already written something; those bytes are
                        // nobody's, and the row above them claims they are good.
                        val stored = try {
                            store.put(row.storageLocator, ByteSource { bytes })
                        } catch (t: Throwable) {
                            store.deleteBestEffort(row.storageLocator)
                            throw t
                        }
                        if (stored.sha256 != row.sha256) {
                            // Best effort: a store that will not delete leaves an orphan for 4B,
                            // not a reason to abandon the entries after this one.
                            store.deleteBestEffort(row.storageLocator)
                            skipped += 1
                        } else {
                            restored += 1
                        }
                    }
                }
            },
        )
        return ArtifactsReport(
            backupSetId = setId,
            restored = restored,
            skipped = skipped,
            missingRows = missingRows,
            missingEntries = read.missingEntries,
            unexpectedEntries = read.unexpectedEntries,
            alreadyPresent = alreadyPresent,
        )
    }
}
