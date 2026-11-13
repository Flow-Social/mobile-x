package me.floow.data.repos

import me.floow.domain.data.GetDataError
import me.floow.domain.data.GetDataResponse
import me.floow.domain.data.UpdateDataResponse
import me.floow.domain.data.repos.UserProfileRepository
import me.floow.domain.models.UserProfile
import me.floow.domain.utils.Logger

class UserProfileRepositoryImpl(
	private val logger: Logger,
) : UserProfileRepository {

	override suspend fun getUserProfile(): GetDataResponse<UserProfile> {
		logger.d("UserProfileRepositoryImpl.getUserProfile", "Loading user profile from API")

		return GetDataResponse.Success(UserProfile())
	}

	override suspend fun updateUserProfile(profile: UserProfile): UpdateDataResponse {
		logger.d("UserProfileRepositoryImpl.updateUserProfile", "Updating user profile via API")

		return UpdateDataResponse.Failure()
	}

	override suspend fun getSimilarUsers(myProfile: UserProfile): GetDataResponse<List<String>> {
		logger.d("UserProfileRepositoryImpl.getSimilarUsers", "Loading similar users from API")

		return GetDataResponse.Error(GetDataError.Other)
	}

	override suspend fun getAuthorLikes(authorUsername: String): GetDataResponse<List<String>> {
		logger.d("UserProfileRepositoryImpl.getAuthorLikes", "Loading author likes for $authorUsername from API")

		return GetDataResponse.Error(GetDataError.Other)
	}
}
