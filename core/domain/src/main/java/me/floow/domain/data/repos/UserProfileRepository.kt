package me.floow.domain.data.repos

import me.floow.domain.data.GetDataResponse
import me.floow.domain.data.UpdateDataResponse
import me.floow.domain.models.UserProfile

interface UserProfileRepository {
	suspend fun getUserProfile(): GetDataResponse<UserProfile>
	suspend fun updateUserProfile(profile: UserProfile): UpdateDataResponse
	
	// Методы для алгоритма рекомендаций
	suspend fun getSimilarUsers(myProfile: UserProfile): GetDataResponse<List<String>>
	suspend fun getAuthorLikes(authorUsername: String): GetDataResponse<List<String>>
	
	// Опциональные методы для тестирования (только для mock реализации)
	suspend fun clearProfile(): UpdateDataResponse {
		return UpdateDataResponse.Failure()
	}
	
	suspend fun resetRecommendations(): UpdateDataResponse {
		return UpdateDataResponse.Failure()
	}
}