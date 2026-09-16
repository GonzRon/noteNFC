package com.loosecannon.notenfc.core.usecase

import com.loosecannon.notenfc.core.backup.ArtifactsCodec
import com.loosecannon.notenfc.core.backup.ArtifactsSetMismatch
import com.loosecannon.notenfc.core.model.AttachmentId
import com.loosecannon.notenfc.core.ports.AttachmentRepository
import com.loosecannon.notenfc.core.ports.AttachmentStorage
import com.loosecannon.notenfc.core.ports.ByteSource
import com.loosecannon.notenfc.core.ports.StoreIoException
import java.io.InputStream

/**
 * Restored, skipped, and the ids the archive carried that this install has no row for.
 *
 * `missingEntries`/`unexpectedEntries` are the archive's own damage, as the reader saw it: bytes
 * the manifest promised and did not carry, and bytes the manifest never named.
 */
data class ArtifactsReport(
    val backupSetId: String,
    val restored: Int,
    val skipped: Int,
    val missingRows: List<String>,
    val missingEntries: List<String> = emptyList(),
    val unexpectedEntries: List<String> = emptyList(),
)

/**
 * The second half of a restore. It writes bytes only, never rows: the rows came from the data
 * archive, and an entry that no row claims is skipped rather than invented (spec §6).
 *
 * Verification happens twice, on purpose. The manifest's sha256 is checked against the row before
 * anything is opened, and the store's own digest — `put` hashes while it copies — is checked
 * against the row afterwards; a mismatch deletes what was written, so a bad entry can never leave
 * partial bytes behind a row that claims they are good.
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
                    else -> {
                        val stored = store.put(row.storageLocator, ByteSource { bytes })
                        if (stored.sha256 != row.sha256) {
                            store.delete(row.storageLocator)
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
        )
    }
}
