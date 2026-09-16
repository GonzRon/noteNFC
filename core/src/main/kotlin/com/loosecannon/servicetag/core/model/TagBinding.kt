package com.loosecannon.servicetag.core.model

enum class PayloadFormat { V1 }

enum class TagStatus { ACTIVE, UNBOUND, LOST, RETIRED }

sealed interface TagTarget {
    data class AssetTarget(val assetId: AssetId) : TagTarget
    data class LinkTarget(val linkId: LinkId) : TagTarget
    data object None : TagTarget
}

data class TagBinding(
    val id: TagId,
    val payloadFormat: PayloadFormat,
    val payloadKey: String,
    val target: TagTarget = TagTarget.None,
    val status: TagStatus = TagStatus.ACTIVE,
    val label: String? = null,
    val physicalUid: String? = null,
    val writtenAt: Long? = null,
    val lastScannedAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
)
