package com.loosecannon.servicetag.core.usecase

import com.loosecannon.servicetag.core.model.PayloadFormat
import com.loosecannon.servicetag.core.model.TagBinding
import com.loosecannon.servicetag.core.model.TagId
import com.loosecannon.servicetag.core.model.TagStatus
import com.loosecannon.servicetag.core.model.TagTarget
import com.loosecannon.servicetag.core.ports.AssetRepository
import com.loosecannon.servicetag.core.ports.Clock
import com.loosecannon.servicetag.core.ports.IdGenerator
import com.loosecannon.servicetag.core.ports.LinkRepository
import com.loosecannon.servicetag.core.ports.TagRepository
import com.loosecannon.servicetag.core.ports.UnitOfWork

/**
 * The row side of writing a new v1 tag: [begin] mints the identity before the write, [complete]
 * records the successful read-back, [abandon] cleans up a row whose tag was never written.
 */
class ProvisionTag(
    private val tags: TagRepository,
    private val assets: AssetRepository,
    private val links: LinkRepository,
    private val uow: UnitOfWork,
    private val ids: IdGenerator,
    private val clock: Clock,
) {
    suspend fun begin(target: TagTarget, label: String?): TagBinding = uow.write {
        requireTargetExists(target, assets, links)
        val now = clock.nowMillis()
        val id = ids.newId()
        val row = TagBinding(
            id = TagId(id),
            payloadFormat = PayloadFormat.V1,
            payloadKey = id,
            target = target,
            status = if (target == TagTarget.None) TagStatus.UNBOUND else TagStatus.ACTIVE,
            label = label?.trim()?.takeIf { it.isNotEmpty() },
            createdAt = now,
            updatedAt = now,
        )
        tags.upsert(row)
        row
    }

    suspend fun complete(id: TagId, physicalUid: String?): TagBinding = uow.write {
        val row = tags.get(id) ?: throw IllegalStateException("no provisioned tag ${id.value}")
        val now = clock.nowMillis()
        val done = row.copy(physicalUid = physicalUid ?: row.physicalUid, writtenAt = now, updatedAt = now)
        tags.upsert(done)
        done
    }

    suspend fun abandon(id: TagId) {
        uow.write {
            val row = tags.get(id)
            if (row != null && row.writtenAt == null) tags.delete(id)
        }
    }
}
