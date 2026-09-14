package com.loosecannon.notenfc.data.room.entities

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(tableName = "asset", indices = [Index("status"), Index("name")])
data class AssetEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val category: String,
    val notes: String,
    val status: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)
