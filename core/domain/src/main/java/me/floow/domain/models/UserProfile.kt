package me.floow.domain.models

import kotlinx.serialization.Serializable

@Serializable
data class UserProfile(
	val categoryScores: Map<PostCategory, Float> = emptyMap(),
	val authorScores: Map<String, Float> = emptyMap(),
	val seenCategories: Set<PostCategory> = emptySet(),
	val totalSwipes: Int = 0,
	
	// НОВЫЕ поля для умного анализа
	val recentSwipes: List<SwipeRecord> = emptyList(), // последние 20 свайпов
	val blockedAuthors: Map<String, Int> = emptyMap(), // автор -> оставшиеся свайпы блокировки
	val blockedCategories: Map<PostCategory, Int> = emptyMap(), // категория -> оставшиеся свайпы
	val analysisHistory: List<AnalysisResult> = emptyList(), // история анализов для debug
	val lastShownCategories: List<PostCategory> = emptyList() // последние показанные категории для контроля повторов
) {
	fun getFavoriteAuthors(): Set<String> {
		return authorScores.filter { it.value > 0.7f }.keys
	}
	
	fun getUnexploredCategories(): List<PostCategory> {
		val allCategories = PostCategories.defaults.toSet()
		val lessSeenCategories = allCategories - seenCategories
		return lessSeenCategories.toList()
	}
	
	fun isNewUser(): Boolean = totalSwipes < 10
	
	fun isDevelopingUser(): Boolean = totalSwipes in 10..49
	
	fun isMatureUser(): Boolean = totalSwipes >= 50
}
