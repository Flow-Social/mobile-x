package me.floow.shared.profile.uilogic.edit

import kotlinx.coroutines.flow.firstOrNull
import me.floow.domain.api.models.EditProfileData
import me.floow.domain.cache.ProfileLocalStore
import me.floow.domain.data.GetDataResponse
import me.floow.domain.data.UpdateDataResponse
import me.floow.domain.data.repos.ProfileRepository
import me.floow.domain.models.PublicProfile
import me.floow.domain.values.ProfileDescription
import me.floow.domain.values.ProfileName
import me.floow.domain.values.ProfileUsername
import me.floow.domain.values.util.ValueValidationResult

class AndroidProfileEditorRepository(
    private val profileRepository: ProfileRepository,
    private val profileLocalStore: ProfileLocalStore,
) : ProfileEditorRepository {
    override suspend fun checkUsernameAvailability(username: String): Boolean {
        return profileRepository.checkUsername(username)
    }

    override suspend fun updateProfile(
        name: String,
        username: String,
        bio: String,
    ): Result<Unit> {
        val validatedName = when (val result = ProfileName.createWithValidation(name)) {
            is ValueValidationResult.Valid -> result.value
            is ValueValidationResult.Invalid -> return Result.failure(IllegalArgumentException("Invalid profile name"))
            else -> return Result.failure(IllegalArgumentException("Invalid profile name"))
        }
        val validatedUsername = when (val result = ProfileUsername.createWithValidation(username)) {
            is ValueValidationResult.Valid -> result.value
            is ValueValidationResult.Invalid -> return Result.failure(IllegalArgumentException("Invalid profile username"))
            else -> return Result.failure(IllegalArgumentException("Invalid profile username"))
        }
        val validatedDescription = when (val result = ProfileDescription.createWithValidation(bio)) {
            is ValueValidationResult.Valid -> result.value
            is ValueValidationResult.Invalid -> return Result.failure(IllegalArgumentException("Invalid profile bio"))
            else -> return Result.failure(IllegalArgumentException("Invalid profile bio"))
        }
        return when (profileRepository.edit(
            EditProfileData(
                name = validatedName,
                username = validatedUsername,
                description = validatedDescription,
            )
        )) {
            is UpdateDataResponse.Success -> {
                upsertLocalProfile(name, username, bio)
                refreshLocalProfileInBackground()
                Result.success(Unit)
            }
            is UpdateDataResponse.Failure -> Result.failure(IllegalStateException("Failed to update profile"))
        }
    }

    override suspend fun updateProfileMedia(
        kind: String,
        mediaUrl: String,
    ): Result<Unit> {
        val response = when (kind) {
            "avatar" -> profileRepository.updateAvatarUrl(mediaUrl)
            "background" -> profileRepository.updateBackgroundUrl(mediaUrl)
            else -> return Result.failure(IllegalArgumentException("Unsupported media kind: $kind"))
        }
        return when (response) {
            is UpdateDataResponse.Success -> {
                refreshLocalProfileInBackground()
                Result.success(Unit)
            }
            is UpdateDataResponse.Failure -> Result.failure(IllegalStateException("Failed to update profile media"))
        }
    }

    private suspend fun upsertLocalProfile(name: String, username: String, bio: String) {
        val cached = profileLocalStore.observeProfile(SELF_USER_ID).firstOrNull()
        val updated = PublicProfile(
            id = SELF_USER_ID,
            name = ProfileName.create(name),
            username = ProfileUsername.create(username),
            description = ProfileDescription.create(bio),
            avatarUrl = cached?.avatarUrl,
            backgroundUrl = cached?.backgroundUrl,
            backgroundUpdatedAt = cached?.backgroundUpdatedAt,
            totalLikesReceived = cached?.totalLikesReceived ?: 0,
        )
        profileLocalStore.upsertProfile(
            userId = SELF_USER_ID,
            profile = updated,
            updatedAt = System.currentTimeMillis(),
        )
    }

    private suspend fun refreshLocalProfileInBackground() {
        when (val result = profileRepository.getSelfData()) {
            is GetDataResponse.Success -> {
                val refreshed = PublicProfile(
                    id = SELF_USER_ID,
                    name = result.data.name,
                    username = result.data.username,
                    description = result.data.description,
                    avatarUrl = result.data.avatarUrl,
                    backgroundUrl = result.data.backgroundUrl,
                    backgroundUpdatedAt = result.data.backgroundUpdatedAt,
                    totalLikesReceived = result.data.totalLikesReceived,
                )
                profileLocalStore.upsertProfile(
                    userId = SELF_USER_ID,
                    profile = refreshed,
                    updatedAt = System.currentTimeMillis(),
                )
            }
            is GetDataResponse.Error -> Unit
        }
    }

    private companion object {
        const val SELF_USER_ID = "me"
    }
}
