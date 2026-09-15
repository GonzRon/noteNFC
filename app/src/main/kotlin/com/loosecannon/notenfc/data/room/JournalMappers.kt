package com.loosecannon.notenfc.data.room

import com.loosecannon.notenfc.core.model.AssetEvent
import com.loosecannon.notenfc.core.model.AssetId
import com.loosecannon.notenfc.core.model.ConsumableUsage
import com.loosecannon.notenfc.core.model.DefinitionId
import com.loosecannon.notenfc.core.model.EventId
import com.loosecannon.notenfc.core.model.EventKind
import com.loosecannon.notenfc.core.model.EventProfile
import com.loosecannon.notenfc.core.model.EventSource
import com.loosecannon.notenfc.core.model.Measurement
import com.loosecannon.notenfc.core.model.MeasurementDefinition
import com.loosecannon.notenfc.core.model.ProfileConsumable
import com.loosecannon.notenfc.core.model.ProfileField
import com.loosecannon.notenfc.core.model.ProfileId
import com.loosecannon.notenfc.core.model.ValueType
import com.loosecannon.notenfc.data.room.dao.EventWithParts
import com.loosecannon.notenfc.data.room.dao.ProfileWithParts
import com.loosecannon.notenfc.data.room.entities.AssetEventEntity
import com.loosecannon.notenfc.data.room.entities.ConsumableUsageEntity
import com.loosecannon.notenfc.data.room.entities.EventProfileEntity
import com.loosecannon.notenfc.data.room.entities.MeasurementDefinitionEntity
import com.loosecannon.notenfc.data.room.entities.MeasurementEntity
import com.loosecannon.notenfc.data.room.entities.ProfileConsumableEntity
import com.loosecannon.notenfc.data.room.entities.ProfileFieldEntity

// Schema v2's half of the mapping layer. Same rules as [Mappers.kt]: enums travel as their Kotlin
// name and `valueOf` rejects anything else here, at the repository boundary. Child-row ids pass
// through unchanged in both directions — the database never mints one, the domain always does.
// `@Relation` returns children in no particular order, so every read sorts by `sortOrder`.

fun MeasurementDefinitionEntity.toDomain(): MeasurementDefinition = MeasurementDefinition(
    id = DefinitionId(id),
    assetId = AssetId(assetId),
    key = key,
    label = label,
    unit = unit,
    valueType = ValueType.valueOf(valueType),
    decimals = decimals,
    rangeLow = rangeLow,
    rangeHigh = rangeHigh,
    isMeter = isMeter,
    sortOrder = sortOrder,
    archivedAt = archivedAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun MeasurementDefinition.toEntity(): MeasurementDefinitionEntity = MeasurementDefinitionEntity(
    id = id.value,
    assetId = assetId.value,
    key = key,
    label = label,
    unit = unit,
    valueType = valueType.name,
    decimals = decimals,
    rangeLow = rangeLow,
    rangeHigh = rangeHigh,
    isMeter = isMeter,
    sortOrder = sortOrder,
    archivedAt = archivedAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun ProfileWithParts.toDomain(): EventProfile = EventProfile(
    id = ProfileId(profile.id),
    assetId = AssetId(profile.assetId),
    name = profile.name,
    eventKind = EventKind.valueOf(profile.eventKind),
    defaultTitle = profile.defaultTitle,
    templateKey = profile.templateKey,
    sortOrder = profile.sortOrder,
    archivedAt = profile.archivedAt,
    createdAt = profile.createdAt,
    updatedAt = profile.updatedAt,
    fields = fields.sortedBy { it.sortOrder }.map {
        ProfileField(
            id = it.id,
            definitionId = DefinitionId(it.definitionId),
            required = it.required,
            sortOrder = it.sortOrder,
        )
    },
    consumables = consumables.sortedBy { it.sortOrder }.map {
        ProfileConsumable(
            id = it.id,
            name = it.name,
            defaultQuantity = it.defaultQuantity,
            unit = it.unit,
            sortOrder = it.sortOrder,
        )
    },
)

fun EventProfile.toEntity(): EventProfileEntity = EventProfileEntity(
    id = id.value,
    assetId = assetId.value,
    name = name,
    eventKind = eventKind.name,
    defaultTitle = defaultTitle,
    templateKey = templateKey,
    sortOrder = sortOrder,
    archivedAt = archivedAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun ProfileField.toEntity(profileId: ProfileId): ProfileFieldEntity = ProfileFieldEntity(
    id = id,
    profileId = profileId.value,
    definitionId = definitionId.value,
    required = required,
    sortOrder = sortOrder,
)

fun ProfileConsumable.toEntity(profileId: ProfileId): ProfileConsumableEntity = ProfileConsumableEntity(
    id = id,
    profileId = profileId.value,
    name = name,
    defaultQuantity = defaultQuantity,
    unit = unit,
    sortOrder = sortOrder,
)

fun EventWithParts.toDomain(): AssetEvent = AssetEvent(
    id = EventId(event.id),
    assetId = AssetId(event.assetId),
    kind = EventKind.valueOf(event.kind),
    title = event.title,
    profileId = event.profileId?.let(::ProfileId),
    occurredOn = event.occurredOn,
    occurredTime = event.occurredTime,
    tzId = event.tzId,
    notes = event.notes,
    source = EventSource.valueOf(event.source),
    sourceRef = event.sourceRef,
    createdAt = event.createdAt,
    updatedAt = event.updatedAt,
    measurements = measurements.sortedBy { it.sortOrder }.map {
        Measurement(
            id = it.id,
            definitionId = DefinitionId(it.definitionId),
            valueNum = it.valueNum,
            valueText = it.valueText,
            unit = it.unit,
            sortOrder = it.sortOrder,
        )
    },
    consumables = consumables.sortedBy { it.sortOrder }.map {
        ConsumableUsage(
            id = it.id,
            name = it.name,
            quantity = it.quantity,
            unit = it.unit,
            sortOrder = it.sortOrder,
        )
    },
)

fun AssetEvent.toEntity(): AssetEventEntity = AssetEventEntity(
    id = id.value,
    assetId = assetId.value,
    kind = kind.name,
    title = title,
    profileId = profileId?.value,
    occurredOn = occurredOn,
    occurredTime = occurredTime,
    tzId = tzId,
    notes = notes,
    source = source.name,
    sourceRef = sourceRef,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun Measurement.toEntity(eventId: EventId): MeasurementEntity = MeasurementEntity(
    id = id,
    eventId = eventId.value,
    definitionId = definitionId.value,
    valueNum = valueNum,
    valueText = valueText,
    unit = unit,
    sortOrder = sortOrder,
)

fun ConsumableUsage.toEntity(eventId: EventId): ConsumableUsageEntity = ConsumableUsageEntity(
    id = id,
    eventId = eventId.value,
    name = name,
    quantity = quantity,
    unit = unit,
    sortOrder = sortOrder,
)
