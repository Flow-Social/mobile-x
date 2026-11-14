package me.floow.database.dbo

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey
    val userId: String,
    val name: String?,
    val username: String?,
    val description: String?,
    val avatarUrl: String?,
    @ColumnInfo(name = "avatar_thumb")
    val avatarThumb: String? = null,
    val backgroundUrl: String?,
    @ColumnInfo(name = "background_thumb")
    val backgroundThumb: String? = null,
    val backgroundUpdatedAt: Long?,
    val totalLikesReceived: Int,
    val updatedAt: Long
)
