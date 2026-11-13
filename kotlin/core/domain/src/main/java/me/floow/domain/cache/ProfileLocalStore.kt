package me.floow.domain.cache

import kotlinx.coroutines.flow.Flow
import me.floow.domain.models.PublicProfile

interface ProfileLocalStore {
    fun observeProfile(userId: String): Flow<PublicProfile?>
    suspend fun getProfile(userId: String): PublicProfile?
    suspend fun getLastUpdatedAt(userId: String): Long?
    suspend fun upsertProfile(userId: String, profile: PublicProfile, updatedAt: Long)
    suspend fun clearUser(userId: String)
}
