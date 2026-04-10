package me.floow.data.repos

import me.floow.domain.api.ProfileApi
import me.floow.domain.api.models.EditProfileData
import me.floow.domain.api.models.EditProfileResponseStatus
import me.floow.domain.api.models.GetSelfResponse
import me.floow.domain.data.FailureError
import me.floow.domain.data.GetDataError
import me.floow.domain.data.GetDataResponse
import me.floow.domain.data.UpdateDataResponse
import me.floow.domain.data.repos.ProfileRepository
import me.floow.domain.models.PublicProfile
import me.floow.domain.models.SelfProfile
import me.floow.domain.utils.Logger
import me.floow.domain.values.ProfileDescription
import me.floow.domain.values.ProfileName
import me.floow.domain.values.ProfileUsername
import me.floow.domain.values.util.RawValueObjectCreate

class ProfileRepositoryImpl(
	private val logger: Logger,
	private val profileApi: ProfileApi,
) : ProfileRepository {
	@OptIn(RawValueObjectCreate::class)
	override suspend fun getSelfData(): GetDataResponse<SelfProfile> {
		return when (val selfData = profileApi.getSelf()) {
			is GetSelfResponse.Success -> {
				logger.d("ProfileRepositoryImpl.getSelfData", "Success response: $selfData")

				GetDataResponse.Success(
					SelfProfile(
						name = selfData.name?.let { ProfileName.createRaw(it) },
						username = selfData.username?.let { ProfileUsername.createRaw(it) },
						description = selfData.biography?.let { ProfileDescription.createRaw(it) },
						avatarUrl = selfData.avatarUrl,
						backgroundUrl = selfData.backgroundUrl,
						backgroundUpdatedAt = selfData.backgroundUpdatedAt,
						totalLikesReceived = selfData.totalLikesReceived,
					)
				)
			}

			is GetSelfResponse.Error -> {
				logger.d("ProfileRepositoryImpl.getSelfData", "Failure response: $selfData")
				GetDataResponse.Error(error = GetDataError.Other)
			}
		}
	}

	@OptIn(RawValueObjectCreate::class)
	override suspend fun getUserProfile(userId: String): GetDataResponse<PublicProfile> {
		return when (val userData = profileApi.getUserProfile(userId)) {
			is GetSelfResponse.Success -> {
				logger.d("ProfileRepositoryImpl.getUserProfile", "Success response: $userData")

				GetDataResponse.Success(
					PublicProfile(
						id = userId,
						name = userData.name?.let { ProfileName.createRaw(it) },
						username = userData.username?.let { ProfileUsername.createRaw(it) },
						description = userData.biography?.let { ProfileDescription.createRaw(it) },
						avatarUrl = userData.avatarUrl,
						backgroundUrl = userData.backgroundUrl,
						backgroundUpdatedAt = userData.backgroundUpdatedAt,
						totalLikesReceived = userData.totalLikesReceived,
					)
				)
			}

			is GetSelfResponse.Error -> {
				logger.d("ProfileRepositoryImpl.getUserProfile", "Failure response: $userData")
				GetDataResponse.Error(error = GetDataError.Other)
			}
		}
	}

	override suspend fun edit(data: EditProfileData): UpdateDataResponse {
		val result = profileApi.edit(data = data)
		return when (result.status) {
			EditProfileResponseStatus.ERROR -> UpdateDataResponse.Failure(FailureError.Other)
			EditProfileResponseStatus.SUCCESS -> UpdateDataResponse.Success
			EditProfileResponseStatus.USERNAME_ALREADY_EXISTS -> UpdateDataResponse.Failure(FailureError.UsernameAlreadyExists)
		}
	}

	override suspend fun updateAvatarUrl(avatarUrl: String): UpdateDataResponse {
		val success = profileApi.updateAvatarUrl(avatarUrl)
		return if (success) UpdateDataResponse.Success else UpdateDataResponse.Failure(FailureError.Other)
	}

	override suspend fun updateBackgroundUrl(backgroundUrl: String): UpdateDataResponse {
		val success = profileApi.updateBackgroundUrl(backgroundUrl)
		return if (success) UpdateDataResponse.Success else UpdateDataResponse.Failure(FailureError.Other)
	}

	override suspend fun checkUsername(username: String): Boolean {
		return profileApi.checkUsername(username)
	}
}
