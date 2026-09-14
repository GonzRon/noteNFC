package com.loosecannon.notenfc.core.model

enum class AssetStatus { ACTIVE, ARCHIVED, RETIRED }

data class Asset(
    val id: AssetId,
    val name: String,
    val description: String = "",
    val category: String = "",
    val notes: String = "",
    val status: AssetStatus = AssetStatus.ACTIVE,
    val createdAt: Long,
    val updatedAt: Long,
)
