package com.loosecannon.notenfc.data.room

import androidx.room3.withWriteTransaction
import com.loosecannon.notenfc.core.model.Asset
import com.loosecannon.notenfc.core.model.AssetId
import com.loosecannon.notenfc.core.model.ExternalLink
import com.loosecannon.notenfc.core.model.LinkId
import com.loosecannon.notenfc.core.model.PayloadFormat
import com.loosecannon.notenfc.core.model.TagBinding
import com.loosecannon.notenfc.core.model.TagId
import com.loosecannon.notenfc.core.ports.AssetRepository
import com.loosecannon.notenfc.core.ports.LinkRepository
import com.loosecannon.notenfc.core.ports.TagRepository
import com.loosecannon.notenfc.core.ports.UnitOfWork
import com.loosecannon.notenfc.data.room.dao.AssetDao
import com.loosecannon.notenfc.data.room.dao.ExternalLinkDao
import com.loosecannon.notenfc.data.room.dao.NfcTagDao
import com.loosecannon.notenfc.data.room.entities.NfcTagEntity

/**
 * D4 §3 requires `CHECK(NOT (asset_id IS NOT NULL AND link_id IS NOT NULL))`. Room has no CHECK
 * annotation, so the rule is enforced here, on the way into the table. The domain's `TagTarget`
 * already makes both-at-once unrepresentable; this guards rows built any other way.
 */
fun NfcTagEntity.requireAtMostOneTarget(): NfcTagEntity = apply {
    require(assetId == null || linkId == null) {
        "nfc_tag '$id' carries both asset_id ('$assetId') and link_id ('$linkId'); at most one target is allowed"
    }
}

class RoomAssetRepository(private val dao: AssetDao) : AssetRepository {
    override suspend fun upsert(asset: Asset) = dao.upsert(asset.toEntity())
    override suspend fun get(id: AssetId): Asset? = dao.byId(id.value)?.toDomain()
    override suspend fun all(): List<Asset> = dao.all().map { it.toDomain() }
    override suspend fun delete(id: AssetId) = dao.delete(id.value)
    override suspend fun deleteAll() = dao.deleteAll()
}

class RoomTagRepository(private val dao: NfcTagDao) : TagRepository {
    override suspend fun upsert(tag: TagBinding) = dao.upsert(tag.toEntity().requireAtMostOneTarget())
    override suspend fun get(id: TagId): TagBinding? = dao.byId(id.value)?.toDomain()

    override suspend fun findByPayload(format: PayloadFormat, key: String): TagBinding? =
        dao.byPayload(format.name, key)?.toDomain()

    override suspend fun forAsset(assetId: AssetId): List<TagBinding> =
        dao.forAsset(assetId.value).map { it.toDomain() }

    override suspend fun forLink(linkId: LinkId): List<TagBinding> =
        dao.forLink(linkId.value).map { it.toDomain() }

    override suspend fun all(): List<TagBinding> = dao.all().map { it.toDomain() }
    override suspend fun delete(id: TagId) = dao.delete(id.value)
    override suspend fun deleteAll() = dao.deleteAll()
}

class RoomLinkRepository(private val dao: ExternalLinkDao) : LinkRepository {
    override suspend fun upsert(link: ExternalLink) = dao.upsert(link.toEntity())
    override suspend fun get(id: LinkId): ExternalLink? = dao.byId(id.value)?.toDomain()

    override suspend fun forAsset(assetId: AssetId): List<ExternalLink> =
        dao.forAsset(assetId.value).map { it.toDomain() }

    override suspend fun standalone(): List<ExternalLink> = dao.standalone().map { it.toDomain() }
    override suspend fun all(): List<ExternalLink> = dao.all().map { it.toDomain() }
    override suspend fun delete(id: LinkId) = dao.delete(id.value)
    override suspend fun deleteAll() = dao.deleteAll()
}

class RoomUnitOfWork(private val db: AppDatabase) : UnitOfWork {
    override suspend fun <T> write(block: suspend () -> T): T = db.withWriteTransaction { block() }
}
