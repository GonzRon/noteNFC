package com.loosecannon.servicetag.core.model

enum class LinkKind { JOPLIN, OBSIDIAN, LOGSEQ, WEB, OTHER }

data class ExternalLink(
    val id: LinkId,
    val assetId: AssetId? = null,
    val kind: LinkKind,
    val label: String,
    val uri: String,
    val createdAt: Long,
    val lastOpenedAt: Long? = null,
    val updatedAt: Long,
)
