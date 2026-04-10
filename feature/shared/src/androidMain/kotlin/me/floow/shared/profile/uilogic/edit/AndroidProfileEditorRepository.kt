package me.floow.shared.profile.uilogic.edit

import me.floow.domain.api.models.EditProfileData
import me.floow.domain.data.UpdateDataResponse
import me.floow.domain.data.repos.ProfileRepository
import me.floow.domain.values.ProfileDescription
import me.floow.domain.values.ProfileName
import me.floow.domain.values.ProfileUsername
import me.floow.domain.values.util.ValueValidationResult

class AndroidProfileEditorRepository(
    private val profileRepository: ProfileRepository,
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
            is UpdateDataResponse.Success -> Result.success(Unit)
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
            is UpdateDataResponse.Success -> Result.success(Unit)
            is UpdateDataResponse.Failure -> Result.failure(IllegalStateException("Failed to update profile media"))
        }
    }
}
