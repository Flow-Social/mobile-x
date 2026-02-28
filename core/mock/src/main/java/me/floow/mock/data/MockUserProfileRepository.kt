package me.floow.mock.data

import kotlinx.coroutines.delay
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import me.floow.domain.data.GetDataResponse
import me.floow.domain.data.UpdateDataResponse
import me.floow.domain.data.repos.UserProfileRepository
import me.floow.domain.models.UserProfile
import me.floow.mock.interfaces.MockStorage

class MockUserProfileRepository(
    private val mockStorage: MockStorage
) : UserProfileRepository {
	
	private var currentProfile: UserProfile
        get() {
            val json = mockStorage.getString("user_profile_json")
            return if (json != null) {
                try {
                    Json.decodeFromString(json)
                } catch (e: Exception) {
                    UserProfile()
                }
            } else {
                UserProfile()
            }
        }
        set(value) {
            val json = Json.encodeToString(value)
            mockStorage.saveString("user_profile_json", json)
        }
	
	// Имитация лайков авторов для алгоритма
	private val mockAuthorLikes = mapOf(
		"anna_cats" to listOf("nature_1", "nature_3", "nature_7", "food_1", "food_5", "lifestyle_1"),
		"petr_travel" to listOf("travel_1", "travel_3", "travel_6", "nature_2", "nature_5", "food_3"),
		"maria_art" to listOf("art_1", "art_3", "art_7", "travel_8", "nature_1", "lifestyle_5"),
		"alex_memes" to listOf("memes_1", "memes_3", "memes_6", "tech_2", "tech_4", "tech_7"),
		"alex_sculptor" to listOf("art_1", "art_2", "art_4", "art_8", "nature_1", "lifestyle_2"),
		"lena_humor" to listOf("memes_2", "memes_4", "memes_7", "tech_5", "tech_8", "art_5"),
		"chef_ivan" to listOf("food_1", "food_3", "food_7", "nature_6", "nature_9", "travel_1"),
		"katya_sweets" to listOf("food_2", "food_4", "food_8", "art_6", "art_9", "lifestyle_1"),
		"sveta_blogger" to listOf("travel_2", "travel_4", "nature_4", "nature_8", "food_6", "lifestyle_1"),
		"dima_dev" to listOf("tech_1", "tech_3", "tech_6", "memes_5", "memes_8", "art_4"),
		"olya_fitness" to listOf("lifestyle_1", "lifestyle_3", "lifestyle_6", "travel_5", "travel_7", "nature_1"),
		"max_style" to listOf("lifestyle_2", "lifestyle_4", "lifestyle_7", "art_5", "travel_1", "tech_4")
	)
	
	// Имитация пользователей с похожими вкусами
	private val mockSimilarUsers = listOf(
		"user_nature_lover", "user_art_fan", "user_foodie", "user_traveler", 
		"user_tech_geek", "user_meme_lord", "user_lifestyle_guru"
	)
	
	override suspend fun getUserProfile(): GetDataResponse<UserProfile> {
		delay(100) // имитация загрузки
		return GetDataResponse.Success(data = currentProfile)
	}
	
	override suspend fun updateUserProfile(profile: UserProfile): UpdateDataResponse {
		delay(50) // имитация сохранения
		currentProfile = profile
		return UpdateDataResponse.Success
	}
	
	override suspend fun getSimilarUsers(myProfile: UserProfile): GetDataResponse<List<String>> {
		delay(200) // имитация поиска похожих пользователей
		
		// Простая логика: возвращаем случайных пользователей
		// В реальности здесь был бы сложный алгоритм поиска похожих вкусов
		val similarCount = when {
			myProfile.totalSwipes < 10 -> 2 // новый пользователь - мало данных
			myProfile.totalSwipes < 50 -> 4 // развивающийся пользователь
			else -> 6 // зрелый пользователь - больше похожих
		}
		
		val selectedUsers = mockSimilarUsers.shuffled().take(similarCount)
		return GetDataResponse.Success(data = selectedUsers)
	}
	
	override suspend fun getAuthorLikes(authorUsername: String): GetDataResponse<List<String>> {
		delay(150) // имитация загрузки лайков автора
		
		val authorLikes = mockAuthorLikes[authorUsername] ?: emptyList()
		return GetDataResponse.Success(data = authorLikes)
	}
	
	override suspend fun clearProfile(): UpdateDataResponse {
		delay(50) // имитация очистки
		mockStorage.clearAll()
		return UpdateDataResponse.Success
	}
	
	// Метод для сброса только рекомендаций (оставляет базовую статистику)
	override suspend fun resetRecommendations(): UpdateDataResponse {
		delay(50) // имитация сброса
		val currentProfile = this.currentProfile
		val resetProfile = currentProfile.copy(
			categoryScores = emptyMap(),
			authorScores = emptyMap(),
			recentSwipes = emptyList(),
			blockedAuthors = emptyMap(),
			blockedCategories = emptyMap(),
			analysisHistory = emptyList(),
			lastShownCategories = emptyList()
			// Оставляем seenCategories и totalSwipes
		)
		this.currentProfile = resetProfile
		return UpdateDataResponse.Success
	}
}