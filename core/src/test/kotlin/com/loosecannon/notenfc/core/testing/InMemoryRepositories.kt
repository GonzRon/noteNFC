package com.loosecannon.notenfc.core.testing

import com.loosecannon.notenfc.core.journal.EventChronology
import com.loosecannon.notenfc.core.model.Asset
import com.loosecannon.notenfc.core.model.AssetEvent
import com.loosecannon.notenfc.core.model.AssetId
import com.loosecannon.notenfc.core.model.AssetStatus
import com.loosecannon.notenfc.core.model.Attachment
import com.loosecannon.notenfc.core.model.AttachmentId
import com.loosecannon.notenfc.core.model.AttachmentOwner
import com.loosecannon.notenfc.core.model.DefinitionId
import com.loosecannon.notenfc.core.model.EventId
import com.loosecannon.notenfc.core.model.EventProfile
import com.loosecannon.notenfc.core.model.ExternalLink
import com.loosecannon.notenfc.core.model.LinkId
import com.loosecannon.notenfc.core.model.MeasurementDefinition
import com.loosecannon.notenfc.core.model.PayloadFormat
import com.loosecannon.notenfc.core.model.ProfileId
import com.loosecannon.notenfc.core.model.TagBinding
import com.loosecannon.notenfc.core.model.TagId
import com.loosecannon.notenfc.core.model.TagTarget
import com.loosecannon.notenfc.core.ports.AssetRepository
import com.loosecannon.notenfc.core.ports.AttachmentRepository
import com.loosecannon.notenfc.core.ports.DefinitionRepository
import com.loosecannon.notenfc.core.ports.EventRepository
import com.loosecannon.notenfc.core.ports.LinkRepository
import com.loosecannon.notenfc.core.ports.ProfileRepository
import com.loosecannon.notenfc.core.ports.TagRepository
import com.loosecannon.notenfc.core.ports.UnitOfWork
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** A fake store that can hand back a closure restoring its state at the moment of the call. */
interface Rollbackable {
    fun snapshot(): () -> Unit
}

/**
 * Bookkeeping shared by a [FakeUnitOfWork] and the stores it covers, so a test can ask which
 * transaction — if any — a table read happened in.
 */
class TransactionWitness {
    var inRead = false
    var inWrite = false

    /** Table reads that ran in no transaction at all, i.e. outside any snapshot. */
    var readsOutsideSnapshot = 0
        private set

    fun observeAll() {
        if (!inRead && !inWrite) readsOutsideSnapshot += 1
    }
}

/** A fake store that reports its `all()` calls to the witness a [FakeUnitOfWork] hands it. */
interface Witnessed {
    var witness: TransactionWitness?
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

/** Open so a test can subclass it to rig a check on upsert order (e.g. FK-like checks). */
open class InMemoryAssetRepository : AssetRepository, Rollbackable, Witnessed {
    val rows = LinkedHashMap<String, Asset>()
    override var witness: TransactionWitness? = null
    private val rig = UpsertRig("asset")
    private val version = MutableStateFlow(0)
    var failOnUpsert: Int?
        get() = rig.failOnUpsert
        set(value) { rig.failOnUpsert = value }

    override fun snapshot(): () -> Unit {
        val copy = LinkedHashMap(rows)
        return { rows.clear(); rows.putAll(copy); version.value += 1 }
    }

    override suspend fun upsert(asset: Asset) {
        rig.check()
        rows[asset.id.value] = asset
        version.value += 1
    }

    override suspend fun get(id: AssetId): Asset? = rows[id.value]

    override suspend fun all(): List<Asset> {
        witness?.observeAll()
        return rows.values.toList()
    }

    override suspend fun delete(id: AssetId) { rows.remove(id.value); version.value += 1 }

    override suspend fun deleteAll() { rows.clear(); version.value += 1 }

    override fun observeAll(): Flow<List<Asset>> = version.map {
        rows.values.sortedWith(
            compareBy({ if (it.status == AssetStatus.ACTIVE) 0 else 1 }, { it.name.lowercase() }),
        )
    }
}

class InMemoryTagRepository : TagRepository, Rollbackable, Witnessed {
    val rows = LinkedHashMap<String, TagBinding>()
    override var witness: TransactionWitness? = null
    private val rig = UpsertRig("tag")
    private val version = MutableStateFlow(0)
    var failOnUpsert: Int?
        get() = rig.failOnUpsert
        set(value) { rig.failOnUpsert = value }

    override fun snapshot(): () -> Unit {
        val copy = LinkedHashMap(rows)
        return { rows.clear(); rows.putAll(copy); version.value += 1 }
    }

    override suspend fun upsert(tag: TagBinding) {
        rig.check()
        rows[tag.id.value] = tag
        version.value += 1
    }

    override suspend fun get(id: TagId): TagBinding? = rows[id.value]

    override suspend fun findByPayload(format: PayloadFormat, key: String): TagBinding? =
        rows.values.firstOrNull { it.payloadFormat == format && it.payloadKey == key }

    override suspend fun forAsset(assetId: AssetId): List<TagBinding> =
        rows.values.filter { (it.target as? TagTarget.AssetTarget)?.assetId == assetId }

    override suspend fun forLink(linkId: LinkId): List<TagBinding> =
        rows.values.filter { (it.target as? TagTarget.LinkTarget)?.linkId == linkId }

    override suspend fun all(): List<TagBinding> {
        witness?.observeAll()
        return rows.values.toList()
    }

    override suspend fun delete(id: TagId) { rows.remove(id.value); version.value += 1 }

    override suspend fun deleteAll() { rows.clear(); version.value += 1 }

    override fun observeForAsset(assetId: AssetId): Flow<List<TagBinding>> = version.map {
        rows.values.filter { (it.target as? TagTarget.AssetTarget)?.assetId == assetId }
    }

    override fun observeForLink(linkId: LinkId): Flow<List<TagBinding>> = version.map {
        rows.values.filter { (it.target as? TagTarget.LinkTarget)?.linkId == linkId }
    }
}

class InMemoryLinkRepository : LinkRepository, Rollbackable, Witnessed {
    val rows = LinkedHashMap<String, ExternalLink>()
    override var witness: TransactionWitness? = null
    private val rig = UpsertRig("link")
    private val version = MutableStateFlow(0)
    var failOnUpsert: Int?
        get() = rig.failOnUpsert
        set(value) { rig.failOnUpsert = value }

    override fun snapshot(): () -> Unit {
        val copy = LinkedHashMap(rows)
        return { rows.clear(); rows.putAll(copy); version.value += 1 }
    }

    override suspend fun upsert(link: ExternalLink) {
        rig.check()
        rows[link.id.value] = link
        version.value += 1
    }

    override suspend fun get(id: LinkId): ExternalLink? = rows[id.value]

    override suspend fun forAsset(assetId: AssetId): List<ExternalLink> =
        rows.values.filter { it.assetId == assetId }

    override suspend fun standalone(): List<ExternalLink> = rows.values.filter { it.assetId == null }

    override suspend fun all(): List<ExternalLink> {
        witness?.observeAll()
        return rows.values.toList()
    }

    override suspend fun delete(id: LinkId) { rows.remove(id.value); version.value += 1 }

    override suspend fun deleteAll() { rows.clear(); version.value += 1 }

    override fun observeAll(): Flow<List<ExternalLink>> = version.map {
        rows.values.sortedBy { it.label.lowercase() }
    }

    override fun observeForAsset(assetId: AssetId): Flow<List<ExternalLink>> = version.map {
        rows.values.filter { it.assetId == assetId }.sortedBy { it.label.lowercase() }
    }
}

/** Open so a test can subclass it to rig a check on upsert order (e.g. FK-like checks). */
open class InMemoryDefinitionRepository : DefinitionRepository, Rollbackable, Witnessed {
    val rows = LinkedHashMap<String, MeasurementDefinition>()
    override var witness: TransactionWitness? = null
    private val rig = UpsertRig("definition")
    private val version = MutableStateFlow(0)
    var failOnUpsert: Int?
        get() = rig.failOnUpsert
        set(value) { rig.failOnUpsert = value }

    override fun snapshot(): () -> Unit {
        val copy = LinkedHashMap(rows)
        return { rows.clear(); rows.putAll(copy); version.value += 1 }
    }

    override suspend fun upsert(d: MeasurementDefinition) {
        rig.check()
        rows[d.id.value] = d
        version.value += 1
    }

    override suspend fun get(id: DefinitionId): MeasurementDefinition? = rows[id.value]

    override suspend fun forAsset(assetId: AssetId): List<MeasurementDefinition> =
        rows.values.filter { it.assetId == assetId }

    override suspend fun all(): List<MeasurementDefinition> {
        witness?.observeAll()
        return rows.values.toList()
    }

    override suspend fun delete(id: DefinitionId) { rows.remove(id.value); version.value += 1 }

    override suspend fun deleteAll() { rows.clear(); version.value += 1 }

    override fun observeForAsset(assetId: AssetId): Flow<List<MeasurementDefinition>> = version.map {
        rows.values.filter { it.assetId == assetId }.sortedBy { it.sortOrder }
    }
}

class InMemoryProfileRepository : ProfileRepository, Rollbackable, Witnessed {
    val rows = LinkedHashMap<String, EventProfile>()
    override var witness: TransactionWitness? = null
    private val rig = UpsertRig("profile")
    private val version = MutableStateFlow(0)
    var failOnUpsert: Int?
        get() = rig.failOnUpsert
        set(value) { rig.failOnUpsert = value }

    override fun snapshot(): () -> Unit {
        val copy = LinkedHashMap(rows)
        return { rows.clear(); rows.putAll(copy); version.value += 1 }
    }

    override suspend fun upsert(p: EventProfile) {
        rig.check()
        rows[p.id.value] = p
        version.value += 1
    }

    override suspend fun get(id: ProfileId): EventProfile? = rows[id.value]

    override suspend fun forAsset(assetId: AssetId): List<EventProfile> =
        rows.values.filter { it.assetId == assetId }

    override suspend fun all(): List<EventProfile> {
        witness?.observeAll()
        return rows.values.toList()
    }

    /**
     * Stands in for the schema's `ON DELETE SET NULL` on `event.profile_id`: production relies on
     * the foreign key, so a test that cares wires this to [InMemoryEventRepository.clearProfile].
     */
    var onDeleted: (ProfileId) -> Unit = {}

    override suspend fun delete(id: ProfileId) {
        rows.remove(id.value)
        onDeleted(id)
        version.value += 1
    }

    override suspend fun deleteAll() { rows.clear(); version.value += 1 }

    override fun observeForAsset(assetId: AssetId): Flow<List<EventProfile>> = version.map {
        rows.values.filter { it.assetId == assetId }.sortedBy { it.sortOrder }
    }
}

class InMemoryEventRepository : EventRepository, Rollbackable, Witnessed {
    val rows = LinkedHashMap<String, AssetEvent>()
    override var witness: TransactionWitness? = null
    private val rig = UpsertRig("event")
    private val version = MutableStateFlow(0)
    var failOnUpsert: Int?
        get() = rig.failOnUpsert
        set(value) { rig.failOnUpsert = value }

    override fun snapshot(): () -> Unit {
        val copy = LinkedHashMap(rows)
        return { rows.clear(); rows.putAll(copy); version.value += 1 }
    }

    override suspend fun upsert(e: AssetEvent) {
        rig.check()
        rows[e.id.value] = e
        version.value += 1
    }

    override suspend fun get(id: EventId): AssetEvent? = rows[id.value]

    override suspend fun forAsset(assetId: AssetId): List<AssetEvent> =
        rows.values.filter { it.assetId == assetId }

    override suspend fun all(): List<AssetEvent> {
        witness?.observeAll()
        return rows.values.toList()
    }

    override suspend fun countMeasurementsFor(definitionId: DefinitionId): Int =
        rows.values.sumOf { e -> e.measurements.count { it.definitionId == definitionId } }

    /** The SET NULL half of deleting a profile, driven by [InMemoryProfileRepository.onDeleted]. */
    fun clearProfile(profileId: ProfileId) {
        rows.values.filter { it.profileId == profileId }
            .forEach { rows[it.id.value] = it.copy(profileId = null) }
        version.value += 1
    }

    override suspend fun delete(id: EventId) { rows.remove(id.value); version.value += 1 }

    override suspend fun deleteAll() { rows.clear(); version.value += 1 }

    override fun observeForAsset(assetId: AssetId): Flow<List<AssetEvent>> = version.map {
        rows.values.filter { it.assetId == assetId }.sortedWith(EventChronology.reversed())
    }

    override fun observe(id: EventId): Flow<AssetEvent?> = version.map { rows[id.value] }
}

open class InMemoryAttachmentRepository : AttachmentRepository, Rollbackable, Witnessed {
    val rows = LinkedHashMap<String, Attachment>()
    override var witness: TransactionWitness? = null
    private val version = MutableStateFlow(0)
    var failOnUpsert: Int? = null
    private var upserts = 0

    override fun snapshot(): () -> Unit {
        val copy = LinkedHashMap(rows)
        return { rows.clear(); rows.putAll(copy); version.value += 1 }
    }

    override suspend fun upsert(a: Attachment) {
        upserts += 1
        if (upserts == failOnUpsert) throw RiggedFailure("rigged attachment upsert failure at #$upserts")
        rows[a.id.value] = a
        version.value += 1
    }

    override suspend fun get(id: AttachmentId): Attachment? = rows[id.value]

    override suspend fun forOwner(owner: AttachmentOwner): List<Attachment> =
        rows.values.filter { it.owner == owner }

    override suspend fun forAsset(assetId: AssetId): List<Attachment> =
        forOwner(AttachmentOwner.OfAsset(assetId))

    override suspend fun all(): List<Attachment> {
        witness?.observeAll()
        return rows.values.toList()
    }

    override suspend fun delete(id: AttachmentId) { rows.remove(id.value); version.value += 1 }
    override suspend fun deleteAll() { rows.clear(); version.value += 1 }
    override suspend fun count(): Int = rows.size

    override fun observeForOwner(owner: AttachmentOwner): Flow<List<Attachment>> = version.map {
        rows.values.filter { it.owner == owner }.sortedBy { it.displayName.lowercase() }
    }
}

/**
 * Snapshots every store before running [block] and restores them all if it throws,
 * so rollback is observable in tests without a real database.
 */
class FakeUnitOfWork(private vararg val stores: Rollbackable) : UnitOfWork {
    private val witness = TransactionWitness()

    init {
        stores.filterIsInstance<Witnessed>().forEach { it.witness = witness }
    }

    var commits = 0
        private set
    var rollbacks = 0
        private set

    /** How many read transactions have been opened. */
    var reads = 0
        private set

    /** Table reads the covered stores served outside any transaction. */
    val readsOutsideSnapshot: Int get() = witness.readsOutsideSnapshot

    override suspend fun <T> write(block: suspend () -> T): T {
        val restores = stores.map { it.snapshot() }
        witness.inWrite = true
        return try {
            val result = block()
            commits += 1
            result
        } catch (t: Throwable) {
            restores.forEach { it() }
            rollbacks += 1
            throw t
        } finally {
            witness.inWrite = false
        }
    }

    override suspend fun <T> read(block: suspend () -> T): T {
        reads += 1
        witness.inRead = true
        return try {
            block()
        } finally {
            witness.inRead = false
        }
    }
}
