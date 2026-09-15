package com.loosecannon.notenfc.core.usecase

import com.loosecannon.notenfc.core.backup.BackupCodec
import com.loosecannon.notenfc.core.backup.toDomain
import com.loosecannon.notenfc.core.ports.AssetRepository
import com.loosecannon.notenfc.core.ports.DefinitionRepository
import com.loosecannon.notenfc.core.ports.EventRepository
import com.loosecannon.notenfc.core.ports.LinkRepository
import com.loosecannon.notenfc.core.ports.ProfileRepository
import com.loosecannon.notenfc.core.ports.TagRepository
import com.loosecannon.notenfc.core.ports.UnitOfWork

data class ImportReport(
    val formatVersion: Int,
    val assets: Int,
    val tags: Int,
    val links: Int,
    val definitions: Int,
    val profiles: Int,
    val events: Int,
)

/**
 * Replace import: wipe and load in one transaction. A decode failure, a newer format, or a failed
 * insert leaves the previous data exactly as it was — the decode happens before the transaction
 * opens, and everything after it rolls back together.
 */
class ImportBackupReplace(
    private val assets: AssetRepository,
    private val tags: TagRepository,
    private val links: LinkRepository,
    private val definitions: DefinitionRepository,
    private val profiles: ProfileRepository,
    private val events: EventRepository,
    private val uow: UnitOfWork,
) {
    suspend fun run(bytes: ByteArray): ImportReport {
        val backup = BackupCodec.decode(bytes) // outside the transaction: refuse before touching data
        val data = backup.data

        uow.write {
            // delete in the order that clears references before the rows they point at
            events.deleteAll()
            profiles.deleteAll()
            definitions.deleteAll()
            tags.deleteAll()
            links.deleteAll()
            assets.deleteAll()

            // insert in reference order so foreign keys are satisfied at every step
            data.assets.forEach { assets.upsert(it.toDomain()) }
            data.measurementDefinitions.forEach { definitions.upsert(it.toDomain()) }
            data.eventProfiles.forEach { profiles.upsert(it.toDomain()) }
            data.externalLinks.forEach { links.upsert(it.toDomain()) }
            data.nfcTags.forEach { tags.upsert(it.toDomain()) }
            data.assetEvents.forEach { events.upsert(it.toDomain()) }
        }

        return ImportReport(
            formatVersion = backup.manifest.formatVersion,
            assets = data.assets.size,
            tags = data.nfcTags.size,
            links = data.externalLinks.size,
            definitions = data.measurementDefinitions.size,
            profiles = data.eventProfiles.size,
            events = data.assetEvents.size,
        )
    }
}
