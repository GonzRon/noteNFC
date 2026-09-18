package com.loosecannon.servicetag.core.ports

import com.loosecannon.servicetag.core.model.Asset
import com.loosecannon.servicetag.core.model.AssetEvent
import com.loosecannon.servicetag.core.model.AssetId
import com.loosecannon.servicetag.core.model.Attachment
import com.loosecannon.servicetag.core.model.AttachmentId
import com.loosecannon.servicetag.core.model.AttachmentOwner
import com.loosecannon.servicetag.core.model.DefinitionId
import com.loosecannon.servicetag.core.model.EventId
import com.loosecannon.servicetag.core.model.EventProfile
import com.loosecannon.servicetag.core.model.ExternalLink
import com.loosecannon.servicetag.core.model.LinkId
import com.loosecannon.servicetag.core.model.MeasurementDefinition
import com.loosecannon.servicetag.core.model.PayloadFormat
import com.loosecannon.servicetag.core.model.ProfileId
import com.loosecannon.servicetag.core.model.TagBinding
import com.loosecannon.servicetag.core.model.TagId
import kotlinx.coroutines.flow.Flow

interface AssetRepository {
    suspend fun upsert(asset: Asset)
    suspend fun get(id: AssetId): Asset?
    suspend fun all(): List<Asset>
    suspend fun delete(id: AssetId)

    /**
     * Wipes every asset. With a self-referencing `parent_asset_id` FK, this must delete children
     * before parents (spec §10): the Room adapter walks `AssetTree.parentsFirst(all).asReversed()`
     * and deletes each id individually inside the caller's transaction, rather than issuing a
     * single unordered `DELETE`. A fake repository backed by a plain in-memory map may delete in
     * any order — there is no FK to violate.
     */
    suspend fun deleteAll()
    fun observeAll(): Flow<List<Asset>>
}

interface TagRepository {
    suspend fun upsert(tag: TagBinding)
    suspend fun get(id: TagId): TagBinding?
    suspend fun findByPayload(format: PayloadFormat, key: String): TagBinding?
    suspend fun forAsset(assetId: AssetId): List<TagBinding>
    suspend fun forLink(linkId: LinkId): List<TagBinding>
    suspend fun all(): List<TagBinding>
    suspend fun delete(id: TagId)
    suspend fun deleteAll()
    fun observeForAsset(assetId: AssetId): Flow<List<TagBinding>>
    fun observeForLink(linkId: LinkId): Flow<List<TagBinding>>
}

interface LinkRepository {
    suspend fun upsert(link: ExternalLink)
    suspend fun get(id: LinkId): ExternalLink?
    suspend fun forAsset(assetId: AssetId): List<ExternalLink>
    suspend fun standalone(): List<ExternalLink>
    suspend fun all(): List<ExternalLink>
    suspend fun delete(id: LinkId)
    suspend fun deleteAll()
    fun observeAll(): Flow<List<ExternalLink>>
    fun observeForAsset(assetId: AssetId): Flow<List<ExternalLink>>
}

interface DefinitionRepository {
    suspend fun upsert(d: MeasurementDefinition)
    suspend fun get(id: DefinitionId): MeasurementDefinition?
    suspend fun forAsset(assetId: AssetId): List<MeasurementDefinition>
    suspend fun all(): List<MeasurementDefinition>
    suspend fun delete(id: DefinitionId)
    suspend fun deleteAll()
    fun observeForAsset(assetId: AssetId): Flow<List<MeasurementDefinition>>
}

interface ProfileRepository {   // aggregate: upsert replaces fields and consumables
    suspend fun upsert(p: EventProfile)
    suspend fun get(id: ProfileId): EventProfile?
    suspend fun forAsset(assetId: AssetId): List<EventProfile>
    suspend fun all(): List<EventProfile>
    suspend fun delete(id: ProfileId)   // events keep their history; the schema SET NULLs profile_id
    suspend fun deleteAll()
    fun observeForAsset(assetId: AssetId): Flow<List<EventProfile>>
}

interface EventRepository {     // aggregate: upsert replaces measurements and consumables
    suspend fun upsert(e: AssetEvent)
    suspend fun get(id: EventId): AssetEvent?
    suspend fun forAsset(assetId: AssetId): List<AssetEvent>
    suspend fun all(): List<AssetEvent>

    /** How many stored measurements name [definitionId] — what makes a definition "in use". */
    suspend fun countMeasurementsFor(definitionId: DefinitionId): Int
    suspend fun delete(id: EventId)
    suspend fun deleteAll()
    fun observeForAsset(assetId: AssetId): Flow<List<AssetEvent>>   // newest first by §4.1
    fun observe(id: EventId): Flow<AssetEvent?>
}

interface AttachmentRepository {
    suspend fun upsert(a: Attachment)
    suspend fun get(id: AttachmentId): Attachment?
    suspend fun forOwner(owner: AttachmentOwner): List<Attachment>
    /** The asset's own rows only — not its events'. `DeleteAsset` asks for both, separately. */
    suspend fun forAsset(assetId: AssetId): List<Attachment>
    suspend fun all(): List<Attachment>
    suspend fun delete(id: AttachmentId)
    suspend fun deleteAll()
    suspend fun count(): Int
    fun observeForOwner(owner: AttachmentOwner): Flow<List<Attachment>>
}
