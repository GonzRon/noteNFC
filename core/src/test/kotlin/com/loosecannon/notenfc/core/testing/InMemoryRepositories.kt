package com.loosecannon.notenfc.core.testing

import com.loosecannon.notenfc.core.model.Asset
import com.loosecannon.notenfc.core.model.AssetId
import com.loosecannon.notenfc.core.model.ExternalLink
import com.loosecannon.notenfc.core.model.LinkId
import com.loosecannon.notenfc.core.model.PayloadFormat
import com.loosecannon.notenfc.core.model.TagBinding
import com.loosecannon.notenfc.core.model.TagId
import com.loosecannon.notenfc.core.model.TagTarget
import com.loosecannon.notenfc.core.ports.AssetRepository
import com.loosecannon.notenfc.core.ports.LinkRepository
import com.loosecannon.notenfc.core.ports.TagRepository
import com.loosecannon.notenfc.core.ports.UnitOfWork

/** A fake store that can hand back a closure restoring its state at the moment of the call. */
interface Rollbackable {
    fun snapshot(): () -> Unit
}

/** Thrown by a rigged fake repository so tests can force a mid-transaction failure. */
class RiggedFailure(message: String) : RuntimeException(message)

private class UpsertRig(private val label: String) {
    var failOnUpsert: Int? = null
    private var seen = 0

    fun check() {
        seen += 1
        if (seen == failOnUpsert) throw RiggedFailure("rigged $label upsert failure at #$seen")
    }
}

class InMemoryAssetRepository : AssetRepository, Rollbackable {
    val rows = LinkedHashMap<String, Asset>()
    private val rig = UpsertRig("asset")
    var failOnUpsert: Int?
        get() = rig.failOnUpsert
        set(value) { rig.failOnUpsert = value }

    override fun snapshot(): () -> Unit {
        val copy = LinkedHashMap(rows)
        return { rows.clear(); rows.putAll(copy) }
    }

    override suspend fun upsert(asset: Asset) {
        rig.check()
        rows[asset.id.value] = asset
    }

    override suspend fun get(id: AssetId): Asset? = rows[id.value]

    override suspend fun all(): List<Asset> = rows.values.toList()

    override suspend fun delete(id: AssetId) { rows.remove(id.value) }

    override suspend fun deleteAll() { rows.clear() }
}

class InMemoryTagRepository : TagRepository, Rollbackable {
    val rows = LinkedHashMap<String, TagBinding>()
    private val rig = UpsertRig("tag")
    var failOnUpsert: Int?
        get() = rig.failOnUpsert
        set(value) { rig.failOnUpsert = value }

    override fun snapshot(): () -> Unit {
        val copy = LinkedHashMap(rows)
        return { rows.clear(); rows.putAll(copy) }
    }

    override suspend fun upsert(tag: TagBinding) {
        rig.check()
        rows[tag.id.value] = tag
    }

    override suspend fun get(id: TagId): TagBinding? = rows[id.value]

    override suspend fun findByPayload(format: PayloadFormat, key: String): TagBinding? =
        rows.values.firstOrNull { it.payloadFormat == format && it.payloadKey == key }

    override suspend fun forAsset(assetId: AssetId): List<TagBinding> =
        rows.values.filter { (it.target as? TagTarget.AssetTarget)?.assetId == assetId }

    override suspend fun forLink(linkId: LinkId): List<TagBinding> =
        rows.values.filter { (it.target as? TagTarget.LinkTarget)?.linkId == linkId }

    override suspend fun all(): List<TagBinding> = rows.values.toList()

    override suspend fun delete(id: TagId) { rows.remove(id.value) }

    override suspend fun deleteAll() { rows.clear() }
}

class InMemoryLinkRepository : LinkRepository, Rollbackable {
    val rows = LinkedHashMap<String, ExternalLink>()
    private val rig = UpsertRig("link")
    var failOnUpsert: Int?
        get() = rig.failOnUpsert
        set(value) { rig.failOnUpsert = value }

    override fun snapshot(): () -> Unit {
        val copy = LinkedHashMap(rows)
        return { rows.clear(); rows.putAll(copy) }
    }

    override suspend fun upsert(link: ExternalLink) {
        rig.check()
        rows[link.id.value] = link
    }

    override suspend fun get(id: LinkId): ExternalLink? = rows[id.value]

    override suspend fun forAsset(assetId: AssetId): List<ExternalLink> =
        rows.values.filter { it.assetId == assetId }

    override suspend fun standalone(): List<ExternalLink> = rows.values.filter { it.assetId == null }

    override suspend fun all(): List<ExternalLink> = rows.values.toList()

    override suspend fun delete(id: LinkId) { rows.remove(id.value) }

    override suspend fun deleteAll() { rows.clear() }
}

/**
 * Snapshots every store before running [block] and restores them all if it throws,
 * so rollback is observable in tests without a real database.
 */
class FakeUnitOfWork(private vararg val stores: Rollbackable) : UnitOfWork {
    var commits = 0
        private set
    var rollbacks = 0
        private set

    override suspend fun <T> write(block: suspend () -> T): T {
        val restores = stores.map { it.snapshot() }
        return try {
            val result = block()
            commits += 1
            result
        } catch (t: Throwable) {
            restores.forEach { it() }
            rollbacks += 1
            throw t
        }
    }
}
