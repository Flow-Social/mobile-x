package me.floow.database.localstore

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import me.floow.database.dao.ProfileDao
import me.floow.database.dbo.ProfileEntity
import me.floow.domain.cache.ProfileLocalStore
import me.floow.domain.models.PublicProfile
import me.floow.domain.values.ProfileDescription
import me.floow.domain.values.ProfileName
import me.floow.domain.values.ProfileUsername
import me.floow.domain.values.util.RawValueObjectCreate

class ProfileLocalStoreImpl(
    private val profileDao: ProfileDao
) : ProfileLocalStore {
    override fun observeProfile(userId: String): Flow<PublicProfile?> {
        return profileDao.observeProfile(userId).map { entity ->
            entity?.toDomain()
        }
    }

    override suspend fun getProfile(userId: String): PublicProfile? {
        return profileDao.getProfile(userId)?.toDomain()
    }

    override suspend fun getLastUpdatedAt(userId: String): Long? {
        return profileDao.getUpdatedAt(userId)
    }

    override suspend fun upsertProfile(userId: String, profile: PublicProfile, updatedAt: Long) {
        profileDao.upsert(profile.toEntity(userId, updatedAt))
    }

    override suspend fun clearUser(userId: String) {
        profileDao.deleteByUser(userId)
    }
}

@OptIn(RawValueObjectCreate::class)
private fun ProfileEntity.toDomain(): PublicProfile {
    return PublicProfile(
        id = userId,
        name = name?.let { ProfileName.createRaw(it) },
        username = username?.let { ProfileUsername.createRaw(it) },
        avatarUrl = avatarUrl,
        backgroundUrl = backgroundUrl,
        backgroundUpdatedAt = backgroundUpdatedAt,
        description = description?.let { ProfileDescription.createRaw(it) },
        totalLikesReceived = totalLikesReceived
    )
}

private fun PublicProfile.toEntity(userId: String, updatedAt: Long): ProfileEntity {
    return ProfileEntity(
        userId = userId,
        name = name?.value,
        username = username?.value,
        description = description?.value,
        avatarUrl = avatarUrl,
        backgroundUrl = backgroundUrl,
        backgroundUpdatedAt = backgroundUpdatedAt,
        totalLikesReceived = totalLikesReceived,
        updatedAt = updatedAt
    )
}
