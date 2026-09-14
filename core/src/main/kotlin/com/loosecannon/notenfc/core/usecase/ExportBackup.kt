package com.loosecannon.notenfc.core.usecase

import com.loosecannon.notenfc.core.backup.BackupCodec
import com.loosecannon.notenfc.core.backup.BackupData
import com.loosecannon.notenfc.core.backup.toDto
import com.loosecannon.notenfc.core.ports.AssetRepository
import com.loosecannon.notenfc.core.ports.Clock
import com.loosecannon.notenfc.core.ports.LinkRepository
import com.loosecannon.notenfc.core.ports.TagRepository
import com.loosecannon.notenfc.core.ports.UnitOfWork

/**
 * Reads every canonical table and returns the bytes of a v1 backup. Writing them is the caller's job.
 *
 * All three table reads happen in one read transaction, so the file is a single consistent point
 * in time: a write landing mid-export cannot leave a tag in the archive whose asset is not.
 */
class ExportBackup(
    private val assets: AssetRepository,
    private val tags: TagRepository,
    private val links: LinkRepository,
    private val uow: UnitOfWork,
    private val clock: Clock,
    private val appVersion: String,
    private val schemaVersion: Int,
) {
    suspend fun run(): ByteArray {
        val data = uow.read {
            BackupData(
                assets = assets.all().map { it.toDto() },
                nfcTags = tags.all().map { it.toDto() },
                externalLinks = links.all().map { it.toDto() },
            )
        }
        return BackupCodec.encode(
            data = data,
            appVersion = appVersion,
            schemaVersion = schemaVersion,
            createdAt = clock.nowMillis(),
        )
    }
}
