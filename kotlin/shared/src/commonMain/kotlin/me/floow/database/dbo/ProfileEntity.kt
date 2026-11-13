package me.floow.database.dbo

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
    val backgroundUrl: String?,
    val backgroundUpdatedAt: Long?,
    val totalLikesReceived: Int,
    val updatedAt: Long
)
