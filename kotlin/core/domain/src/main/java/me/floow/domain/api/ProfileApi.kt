package me.floow.domain.api

import me.floow.domain.api.models.DeleteProfileResponse
import me.floow.domain.api.models.EditProfileData
import me.floow.domain.api.models.EditProfileResponse
import me.floow.domain.api.models.GetSelfResponse

interface ProfileApi {
	suspend fun getSelf(): GetSelfResponse

	suspend fun getUserProfile(userId: String): GetSelfResponse

	suspend fun edit(data: EditProfileData): EditProfileResponse

	suspend fun updateProfileMedia(kind: String, mediaUrl: String): Boolean

	suspend fun updateAvatarUrl(avatarUrl: String): Boolean

	suspend fun updateBackgroundUrl(backgroundUrl: String): Boolean

	suspend fun checkUsername(username: String): Boolean

	suspend fun deleteProfile(): DeleteProfileResponse
}
