package com.loosecannon.servicetag.core.usecase

import com.loosecannon.servicetag.core.backup.BackupCodec
import com.loosecannon.servicetag.core.backup.toDomain
import com.loosecannon.servicetag.core.model.AssetTree
import com.loosecannon.servicetag.core.model.DefinitionKind
import com.loosecannon.servicetag.core.ports.AssetRepository
import com.loosecannon.servicetag.core.ports.AttachmentRepository
import com.loosecannon.servicetag.core.ports.AttachmentStorage
import com.loosecannon.servicetag.core.ports.DefinitionRepository
import com.loosecannon.servicetag.core.ports.EventRepository
import com.loosecannon.servicetag.core.ports.LinkRepository
import com.loosecannon.servicetag.core.ports.ProfileRepository
import com.loosecannon.servicetag.core.ports.TagRepository
import com.loosecannon.servicetag.core.ports.UnitOfWork

data class ImportReport(
    val formatVersion: Int,
    val assets: Int,
    val tags: Int,
    val links: Int,
    val definitions: Int,
    val profiles: Int,
    val events: Int,
    val attachments: Int,
    /** What a later `RestoreArtifacts` must match; `""` for a format ≤4 file. */
    val lastRestoredBackupSetId: String,
)

/**
 * Replace import: wipe and load in one transaction. A decode failure, a newer format, or a failed
 * insert leaves the previous data exactly as it was — the decode happens before the transaction
 * opens, and everything after it rolls back together.
 *
 * The bytes of the attachment rows this replaces are swept *after* the commit, best effort: a
 * store that will not co-operate leaves an orphaned file for 4B, never a half-undone import.
 */
class ImportBackupReplace(
    private val assets: AssetRepository,
    private val tags: TagRepository,
    private val links: LinkRepository,
    private val definitions: DefinitionRepository,
    private val profiles: ProfileRepository,
    private val events: EventRepository,
    private val attachments: AttachmentRepository,
    private val storage: AttachmentStorage,
    private val uow: UnitOfWork,
) {
    suspend fun run(bytes: ByteArray): ImportReport {
        val backup = BackupCodec.decode(bytes) // outside the transaction: refuse before touching data
        val data = backup.data

        val orphaned = uow.write {
            // The bytes of everything about to be replaced, read before the wipe.
            val doomed = attachments.all().map { it.storageLocator }

            // delete in the order that clears references before the rows they point at
            attachments.deleteAll()
            events.deleteAll()
            profiles.deleteAll()
            definitions.deleteAll()
            tags.deleteAll()
            links.deleteAll()
            assets.deleteAll()

            // insert in reference order so foreign keys are satisfied at every step. Assets go
            // in parents-first order (AssetTree.parentsFirst) regardless of the file's own list
            // order, so a self-referencing parent_asset_id FK resolves at insert time even for a
            // shuffled file. Within measurementDefinitions, ENTERED rows go first and DERIVED
            // rows after, so a DERIVED definition's source_a_id/source_b_id foreign keys
            // (schema v3) resolve at insert time regardless of the file's own id ordering.
            AssetTree.parentsFirst(data.assets.map { it.toDomain() }).forEach { assets.upsert(it) }
            val (entered, derived) = data.measurementDefinitions.partition { it.kind == DefinitionKind.ENTERED.name }
            entered.forEach { definitions.upsert(it.toDomain()) }
            derived.forEach { definitions.upsert(it.toDomain()) }
            data.eventProfiles.forEach { profiles.upsert(it.toDomain()) }
            data.externalLinks.forEach { links.upsert(it.toDomain()) }
            data.nfcTags.forEach { tags.upsert(it.toDomain()) }
            data.assetEvents.forEach { events.upsert(it.toDomain()) }
            // Attachment rows go last: every owner, asset or event, is already in.
            data.attachments.forEach { attachments.upsert(it.toDomain()) }

            doomed - data.attachments.map { it.storageLocator }.toSet()
        }

        // After the commit, best effort: a file the store will not delete is an orphan for 4B.
        storage.sweepBytes(orphaned)

        return ImportReport(
            formatVersion = backup.manifest.formatVersion,
            assets = data.assets.size,
            tags = data.nfcTags.size,
            links = data.externalLinks.size,
            definitions = data.measurementDefinitions.size,
            profiles = data.eventProfiles.size,
            events = data.assetEvents.size,
            attachments = data.attachments.size,
            lastRestoredBackupSetId = backup.manifest.backupSetId,
        )
    }
}
