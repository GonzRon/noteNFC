package com.loosecannon.notenfc.data.room

import com.loosecannon.notenfc.core.model.Asset
import com.loosecannon.notenfc.core.model.AssetId
import com.loosecannon.notenfc.core.model.AssetStatus
import com.loosecannon.notenfc.core.model.ExternalLink
import com.loosecannon.notenfc.core.model.LinkId
import com.loosecannon.notenfc.core.model.LinkKind
import com.loosecannon.notenfc.core.model.PayloadFormat
import com.loosecannon.notenfc.core.model.TagBinding
import com.loosecannon.notenfc.core.model.TagId
import com.loosecannon.notenfc.core.model.TagStatus
import com.loosecannon.notenfc.core.model.TagTarget
import com.loosecannon.notenfc.data.room.entities.AssetEntity
import com.loosecannon.notenfc.data.room.entities.ExternalLinkEntity
import com.loosecannon.notenfc.data.room.entities.NfcTagEntity

// Enums are stored as TEXT holding the Kotlin enum name (D4 conventions); `valueOf` rejects
// anything else at the repository boundary.

// `status` goes through `AssetStatus.valueOf`, and v4 knows only ACTIVE and ARCHIVED: RETIRED is
// gone from the enum, `MIGRATION_3_4` rewrote any stored one, and retirement is `retired_on` now
// (spec §7). Anything else in the column is a corrupt row and throws, as it always did.
fun AssetEntity.toDomain(): Asset = Asset(
    id = AssetId(id),
    name = name,
    description = description,
    category = category,
    notes = notes,
    status = AssetStatus.valueOf(status),
    templateKey = templateKey,
    createdAt = createdAt,
    updatedAt = updatedAt,
    manufacturer = manufacturer,
    model = model,
    serialNumber = serialNumber,
    purchaseOn = purchaseOn,
    inServiceOn = inServiceOn,
    purchasePriceMinor = purchasePriceMinor,
    currency = currency,
    vendor = vendor,
    location = location,
    warrantyExpiresOn = warrantyExpiresOn,
    warrantyNotes = warrantyNotes,
    retiredOn = retiredOn,
    parentAssetId = parentAssetId?.let(::AssetId),
    seasonStartMmdd = seasonStartMmdd,
    seasonEndMmdd = seasonEndMmdd,
)

fun Asset.toEntity(): AssetEntity = AssetEntity(
    id = id.value,
    name = name,
    description = description,
    category = category,
    notes = notes,
    status = status.name,
    templateKey = templateKey,
    createdAt = createdAt,
    updatedAt = updatedAt,
    manufacturer = manufacturer,
    model = model,
    serialNumber = serialNumber,
    purchaseOn = purchaseOn,
    inServiceOn = inServiceOn,
    purchasePriceMinor = purchasePriceMinor,
    currency = currency,
    vendor = vendor,
    location = location,
    warrantyExpiresOn = warrantyExpiresOn,
    warrantyNotes = warrantyNotes,
    retiredOn = retiredOn,
    parentAssetId = parentAssetId?.value,
    seasonStartMmdd = seasonStartMmdd,
    seasonEndMmdd = seasonEndMmdd,
)

fun NfcTagEntity.toDomain(): TagBinding = TagBinding(
    id = TagId(id),
    payloadFormat = PayloadFormat.valueOf(payloadFormat),
    payloadKey = payloadKey,
    target = when {
        assetId != null -> TagTarget.AssetTarget(AssetId(assetId))
        linkId != null -> TagTarget.LinkTarget(LinkId(linkId))
        else -> TagTarget.None
    },
    status = TagStatus.valueOf(status),
    label = label,
    physicalUid = physicalUid,
    writtenAt = writtenAt,
    lastScannedAt = lastScannedAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun TagBinding.toEntity(): NfcTagEntity = NfcTagEntity(
    id = id.value,
    payloadFormat = payloadFormat.name,
    payloadKey = payloadKey,
    assetId = (target as? TagTarget.AssetTarget)?.assetId?.value,
    linkId = (target as? TagTarget.LinkTarget)?.linkId?.value,
    status = status.name,
    label = label,
    physicalUid = physicalUid,
    writtenAt = writtenAt,
    lastScannedAt = lastScannedAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun ExternalLinkEntity.toDomain(): ExternalLink = ExternalLink(
    id = LinkId(id),
    assetId = assetId?.let(::AssetId),
    kind = LinkKind.valueOf(kind),
    label = label,
    uri = uri,
    createdAt = createdAt,
    lastOpenedAt = lastOpenedAt,
    updatedAt = updatedAt,
)

fun ExternalLink.toEntity(): ExternalLinkEntity = ExternalLinkEntity(
    id = id.value,
    assetId = assetId?.value,
    kind = kind.name,
    label = label,
    uri = uri,
    createdAt = createdAt,
    lastOpenedAt = lastOpenedAt,
    updatedAt = updatedAt,
)
