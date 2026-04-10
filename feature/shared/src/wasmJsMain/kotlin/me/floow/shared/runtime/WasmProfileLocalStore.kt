package me.floow.shared.runtime

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import me.floow.domain.cache.ProfileLocalStore
import me.floow.domain.models.PublicProfile

class WasmProfileLocalStore : ProfileLocalStore {
    private val profilesByUser = MutableStateFlow<Map<String, PublicProfile>>(emptyMap())
    private val updatedAtByUser = mutableMapOf<String, Long>()

    override fun observeProfile(userId: String): Flow<PublicProfile?> {
        return profilesByUser.map { it[userId] }
    }

    override suspend fun getProfile(userId: String): PublicProfile? {
        return profilesByUser.value[userId]
    }

    override suspend fun getLastUpdatedAt(userId: String): Long? {
        return updatedAtByUser[userId]
    }

    override suspend fun upsertProfile(userId: String, profile: PublicProfile, updatedAt: Long) {
        updatedAtByUser[userId] = updatedAt
        profilesByUser.update { current -> current + (userId to profile) }
    }

    override suspend fun clearUser(userId: String) {
        updatedAtByUser.remove(userId)
        profilesByUser.update { current -> current - userId }
    }
}
