package me.floow.database.localstore

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import me.floow.database.dao.FeedStackSnapshotDao
import me.floow.database.dbo.FeedStackSnapshotEntity
import me.floow.domain.cache.FeedStackSnapshot
import me.floow.domain.cache.FeedStackSnapshotLocalStore

class FeedStackSnapshotLocalStoreImpl(
    private val dao: FeedStackSnapshotDao,
) : FeedStackSnapshotLocalStore {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; encodeDefaults = true }

    override fun observeSnapshot(userId: String): Flow<FeedStackSnapshot?> {
        return flow {
            emit(getSnapshot(userId))
        }
    }

    override suspend fun getSnapshot(userId: String): FeedStackSnapshot? {
        return dao.getSnapshot(userId)?.let { entity ->
            runCatching { json.decodeFromString<FeedStackSnapshot>(entity.snapshotJson) }.getOrNull()
        }
    }

    override suspend fun getLastUpdatedAt(userId: String): Long? {
        return dao.getLastUpdatedAt(userId)
    }

    override suspend fun replaceSnapshot(userId: String, snapshot: FeedStackSnapshot, updatedAt: Long) {
        dao.upsert(
            FeedStackSnapshotEntity(
                userId = userId,
                snapshotJson = json.encodeToString(snapshot),
                updatedAt = updatedAt,
            )
        )
    }

    override suspend fun clearUser(userId: String) {
        dao.deleteByUser(userId)
    }
}
