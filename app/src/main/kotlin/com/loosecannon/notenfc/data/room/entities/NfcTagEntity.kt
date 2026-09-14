package com.loosecannon.notenfc.data.room.entities

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(
    tableName = "nfc_tag",
    foreignKeys = [
        ForeignKey(
            entity = AssetEntity::class,
            parentColumns = ["id"],
            childColumns = ["asset_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = ExternalLinkEntity::class,
            parentColumns = ["id"],
            childColumns = ["link_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["payload_format", "payload_key"], unique = true),
        Index("asset_id"),
        Index("link_id"),
    ],
)
data class NfcTagEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "payload_format") val payloadFormat: String,
    @ColumnInfo(name = "payload_key") val payloadKey: String,
    @ColumnInfo(name = "asset_id") val assetId: String?,
    @ColumnInfo(name = "link_id") val linkId: String?,
    val status: String,
    val label: String?,
    @ColumnInfo(name = "physical_uid") val physicalUid: String?,
    @ColumnInfo(name = "written_at") val writtenAt: Long?,
    @ColumnInfo(name = "last_scanned_at") val lastScannedAt: Long?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)
