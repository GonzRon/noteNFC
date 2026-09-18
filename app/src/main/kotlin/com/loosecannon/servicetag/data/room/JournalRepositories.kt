package com.loosecannon.servicetag.data.room

import com.loosecannon.servicetag.core.journal.EventChronology
import com.loosecannon.servicetag.core.model.AssetEvent
import com.loosecannon.servicetag.core.model.AssetId
import com.loosecannon.servicetag.core.model.DefinitionId
import com.loosecannon.servicetag.core.model.EventId
import com.loosecannon.servicetag.core.model.EventProfile
import com.loosecannon.servicetag.core.model.MeasurementDefinition
import com.loosecannon.servicetag.core.model.ProfileId
import com.loosecannon.servicetag.core.ports.DefinitionRepository
import com.loosecannon.servicetag.core.ports.EventRepository
import com.loosecannon.servicetag.core.ports.ProfileRepository
import com.loosecannon.servicetag.data.room.dao.DefinitionDao
import com.loosecannon.servicetag.data.room.dao.EventDao
import com.loosecannon.servicetag.data.room.dao.ProfileDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomDefinitionRepository(private val dao: DefinitionDao) : DefinitionRepository {
    override suspend fun upsert(d: MeasurementDefinition) = dao.upsert(d.toEntity())
    override suspend fun get(id: DefinitionId): MeasurementDefinition? = dao.byId(id.value)?.toDomain()

    override suspend fun forAsset(assetId: AssetId): List<MeasurementDefinition> =
        dao.forAsset(assetId.value).map { it.toDomain() }

    override suspend fun all(): List<MeasurementDefinition> = dao.all().map { it.toDomain() }
    override suspend fun delete(id: DefinitionId) = dao.delete(id.value)
    override suspend fun deleteAll() = dao.deleteAll()

    override fun observeForAsset(assetId: AssetId): Flow<List<MeasurementDefinition>> =
        dao.observeForAsset(assetId.value).map { list -> list.map { it.toDomain() } }
}

/** Aggregate repository: one `upsert` writes the profile row and replaces both child tables. */
class RoomProfileRepository(private val dao: ProfileDao) : ProfileRepository {
    override suspend fun upsert(p: EventProfile) = dao.upsert(
        profile = p.toEntity(),
        fields = p.fields.map { it.toEntity(p.id) },
        consumables = p.consumables.map { it.toEntity(p.id) },
    )

    override suspend fun get(id: ProfileId): EventProfile? = dao.byId(id.value)?.toDomain()

    override suspend fun forAsset(assetId: AssetId): List<EventProfile> =
        dao.forAsset(assetId.value).map { it.toDomain() }

    override suspend fun all(): List<EventProfile> = dao.all().map { it.toDomain() }
    override suspend fun delete(id: ProfileId) = dao.delete(id.value)
    override suspend fun deleteAll() = dao.deleteAll()

    override fun observeForAsset(assetId: AssetId): Flow<List<EventProfile>> =
        dao.observeForAsset(assetId.value).map { list -> list.map { it.toDomain() } }
}

class RoomEventRepository(private val dao: EventDao) : EventRepository {
    override suspend fun upsert(e: AssetEvent) = dao.upsert(
        event = e.toEntity(),
        measurements = e.measurements.map { it.toEntity(e.id) },
        consumables = e.consumables.map { it.toEntity(e.id) },
    )

    override suspend fun get(id: EventId): AssetEvent? = dao.byId(id.value)?.toDomain()

    override suspend fun forAsset(assetId: AssetId): List<AssetEvent> =
        dao.forAsset(assetId.value).map { it.toDomain() }

    override suspend fun all(): List<AssetEvent> = dao.all().map { it.toDomain() }

    override suspend fun countMeasurementsFor(definitionId: DefinitionId): Int =
        dao.countMeasurementsFor(definitionId.value)

    override suspend fun delete(id: EventId) = dao.delete(id.value)
    override suspend fun deleteAll() = dao.deleteAll()

    /**
     * Newest first. The SQL already orders by §4.1, but the list is re-sorted with
     * [EventChronology] on the way out so the ordering rule has exactly one owner: if SQLite's
     * collation and the comparator ever disagreed, the comparator would be the one that wins.
     */
    override fun observeForAsset(assetId: AssetId): Flow<List<AssetEvent>> =
        dao.observeForAsset(assetId.value).map { list ->
            list.map { it.toDomain() }.sortedWith(EventChronology.reversed())
        }

    override fun observe(id: EventId): Flow<AssetEvent?> =
        dao.observe(id.value).map { it?.toDomain() }
}
