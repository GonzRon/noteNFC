package com.loosecannon.notenfc.core.backup

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
import kotlinx.serialization.Serializable

/**
 * What the backup says about itself. `dataSha256` is the hex SHA-256 of the exact
 * `data.json` bytes in the same archive, so a truncated or edited file is caught on read.
 */
@Serializable
data class BackupManifest(
    val formatVersion: Int,
    val appVersion: String,
    val schemaVersion: Int,
    val createdAt: Long,
    val counts: Map<String, Int>,
    val dataSha256: String,
)

@Serializable
data class AssetDto(
    val id: String,
    val name: String,
    val description: String,
    val category: String,
    val notes: String,
    val status: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class NfcTagDto(
    val id: String,
    val payloadFormat: String,
    val payloadKey: String,
    val assetId: String?,
    val linkId: String?,
    val status: String,
    val label: String?,
    val physicalUid: String?,
    val writtenAt: Long?,
    val lastScannedAt: Long?,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class ExternalLinkDto(
    val id: String,
    val assetId: String?,
    val kind: String,
    val label: String,
    val uri: String,
    val createdAt: Long,
    val lastOpenedAt: Long?,
    val updatedAt: Long,
)

/** The canonical tables of Phase 1A. Everything derived is rebuilt after an import. */
@Serializable
data class BackupData(
    val assets: List<AssetDto>,
    val nfcTags: List<NfcTagDto>,
    val externalLinks: List<ExternalLinkDto>,
)

/** A decoded archive: what it claims about itself, and what it holds. */
data class Backup(val manifest: BackupManifest, val data: BackupData)

// --- mapping ------------------------------------------------------------------------------------

private inline fun <reified E : Enum<E>> enumOrCorrupt(name: String, field: String, owner: String): E =
    enumValues<E>().firstOrNull { it.name == name }
        ?: throw BackupCorrupt("unknown $field \"$name\" on $owner")

fun Asset.toDto(): AssetDto = AssetDto(
    id = id.value,
    name = name,
    description = description,
    category = category,
    notes = notes,
    status = status.name,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun AssetDto.toDomain(): Asset = Asset(
    id = AssetId(id),
    name = name,
    description = description,
    category = category,
    notes = notes,
    status = enumOrCorrupt<AssetStatus>(status, "asset status", "asset $id"),
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun TagBinding.toDto(): NfcTagDto = NfcTagDto(
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

fun NfcTagDto.toDomain(): TagBinding {
    if (assetId != null && linkId != null) {
        throw BackupCorrupt("tag $id points at both an asset and a link")
    }
    val target = when {
        assetId != null -> TagTarget.AssetTarget(AssetId(assetId))
        linkId != null -> TagTarget.LinkTarget(LinkId(linkId))
        else -> TagTarget.None
    }
    return TagBinding(
        id = TagId(id),
        payloadFormat = enumOrCorrupt<PayloadFormat>(payloadFormat, "payload format", "tag $id"),
        payloadKey = payloadKey,
        target = target,
        status = enumOrCorrupt<TagStatus>(status, "tag status", "tag $id"),
        label = label,
        physicalUid = physicalUid,
        writtenAt = writtenAt,
        lastScannedAt = lastScannedAt,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}

fun ExternalLink.toDto(): ExternalLinkDto = ExternalLinkDto(
    id = id.value,
    assetId = assetId?.value,
    kind = kind.name,
    label = label,
    uri = uri,
    createdAt = createdAt,
    lastOpenedAt = lastOpenedAt,
    updatedAt = updatedAt,
)

fun ExternalLinkDto.toDomain(): ExternalLink = ExternalLink(
    id = LinkId(id),
    assetId = assetId?.let(::AssetId),
    kind = enumOrCorrupt<LinkKind>(kind, "link kind", "link $id"),
    label = label,
    uri = uri,
    createdAt = createdAt,
    lastOpenedAt = lastOpenedAt,
    updatedAt = updatedAt,
)
